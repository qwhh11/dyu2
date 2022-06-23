package com.myapp.dyu2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
//    static {
//        System.loadLibrary("native-lib");
//    }

    private SSd sSdnet=new SSd();
//    private ImageView imageView;
    private Mypreview mPreviewView;
    private TextView textView;
    private Myview myview;


    int screenWidth;
    int screenHeight;

    private int REQUEST_CODE_PERMISSIONS = 1001;
    private Executor executor = Executors.newSingleThreadExecutor();

    private Spinner spinner;
    private Spinner spinner2;
    private Spinner spinner3;
    private Spinner spinner4;
    private ImageButton imageButton;
    private RelativeLayout relativeLayout;
    private Button button;

    private boolean start=false;
    private boolean us_gpu=false;
    float conf_thred=0.5f;
    float music_thred=0.2f;
    float time_thred=3.f;

    long toch_time=0;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        //获取设置的配置信息
        mConfiguration = this.getResources().getConfiguration();


        boolean init=sSdnet.Init(getAssets());
        Log.i("aa",init+"");

        imageButton=findViewById(R.id.image_btn);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start=!start;
                if (start){
                    imageButton.setImageBitmap(BitmapFactory.decodeResource(getResources(),R.mipmap.open));
                }else {
                    imageButton.setImageBitmap(BitmapFactory.decodeResource(getResources(),R.mipmap.close));
                }
            }
        });

        spinner=findViewById(R.id.spin);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String string=parent.getSelectedItem().toString();
                if (string.equals("GPU")){
                    us_gpu=true;
                }else {
                    us_gpu=false;
                }
                Log.i("aa",""+us_gpu);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        spinner2=findViewById(R.id.spin2);
        spinner2.setSelection(5);
        spinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                String string=parent.getSelectedItem().toString();
                conf_thred=0.25f+0.05f*(float)position;
//                Log.i("aa",""+conf_thred);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        spinner3=findViewById(R.id.spin3);
        spinner3.setSelection(2);
        spinner3.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                music_thred=0.1f+0.05f*(float)position;
                Log.i("aa",""+music_thred);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        spinner4=findViewById(R.id.spin4);
        spinner4.setSelection(5);
        spinner4.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                time_thred=0.5f+0.5f*(float)position;
                Log.i("aa",""+time_thred);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        button=findViewById(R.id.btn1);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                relativeLayout.setVisibility(View.GONE);
            }
        });


        mPreviewView=findViewById(R.id.mypreview);

        textView=findViewById(R.id.txt);
//        imageView=findViewById(R.id.image);

        relativeLayout=findViewById(R.id.relate);
        myview=findViewById(R.id.myview);

        myview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                toch_time=System.currentTimeMillis();
                if (relativeLayout.getVisibility()!=View.INVISIBLE){
                    relativeLayout.setVisibility(View.VISIBLE);
                }
                return false;
            }
        });


        //获取权限
        if(allPermissionsGranted()){
            startCamera(); //start camera if permission has been granted by user
        } else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        mediaPlayer= MediaPlayer.create(getApplicationContext(),R.raw.m1);
//        imageView=findViewById(R.id.image);
//        Bitmap bmp2= BitmapFactory.decodeResource(getResources(),R.mipmap.a1);
//        imageView.setImageBitmap(bmp2);
//
//        SSd.Obj[] outcome=sSdnet.Detect(bmp2,false);
//
//        mysurface.outcom=outcome;
//        mysurface.bitmapWidth=bmp2.getWidth();
//        mysurface.bitmapHeight=bmp2.getHeight();

//        show(outcome,bmp2);

    }


    public void show(SSd.Obj[] outcome,Bitmap bmp){
        if (outcome==null){
            return;
        }

        Paint paint=new Paint();

        Bitmap rgba = bmp.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas=new Canvas(rgba);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8);


        for (int i=0;i<outcome.length;i++){
            Log.i("aa",outcome[i].x+" "+outcome[i].y+" ");
            Log.i("aa",bmp.getWidth()+" "+bmp.getHeight()+" ");
            Log.i("aa",screenWidth+" "+screenHeight+" ");

            canvas.drawRect(outcome[i].x, outcome[i].y, outcome[i].x + outcome[i].w, outcome[i].y + outcome[i].h, paint);

        }

//        imageView.setImageBitmap(rgba);

    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    private ProcessCameraProvider cameraProvider;
    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);


                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    long t1=0;
    long t2=0;
    long t3=0;
    private int camea_id=1;
    private Bitmap bmp;
    private CameraControl cameraControl;
    private boolean su;
    private boolean heng;

    private Configuration mConfiguration;

    float now_h=0.f;
    float now_x=0.f;

    boolean music=false;
    boolean isPlaying=false;
    private MediaPlayer mediaPlayer;
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .build();

        @SuppressLint("WrongConstant") CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(camea_id)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .build();
        //
        //imageAnalysis.setAnalyzer(cameraExecutor, new MyAnalyzer());
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {

                runOnUiThread(() ->{
                    //获取当下屏幕状态
                    int ori = mConfiguration.orientation; //获取屏幕方向
                    if (ori == mConfiguration.ORIENTATION_LANDSCAPE) {
                        //横屏
                        heng=true;
                        su=false;
                    } else if (ori == mConfiguration.ORIENTATION_PORTRAIT) {
                        //竖屏
                        su=true;
                        heng=false;
                    }

                    t1=t2;
                    t2= System.currentTimeMillis();
                    long fps=1000/(t2-t1);
                    textView.setText("FPS= "+fps);


                    if (!start){
                        image.close();
                        return;
                    }

                    //yuv图像数据转bitmap
                    ImageProxy.PlaneProxy[] planes = image.getPlanes();

                    //cameraX 获取yuv
                    ByteBuffer yBuffer = planes[0].getBuffer();
                    ByteBuffer uBuffer = planes[1].getBuffer();
                    ByteBuffer vBuffer = planes[2].getBuffer();

                    int ySize = yBuffer.remaining();
                    int uSize = uBuffer.remaining();
                    int vSize = vBuffer.remaining();

                    byte[] nv21 = new byte[ySize + uSize + vSize];

                    yBuffer.get(nv21, 0, ySize);
                    vBuffer.get(nv21, ySize, vSize);
                    uBuffer.get(nv21, ySize + vSize, uSize);
                    //获取yuvImage
                    YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
                    //输出流
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    //压缩写入out
                    yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, out);
                    //转数组
                    byte[] imageBytes = out.toByteArray();
                    //生成bitmap
                    Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                    //旋转bitmap
                    Bitmap rotateBitmap=null;
                    if (camea_id==1 && su){
                        rotateBitmap = rotateBitmap(bmp, 90);
                    }else if(camea_id==0 && su){
                        rotateBitmap = rotateBitmap(bmp, 270);
                    }else if(camea_id==1 && heng){
                        rotateBitmap=bmp;
                    }else {
                        rotateBitmap=rotateBitmap(bmp, 0);
                    }


                    Bitmap bmp2=rotateBitmap.copy(Bitmap.Config.ARGB_8888, true);

                    SSd.Obj[] outcome=sSdnet.Detect(bmp2,us_gpu,conf_thred);


                    //Log.i("aa","bitmap_w="+bmp2.getWidth()+"  bitmap_h="+bmp2.getHeight());
//                    imageView.setImageBitmap(bmp2);

                    float aa=0;
                    float bb=0;

                    SSd.Obj[] pick_outcom=new SSd.Obj[1];
                    float maxconf=0;
                    int index=0;
                    if(outcome.length>0){

                        for (int i=0;i<outcome.length;i++){
                            if (i==0){
                                maxconf=outcome[i].prob;
                                index=i;
                            }else {
                                if(outcome[i].prob>maxconf){
                                    maxconf=outcome[i].prob;
                                    index=i;
                                }
                            }
                        }

                        pick_outcom[0]=outcome[index];

                    }



                    if (outcome.length>0){
                        aa=Math.abs(pick_outcom[0].h*bmp2.getHeight()/now_h-1.f);
                        if (now_x==0){
                            bb=0;
                        }else {
                            bb=Math.abs(pick_outcom[0].x*bmp2.getHeight()/now_x-1.f);
                        }
                        music= aa > music_thred || bb > music_thred;


                        if (music && !mediaPlayer.isPlaying()){

                            mediaPlayer.start();

                        }
                        Log.i("aa","isplaying="+isPlaying);


//                        Log.i("aa"," aa="+aa+"   bb="+bb);

                        if (t2-t3>3000){
                            t3=t2;
                            now_h=pick_outcom[0].h*bmp2.getHeight();
                            now_x=pick_outcom[0].x*bmp2.getHeight();
                        }

                    }

                    new Thread(new Runnable() { // 匿名类的Runnable接口
                        @Override
                        public void run() {
                            myview.draws(outcome,bmp2.getWidth(),bmp2.getHeight(),heng,mediaPlayer.isPlaying());
                        }
                    }).start();


                    //关闭
                    image.close();

                });

            }
        });

        ImageCapture.Builder builder = new ImageCapture.Builder();

        //Vendor-Extensions (The CameraX extensions dependency in build.gradle)
        HdrImageCaptureExtender hdrImageCaptureExtender = HdrImageCaptureExtender.create(builder);

        // Query if extension is available (optional).
        if (hdrImageCaptureExtender.isExtensionAvailable(cameraSelector)) {
            // Enable the extension if available.
            hdrImageCaptureExtender.enableExtension(cameraSelector);
        }

        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();

        preview.setSurfaceProvider(mPreviewView.createSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis, imageCapture);
            cameraControl=camera.getCameraControl();
            mPreviewView.cameraControl=cameraControl;
//            cameraControl.setLinearZoom(1f);


        } catch (Exception e) {
            e.printStackTrace();
        }


    }
    private Bitmap rotateBitmap(Bitmap origin, float alpha) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(alpha);
        if (camea_id==0){
            matrix.postScale(-1,1);
        }
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    //获取权限函数
    private boolean allPermissionsGranted(){
        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

}