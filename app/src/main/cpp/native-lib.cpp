#include <jni.h>
#include <string>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>
#include <cmath>

#include <vector>

// ncnn
#include "layer.h"
#include "net.h"
#include "benchmark.h"

static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;
static ncnn::Net SSD;

struct Object
{
    float x;
    float y;
    float w;
    float h;
    int label;
    float prob;
};

static float soft_sum(std::vector<float>& v){
    float sum=0;
    float len=0;

    for(float f:v){
        sum+=f;
    }
    for (int i = 0; i < v.size(); i++)
    {
        float a=v[i]/sum*i;
//        v[i]=a;
        len+=a;

    }
    return len;


}

static inline float intersection_area(const Object& a, const Object& b)
{
    float zuo_x=std::max(a.x-0.5*a.w,b.x-0.5*b.w);
    float zuo_y=std::max(a.y-0.5*a.h,b.y-0.5*b.h);

    float you_x=std::min(a.x+0.5*a.w,b.x+0.5*b.w);
    float you_y=std::min(a.y+0.5*a.h,b.y+0.5*b.h);

    float inter_width=you_x-zuo_x;
    float inter_height=you_y-zuo_y;

    if(inter_height<=0 || inter_width<=0){
        return 0.f;
    }

    return inter_width * inter_height;
}

static void qsort_descent_inplace(std::vector<Object>& faceobjects, int left, int right)
{
    int i = left;
    int j = right;
    float p = faceobjects[(left + right) / 2].prob;

    while (i <= j)
    {
        while (faceobjects[i].prob > p)
            i++;

        while (faceobjects[j].prob < p)
            j--;

        if (i <= j)
        {
            // swap
            std::swap(faceobjects[i], faceobjects[j]);

            i++;
            j--;
        }
    }

#pragma omp parallel sections
    {
#pragma omp section
        {
            if (left < j) qsort_descent_inplace(faceobjects, left, j);
        }
#pragma omp section
        {
            if (i < right) qsort_descent_inplace(faceobjects, i, right);
        }
    }
}
static void qsort_descent_inplace(std::vector<Object>& faceobjects)
{
    if (faceobjects.empty())
        return;

    qsort_descent_inplace(faceobjects, 0, faceobjects.size() - 1);
}
static void nms_sorted_bboxes(const std::vector<Object>& faceobjects, std::vector<int>& picked, float nms_threshold)
{
    picked.clear();

    const int n = faceobjects.size();

    std::vector<float> areas(n);
    for (int i = 0; i < n; i++)
    {
        areas[i] = faceobjects[i].w * faceobjects[i].h;
    }

    for (int i = 0; i < n; i++)
    {
        const Object& a = faceobjects[i];

        int keep = 1;
        for (int j = 0; j < (int)picked.size(); j++)
        {
            const Object& b = faceobjects[picked[j]];

            // intersection over union
            float inter_area = intersection_area(a, b);
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            // float IoU = inter_area / union_area
            if (inter_area / union_area > nms_threshold)
                keep = 0;
        }

        if (keep)
            picked.push_back(i);
    }
}

static inline float sigmoid(float x)
{
    return static_cast<float>(1.f / (1.f + exp(-x)));
}
static void generate_proposals(const ncnn::Mat& anchors, int stride, const ncnn::Mat& in_pad, const ncnn::Mat& feat_blob, float prob_threshold, std::vector<Object>& objects)
{
    const int num_grid = feat_blob.h;

    int num_grid_x;
    int num_grid_y;
    if (in_pad.w > in_pad.h)
    {
        num_grid_x = in_pad.w / stride;
        num_grid_y = num_grid / num_grid_x;
    }
    else
    {
        num_grid_y = in_pad.h / stride;
        num_grid_x = num_grid / num_grid_y;
    }

    const int num_class = feat_blob.w - 5;

    const int num_anchors = anchors.w / 2;

    for (int q = 0; q < num_anchors; q++)
    {
        const float anchor_w = anchors[q * 2];
        const float anchor_h = anchors[q * 2 + 1];

        const ncnn::Mat feat = feat_blob.channel(q);

        for (int i = 0; i < num_grid_y; i++)
        {
            for (int j = 0; j < num_grid_x; j++)
            {
                //获取每一行数据
                const float* featptr = feat.row(i * num_grid_x + j);

                // find class index with max class score
                int class_index = 0;
                float class_score = -FLT_MAX;

                //求取每一行的最大值和最大值的索引
                for (int k = 0; k < num_class; k++)
                {
                    float score = featptr[5 + k];
                    if (score > class_score)
                    {
                        class_index = k;
                        class_score = score;
                    }
                }

                //获取盒子的置信度分数
                float box_score = featptr[4];
                //获取总的置信度分数
                float confidence = sigmoid(box_score) * sigmoid(class_score);

                if (confidence >= prob_threshold)
                {
                    // yolov5/models/yolo.py Detect forward
                    // y = x[i].sigmoid()
                    // y[..., 0:2] = (y[..., 0:2] * 2. - 0.5 + self.grid[i].to(x[i].device)) * self.stride[i]  # xy
                    // y[..., 2:4] = (y[..., 2:4] * 2) ** 2 * self.anchor_grid[i]  # wh

                    float dx = sigmoid(featptr[0]);
                    float dy = sigmoid(featptr[1]);
                    float dw = sigmoid(featptr[2]);
                    float dh = sigmoid(featptr[3]);

                    float pb_cx = (dx * 2.f - 0.5f + j) * stride;
                    float pb_cy = (dy * 2.f - 0.5f + i) * stride;

                    float pb_w = pow(dw * 2.f, 2) * anchor_w;
                    float pb_h = pow(dh * 2.f, 2) * anchor_h;

                    float x0 = pb_cx - pb_w * 0.5f;
                    float y0 = pb_cy - pb_h * 0.5f;
                    float x1 = pb_cx + pb_w * 0.5f;
                    float y1 = pb_cy + pb_h * 0.5f;

                    Object obj;
                    obj.x = x0;
                    obj.y = y0;
                    obj.w = x1 - x0;
                    obj.h = y1 - y0;
                    obj.label = class_index;
                    obj.prob = confidence;

                    objects.push_back(obj);
                }
            }
        }
    }
}


// FIXME DeleteGlobalRef is missing for objCls
static jclass objCls = NULL;
static jmethodID constructortorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static jfieldID labelId;
static jfieldID probId;

//初始化函数
extern "C" JNIEXPORT jboolean JNICALL

Java_com_myapp_dyu2_SSd_Init(JNIEnv *env, jobject thiz, jobject assetManager) {
    // TODO: implement Init()
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
    opt.use_packing_layout = true;


    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    SSD.opt = opt;
    // init param
    {
        int ret = SSD.load_param(mgr, "model.param");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "aa", "load_param failed");
            return JNI_FALSE;
        }
    }

    // init bin
    {
        int ret = SSD.load_model(mgr, "model.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "aa", "load_model failed");
            return JNI_FALSE;
        }
    }
    // init jni glue
    //获取java中的对应实例类
    jclass localObjCls = env->FindClass("com/myapp/dyu2/SSd$Obj");
    objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));


    constructortorId = env->GetMethodID(objCls, "<init>", "(Lcom/myapp/dyu2/SSd;)V");

    xId = env->GetFieldID(objCls, "x", "F");
    yId = env->GetFieldID(objCls, "y", "F");
    wId = env->GetFieldID(objCls, "w", "F");
    hId = env->GetFieldID(objCls, "h", "F");
    labelId = env->GetFieldID(objCls, "label", "Ljava/lang/String;");
    probId = env->GetFieldID(objCls, "prob", "F");



    return JNI_TRUE;

}

static int max(int a,int b){
    if (a>b){
        return a;
    } else{return b;}
}
static int min(int a,int b){
    if (a>b){
        return b;
    } else{return a;}
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_myapp_dyu2_SSd_Detect(JNIEnv *env, jobject thiz, jobject bitmap, jboolean use_gpu,jfloat prob_threshold ) {

    // TODO: implement Detect()
    if (use_gpu == JNI_TRUE && ncnn::get_gpu_count() == 0)
    {
        return NULL;
        //return env->NewStringUTF("no vulkan capable gpu");
    }
    //计数当前时间
    double start_time = ncnn::get_current_time();

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    //原始图像的宽和高
    const int width = info.width;
    const int height = info.height;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return NULL;
    // ncnn from bitmap
    const int target_size = 640;

    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_RGB, target_size, target_size);


    const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    in.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor ex = SSD.create_extractor();

    ex.set_vulkan_compute(use_gpu);

    ex.input("images", in);


//    prob_threshold = 0.25f;
    const float nms_threshold = 0.45f;

    //符合要求的盒子都放入该容器中
    std::vector<Object> objects;


    std::vector<Object> proposals;
    // stride 8
    {
        ncnn::Mat out;
        ex.extract("output", out);

        ncnn::Mat anchors(6);
        anchors[0] = 10.f;
        anchors[1] = 13.f;
        anchors[2] = 16.f;
        anchors[3] = 30.f;
        anchors[4] = 33.f;
        anchors[5] = 23.f;

        std::vector<Object> objects8;
        generate_proposals(anchors, 8, in, out, prob_threshold, objects8);

        proposals.insert(proposals.end(), objects8.begin(), objects8.end());
    }
    // stride 16
    {
        ncnn::Mat out;
        ex.extract("365", out);

        ncnn::Mat anchors(6);
        anchors[0] = 30.f;
        anchors[1] = 61.f;
        anchors[2] = 62.f;
        anchors[3] = 45.f;
        anchors[4] = 59.f;
        anchors[5] = 119.f;

        std::vector<Object> objects16;
        generate_proposals(anchors, 16, in, out, prob_threshold, objects16);

        proposals.insert(proposals.end(), objects16.begin(), objects16.end());
    }

    // stride 32
    {
        ncnn::Mat out;
        ex.extract("385", out);

        ncnn::Mat anchors(6);
        anchors[0] = 116.f;
        anchors[1] = 90.f;
        anchors[2] = 156.f;
        anchors[3] = 198.f;
        anchors[4] = 373.f;
        anchors[5] = 326.f;

        std::vector<Object> objects32;
        generate_proposals(anchors, 32, in, out, prob_threshold, objects32);

        proposals.insert(proposals.end(), objects32.begin(), objects32.end());
    }


    // sort all proposals by score from highest to lowest
    qsort_descent_inplace(proposals);

    // apply nms with nms_threshold
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    int count = picked.size();


    //objects size 0=>2
    objects.resize(count);
    for (int i = 0; i < count; i++)
    {
        objects[i] = proposals[picked[i]];

        // adjust offset to original unpadded
        float wpad=0;
        float hpad=0;
        float scalex=1;//640.f/(float)width;
        float scaley=1;//640.f/(float)height;

        float x0 = (objects[i].x - (wpad / 2)) / scalex;
        float y0 = (objects[i].y - (hpad / 2)) / scaley;
        float x1 = (objects[i].x + objects[i].w - (wpad / 2)) / scalex;
        float y1 = (objects[i].y + objects[i].h - (hpad / 2)) / scaley;

//        __android_log_print(ANDROID_LOG_DEBUG, "aa", x0);

        // clip
//        x0 = std::max(std::min(x0, (float)(width - 1)), 0.f);
//        y0 = std::max(std::min(y0, (float)(height - 1)), 0.f);
//        x1 = std::max(std::min(x1, (float)(width - 1)), 0.f);
//        y1 = std::max(std::min(y1, (float)(height - 1)), 0.f);
        x0 = std::max(std::min(x0/640.f, 1.f), 0.f);
        y0 = std::max(std::min(y0/640.f, 1.f), 0.f);
        x1 = std::max(std::min(x1/640.f, 1.f), 0.f);
        y1 = std::max(std::min(y1/640.f, 1.f), 0.f);

        objects[i].x = x0;
        objects[i].y = y0;
        objects[i].w = x1 - x0;
        objects[i].h = y1 - y0;

    }


    static const char* class_names[]={"浮漂"};

    jobjectArray jObjArray = env->NewObjectArray(objects.size(), objCls, NULL);
    for (size_t i=0; i<objects.size(); i++)
    {
        jobject jObj = env->NewObject(objCls, constructortorId, thiz);

        env->SetFloatField(jObj, xId, objects[i].x);
        env->SetFloatField(jObj, yId, objects[i].y);
        env->SetFloatField(jObj, wId, objects[i].w);
        env->SetFloatField(jObj, hId, objects[i].h);
        env->SetObjectField(jObj, labelId, env->NewStringUTF(class_names[objects[i].label]));
        env->SetFloatField(jObj, probId, objects[i].prob);

        env->SetObjectArrayElement(jObjArray, i, jObj);
    }

    return jObjArray;


}