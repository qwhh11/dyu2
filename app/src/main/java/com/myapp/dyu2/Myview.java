package com.myapp.dyu2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

public class Myview extends View {



    public Myview(Context context) {
        super(context);
    }


    private Paint paint;
    private Paint textbgpaint;
    private Paint textpaint;


    private Canvas canvas;
    private Bitmap bitmap;
    public Myview(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint(Paint.DITHER_FLAG);//创建一个画笔
        paint.setStyle(Paint.Style.STROKE);//设置非填充
        paint.setStrokeWidth(5);//笔宽5像素
        paint.setColor(0xFF33FFDD);

        textbgpaint = new Paint();
        textbgpaint.setColor(Color.WHITE);
        textbgpaint.setStyle(Paint.Style.FILL);

        textpaint = new Paint();
        textpaint.setColor(Color.BLACK);
        textpaint.setTextSize(26);
        textpaint.setTextAlign(Paint.Align.LEFT);


    }


    int width=0;
    int height=0;

    int color=0;
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width=getMeasuredWidth();
        height=getMeasuredHeight();


        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);//设置位图的宽高
        canvas = new Canvas(bitmap);

    }

    public void draws(SSd.Obj[] objects,float bitmap_w,float bitmap_h,boolean heng,boolean music){

        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        if (music){
            if (color==1){
                paint.setColor(0xFF33FFDD);
                color=0;
            }else {
                paint.setColor(0xFFFF0000);
                color=1;
            }


        }else {
            paint.setColor(0xFF33FFDD);
        }



        float rate=Math.max((float) height/bitmap_h,(float) width/bitmap_w);
        float new_w=bitmap_w*rate;
        float new_h=bitmap_h*rate;

        float pad_x=Math.max((new_w-width)/2.f,(new_h-height)/2.f);

        for (int i = 0; i < objects.length; i++)
        {
//            paint.setColor(colors[i % 19]);

            float x;
            float y;
            float w;
            float h;
//            width=1644;

            if(!heng){
                x=objects[i].x*(float)new_w-pad_x;
                y=objects[i].y*(float)new_h;
            }else {
                x=objects[i].x*(float)new_w;
                y=objects[i].y*(float)new_h-pad_x;
            }

            w=objects[i].w*(float)new_w;
            h=objects[i].h*(float)new_h;

//            Log.i("aa","h="+height+"  w="+width);

            canvas.drawRect(x, y, x + w, y + h, paint);

            // draw filled text inside image
            {
                String text = objects[i].label + " = " + String.format("%.1f", objects[i].prob * 100) + "%";

                float text_width = textpaint.measureText(text);
                float text_height = - textpaint.ascent() + textpaint.descent();


                y = y - text_height;
                if (y < 0)
                    y = 0;
//                if (x + text_width > width)
//                    x = width- text_width;
                canvas.drawRect(x, y, x + text_width, y + text_height, textbgpaint);

                canvas.drawText(text, x, y - textpaint.ascent(), textpaint);
            }
        }

//        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(bitmap,0,0,paint);
    }
}
