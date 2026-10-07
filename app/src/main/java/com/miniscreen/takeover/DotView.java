package com.miniscreen.takeover;

import android.content.*;
import android.graphics.*;
import android.view.View;

final class DotView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String color;private Config config;private float dx,dy;
    DotView(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void update(String value,Config c,float x,float y){color=value;config=c;dx=x;dy=y;invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(color==null||config==null)return;
        SharedPreferences p=TakeoverControl.prefs(getContext());float scale=Math.min(getWidth(),getHeight())/340f;
        float dot=Config.clamp(p.getInt("dot_size",12),2,100)*scale/2;
        float radius=Math.max(0,Math.min(getWidth(),getHeight())/2f-dot-2*scale-(config.shifting?config.shiftRange*1.414214f*scale:0));
        float x=getWidth()*Config.clamp(p.getInt("dot_x",50),0,100)/100f-getWidth()/2f,y=getHeight()*Config.clamp(p.getInt("dot_y",15),0,100)/100f-getHeight()/2f;
        float distance=(float)Math.hypot(x,y);if(distance>radius&&distance>0){x*=radius/distance;y*=radius/distance;}
        paint.setColor(Color.parseColor(color));canvas.drawCircle(getWidth()/2f+x+dx,getHeight()/2f+y+dy,dot,paint);
    }
}
