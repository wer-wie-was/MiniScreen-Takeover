package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class PebbleView extends View implements PebbleSession.Observer {
    private Bitmap frame;private String status="";private boolean active;private final boolean preview;private Config config;private float dx,dy;private final Paint paint=new Paint(3);
    PebbleView(Context c,boolean preview){super(c);this.preview=preview;}
    void update(Config c,float x,float y,boolean running){config=c;dx=x;dy=y;if(running&&!active){active=true;PebbleSession.get(getContext()).subscribe(this,preview);}else if(!running&&active)stop();invalidate();}
    void stop(){if(active){active=false;PebbleSession.get(getContext()).unsubscribe(this);}}
    @Override public void changed(Bitmap image,String state){frame=image;status=state;invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(config==null)return;float scale=Math.min(getWidth(),getHeight())/340f,margin=(config.shifting?config.shiftRange:0)*scale;int save=canvas.save();canvas.translate(dx,dy);Path circle=new Path();circle.addCircle(getWidth()/2f,getHeight()/2f,Math.max(0,Math.min(getWidth(),getHeight())/2f-margin),Path.Direction.CW);canvas.clipPath(circle);
        if(frame!=null){boolean cover=TakeoverControl.prefs(getContext()).getBoolean("pebble_crop",false);float factor=cover?Math.max((getWidth()-2*margin)/frame.getWidth(),(getHeight()-2*margin)/frame.getHeight()):Math.min((getWidth()-2*margin)/frame.getWidth(),(getHeight()-2*margin)/frame.getHeight());float w=frame.getWidth()*factor,h=frame.getHeight()*factor;canvas.drawBitmap(frame,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),paint);}
        else{paint.setColor(Color.WHITE);paint.setTextSize(14*scale);paint.setTextAlign(Paint.Align.CENTER);String text=PebbleText.status(status);canvas.drawText(text,getWidth()/2f,getHeight()/2f,paint);}
        canvas.restoreToCount(save);
    }
}
