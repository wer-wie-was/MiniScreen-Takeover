package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Measures the renderer at the rear display's physical resolution, then scales its preview. */
final class PreviewFrame extends ViewGroup {
    final DisplaySurface surface;
    private final int targetWidth,targetHeight;
    private int range=8;
    private boolean guides=true;
    private final Path roundClip=new Path();
    private final RectF roundBounds=new RectF();
    PreviewFrame(Context context,Context displayContext,int w,int h) {
        super(context);targetWidth=Math.max(1,w);targetHeight=Math.max(1,h);surface=new DisplaySurface(displayContext,true);addView(surface);
        setClipChildren(true);setWillNotDraw(false);
    }
    void guides(boolean enabled,int range){guides=enabled;this.range=range;invalidate();}
    @Override protected void onMeasure(int wm,int hm) {
        int max=Math.round(280*getResources().getDisplayMetrics().density);
        int w=Math.min(max,MeasureSpec.getSize(wm));int h=Math.round(w*targetHeight/(float)targetWidth);
        setMeasuredDimension(w,h);surface.measure(MeasureSpec.makeMeasureSpec(targetWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(targetHeight,MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){surface.layout(0,0,targetWidth,targetHeight);surface.setPivotX(0);surface.setPivotY(0);surface.setScaleX(getWidth()/(float)targetWidth);surface.setScaleY(getHeight()/(float)targetHeight);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        float diameter=Math.min(w,h),left=(w-diameter)/2f,top=(h-diameter)/2f;
        roundBounds.set(left,top,left+diameter,top+diameter);
        roundClip.reset();roundClip.addOval(roundBounds,Path.Direction.CW);
    }
    @Override protected void dispatchDraw(Canvas canvas){
        int save=canvas.save();canvas.clipPath(roundClip);
        super.dispatchDraw(canvas);
        if(guides){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setColor(0x8863E6DC);p.setStrokeWidth(2);p.setPathEffect(new DashPathEffect(new float[]{8,8},0));
            float inset=Math.max(1,range*Math.min(getWidth(),getHeight())/(float)Math.min(targetWidth,targetHeight));
            RectF border=new RectF(roundBounds);border.inset(inset,inset);canvas.drawOval(border,p);
        }
        canvas.restoreToCount(save);
    }
}
