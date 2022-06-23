package com.myapp.dyu2;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraControl;
import androidx.camera.view.PreviewView;

public class Mypreview extends PreviewView {

    public float rate=0f;
    public float rate1=0f;
    public float rate2=0f;
    private int state=0;
    private float distance;

    public CameraControl cameraControl;

    public Mypreview(@NonNull Context context) {
        super(context);
    }

    public Mypreview(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);


    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:

                int x=(int)event.getRawX();
                int y=(int)event.getRawY();
                Log.e("aa","按下"+x+","+y);
                break;
            case MotionEvent.ACTION_UP:
                Log.e("aa","抬起");
                break;
            case MotionEvent.ACTION_MOVE:
                if(state==2){
                    //计算缩放倍数,放大
                    if(getDistance(event)/distance>=1) {


                        rate2 = (getDistance(event) / distance-1f)*0.4f;

                        rate1=rate;
                        rate1+=rate2;
                        rate1= Math.min(rate1, 1f);
                        cameraControl.setLinearZoom(rate1);

                        Log.e("aa", "双点移动放大" + rate1);
                    }else {
                        rate2=(1f-getDistance(event) / distance)*0.4f;

                        rate1=rate;
                        rate1-=rate2;
                        rate1= Math.max(rate1, 0f);

                        cameraControl.setLinearZoom(rate1);
                        Log.e("aa", "双点移动缩小" + rate1);
                    }


                }else {
                    Log.e("aa","单点移动");
                }
                Log.i("aa","滑动");

                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(event.getPointerCount()==2){
                    Log.i("aa","22222222");
                    distance = getDistance(event);
                    state=2;
                }
                Log.e("aa","多点");
                break;
            case MotionEvent.ACTION_POINTER_UP://双指离开
                //手指离开后，重置状态
                rate=rate1;

                state=0;
                break;
        }



        return true;
    }

    //获取距离
    private static float getDistance(MotionEvent event) {//获取两点间距离
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
}
