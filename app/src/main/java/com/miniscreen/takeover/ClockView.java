package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.File;
import java.util.Date;

final class ClockView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Config config;
    private Bitmap image;
    private Typeface typeface=Typeface.DEFAULT;
    private String cachedImage="";
    private float shiftX,shiftY;
    private float notificationAlpha=1;
    void notificationAlpha(float value){notificationAlpha=value;invalidate();}
    private long cachedTick=Long.MIN_VALUE;
    private String cachedTime="",cachedDate="";
    ClockView(Context context) {super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void configure(Config c,File folder) {
        config=c;cachedTick=Long.MIN_VALUE;
        String imageKey=c.image.isEmpty()?"":new File(folder,c.image).getPath();
        if(!cachedImage.equals(imageKey)){cachedImage=imageKey;image=c.image.isEmpty()?null:decodeImage(new File(folder,c.image));}
        try {typeface=!c.fontFile.isEmpty()?Typeface.createFromFile(new File(folder,c.fontFile)):Typeface.create(c.font,c.bold?Typeface.BOLD:Typeface.NORMAL);
            if(!c.fontFile.isEmpty()&&c.bold)typeface=Typeface.create(typeface,Typeface.BOLD);
        }catch(RuntimeException e){typeface=Typeface.DEFAULT;}
        invalidate();
    }
    static Bitmap decodeImage(File f) {
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        if(o.outWidth<1||o.outHeight<1)return null;
        o.inSampleSize=1;while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>2048)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;return BitmapFactory.decodeFile(f.getPath(),o);
    }
    void shift(float x,float y) {shiftX=x;shiftY=y;cachedTick=Long.MIN_VALUE;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);Config c=config;if(c==null)return;
        canvas.drawColor(Color.parseColor(c.backgroundColor));float scale=Math.min(getWidth(),getHeight())/340f;
        if(image!=null) {
            float sx=getWidth()/(float)image.getWidth(),sy=getHeight()/(float)image.getHeight();
            float k=c.imageFit.equals("contain")?Math.min(sx,sy):Math.max(sx,sy);
            float extra=c.shiftBackground&&c.shifting?c.shiftRange*scale:0;
            if(!c.imageFit.equals("contain"))k=Math.max(k,Math.max((getWidth()+2*extra)/image.getWidth(),(getHeight()+2*extra)/image.getHeight()));
            if(c.imageFit.equals("window"))k*=c.windowZoom/100f;
            float w=image.getWidth()*k,h=image.getHeight()*k;
            long now=System.currentTimeMillis();
            float px=c.imageFit.equals("window")?WindowPan.position(now,c.windowSeconds,1,c.windowMotion):.5f;
            float py=c.imageFit.equals("window")?WindowPan.position(now,c.windowSeconds,2,c.windowMotion):.5f;
            float x=-extra-(w-getWidth()-2*extra)*px+(c.shiftBackground?shiftX:0);
            float y=-extra-(h-getHeight()-2*extra)*py+(c.shiftBackground?shiftY:0);
            paint.setColor(Color.WHITE);paint.setAlpha(255);canvas.drawBitmap(image,null,new RectF(x,y,x+w,y+h),paint);
            paint.setColor(Color.BLACK);paint.setAlpha(Math.round(c.dim*2.55f));canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setAlpha(255);
        }
        long now=System.currentTimeMillis(),bucket=now/(c.seconds?1000:60000);
        if(bucket!=cachedTick){Date date=new Date(now);cachedTime=c.time(date);cachedDate=c.showDate?c.date(date):"";cachedTick=bucket;}
        if(c.showClock)drawText(canvas,cachedTime,c.timeSize*scale,c.timeX,c.timeY,c.timeColor);
        if(c.showDate)drawText(canvas,cachedDate,c.dateSize*scale,c.dateX,c.dateY,c.dateColor);
    }
    private void drawText(Canvas canvas,String text,float size,int xp,int yp,String color) {
        float scale=Math.min(getWidth(),getHeight())/340f;
        float margin=3*scale+(config.shifting?config.shiftRange*scale:0);
        paint.setTypeface(typeface);paint.setTextSize(size);paint.setColor(Color.parseColor(color));paint.setAlpha(Math.round(notificationAlpha*255));
        Rect bounds=new Rect();paint.getTextBounds(text,0,text.length(),bounds);
        float maxW=Math.max(1,getWidth()-2*margin),maxH=Math.max(1,getHeight()-2*margin);
        float shrink=Math.min(1,Math.min(maxW/Math.max(1,bounds.width()),maxH/Math.max(1,bounds.height())));
        paint.setTextSize(size*shrink);paint.getTextBounds(text,0,text.length(),bounds);
        float x=getWidth()*xp/100f-(bounds.left+bounds.right)/2f;
        float y=getHeight()*yp/100f-(bounds.top+bounds.bottom)/2f;
        x=Math.max(margin-bounds.left,Math.min(getWidth()-margin-bounds.right,x));
        y=Math.max(margin-bounds.top,Math.min(getHeight()-margin-bounds.bottom,y));
        canvas.drawText(text,x+shiftX,y+shiftY,paint);
    }
}
