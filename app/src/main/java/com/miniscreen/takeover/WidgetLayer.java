package com.miniscreen.takeover;

import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/** Read-only native layer. Widget dimensions/options are independent of portable design profiles. */
final class WidgetLayer extends FrameLayout {
    private AppWidgetHostView widget;private int id=-1;
    private int optionWidth=-1,optionHeight=-1;private Config config;private float dx,dy;private boolean listening;
    WidgetLayer(Context c){super(c);setClipChildren(true);}
    void update(Config c,float dx,float dy,boolean running){
        config=c;this.dx=dx;this.dy=dy;SharedPreferences p=TakeoverControl.prefs(getContext());
        int next=p.getBoolean("widget_enabled",false)?p.getInt("widget_id",-1):-1;
        if(next!=id){removeAllViews();widget=null;optionWidth=optionHeight=-1;id=next;
            if(id>0)try{AppWidgetProviderInfo info=AppWidgetManager.getInstance(getContext()).getAppWidgetInfo(id);if(info!=null){widget=WidgetHostManager.host(getContext()).createView(getContext(),id,info);addView(widget);}else id=-1;}catch(RuntimeException e){id=-1;}
        }
        boolean want=running&&widget!=null;if(want&&!listening){listening=WidgetHostManager.start(getContext());}else if(!want&&listening){WidgetHostManager.stop();listening=false;}
        requestLayout();invalidate();
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        if(widget==null||config==null)return;SharedPreferences p=TakeoverControl.prefs(getContext());float scale=Math.min(getWidth(),getHeight())/340f;
        float margin=(config.shifting?config.shiftRange:0)*scale+2*scale;
        int width=Math.max(1,Math.round(Math.min(getWidth()-2*margin,getWidth()*Config.clamp(p.getInt("widget_width",65),10,100)/100f)));
        int height=Math.max(1,Math.round(Math.min(getHeight()-2*margin,getHeight()*Config.clamp(p.getInt("widget_height",40),10,100)/100f)));
        float zoom=Config.clamp(p.getInt("widget_scale",100),50,200)/100f;
        int logicalWidth=Math.max(1,Math.round(width/zoom)),logicalHeight=Math.max(1,Math.round(height/zoom));
        widget.measure(MeasureSpec.makeMeasureSpec(logicalWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(logicalHeight,MeasureSpec.EXACTLY));
        int x=Math.round(Math.max(margin,Math.min(getWidth()-margin-width,getWidth()*Config.clamp(p.getInt("widget_x",50),0,100)/100f-width/2f)));
        int y=Math.round(Math.max(margin,Math.min(getHeight()-margin-height,getHeight()*Config.clamp(p.getInt("widget_y",50),0,100)/100f-height/2f)));
        widget.layout(x,y,x+logicalWidth,y+logicalHeight);widget.setPivotX(0);widget.setPivotY(0);widget.setScaleX(zoom);widget.setScaleY(zoom);widget.setTranslationX(dx);widget.setTranslationY(dy);
        // Tell the provider its unscaled host dimensions in dp, once for each size change.
        float density=getResources().getDisplayMetrics().density;int w=Math.max(1,Math.round(Math.min(340-2*(config.shifting?config.shiftRange:0)-4,340*Config.clamp(p.getInt("widget_width",65),10,100)/100f)/zoom/density)),h=Math.max(1,Math.round(Math.min(340-2*(config.shifting?config.shiftRange:0)-4,340*Config.clamp(p.getInt("widget_height",40),10,100)/100f)/zoom/density));
        if(optionWidth!=w||optionHeight!=h){widget.updateAppWidgetSize(null,w,h,w,h);optionWidth=w;optionHeight=h;}
    }
    @Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();Path path=new Path();float radius=Math.min(getWidth(),getHeight())/2f;path.addCircle(getWidth()/2f,getHeight()/2f,radius,Path.Direction.CW);canvas.clipPath(path);super.dispatchDraw(canvas);canvas.restoreToCount(save);}
    void stop(){if(listening){WidgetHostManager.stop();listening=false;}}
    void dispose(){stop();removeAllViews();widget=null;}
    @Override public boolean dispatchTouchEvent(MotionEvent e){return true;}
}
