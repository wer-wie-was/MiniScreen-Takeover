package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.*;
import android.view.View;
import java.io.File;

/** Read-only ticker overlay; all dimensions use the existing 340px design coordinates. */
final class TickerView extends View {
    private final Paint background=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private NotificationTicker.Message message;
    private Config config;
    private TickerSettings settings;
    private float dx,dy;
    private boolean running;
    private String fontKey="",iconKey="";
    private Drawable icon;
    TickerView(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void update(NotificationTicker.Message m,Config c,File folder,float dx,float dy,boolean run){
        message=m;config=c;this.dx=dx;this.dy=dy;running=run;settings=new TickerSettings(getContext());
        String family=settings.p.getString("ticker_font","sans"),path=family.equals("profile")?c.fontFile:"";
        boolean bold=settings.bool("bold",false);String key=folder+":"+family+":"+path+":"+bold;
        if(!key.equals(fontKey)){fontKey=key;Typeface f;try{f=path.isEmpty()?Typeface.create(family.equals("profile")?c.font:family,Typeface.NORMAL):Typeface.createFromFile(new File(folder,path));}catch(RuntimeException e){f=Typeface.DEFAULT;}text.setTypeface(Typeface.create(f,bold?Typeface.BOLD:Typeface.NORMAL));}
        String app=m==null?"":m.app;if(!app.equals(iconKey)){iconKey=app;icon=null;if(!app.isEmpty())try{icon=getContext().getPackageManager().getApplicationIcon(app);}catch(Exception ignored){}}
        invalidate();
    }
    void stop(){running=false;message=null;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(message==null||settings==null||config==null)return;
        float scale=Math.min(getWidth(),getHeight())/340f,pad=6*scale;
        float margin=(config.shifting?config.shiftRange*1.414214f:0)*scale+2*scale;
        float width=Math.max(1,Math.min(getWidth()-2*margin,getWidth()*settings.number("width",90,20,100)/100f));
        text.setTextSize(settings.number("size",18,8,50)*scale);text.setColor(Color.parseColor(settings.color("color","#FFFFFF")));
        int lines=settings.number("lines",2,1,5);boolean scroll=settings.bool("scroll",true);
        boolean showIcon=settings.bool("icon",true)&&icon!=null;
        float iconSize=showIcon?settings.number("icon_size",20,8,50)*scale:0;
        float textHeight=(scroll?1:lines)*text.getFontSpacing();
        float height=textHeight+2*pad+(showIcon?iconSize+pad:0);
        // Width takes priority. Move the card inward instead of narrowing it at its requested Y position.
        float radius=Math.max(1,Math.min(getWidth(),getHeight())/2f-margin);
        float minimumHeight=Math.min(height,(scroll?1:lines)*10*scale+6*scale+(showIcon?11*scale:0));
        float maxWidth=2*(float)Math.sqrt(Math.max(0,radius*radius-minimumHeight*minimumHeight/4));
        width=Math.max(1,Math.min(width,maxWidth));
        float maxHeight=2*(float)Math.sqrt(Math.max(0,radius*radius-width*width/4));
        float fit=Math.min(1,maxHeight/height);
        height*=fit;pad*=fit;iconSize*=fit;textHeight*=fit;text.setTextSize(text.getTextSize()*fit);
        float halfChord=(float)Math.sqrt(Math.max(0,radius*radius-height*height/4));
        float centerX=getWidth()*settings.number("x",50,0,100)/100f;
        float horizontalTravel=Math.max(0,halfChord-width/2);
        centerX=Math.max(getWidth()/2f-horizontalTravel,Math.min(getWidth()/2f+horizontalTravel,centerX));
        float farX=Math.abs(centerX-getWidth()/2f)+width/2;
        float verticalTravel=Math.max(0,(float)Math.sqrt(Math.max(0,radius*radius-farX*farX))-height/2);
        float centerY=getHeight()*settings.number("y",82,0,100)/100f;
        centerY=Math.max(getHeight()/2f-verticalTravel,Math.min(getHeight()/2f+verticalTravel,centerY));
        float x=centerX-width/2,y=centerY-height/2;
        canvas.save();canvas.translate(dx,dy);
        background.setColor(Color.parseColor(settings.color("background","#000000")));background.setAlpha(settings.number("opacity",75,0,100)*255/100);
        canvas.drawRoundRect(new RectF(x,y,x+width,y+height),5*scale,5*scale,background);
        if(showIcon){iconSize=Math.min(iconSize,Math.max(1,width-2*pad));int size=Math.max(1,Math.round(iconSize));int ix=Math.round(centerX-size/2f),iy=Math.round(y+pad);icon.setBounds(ix,iy,ix+size,iy+size);icon.draw(canvas);}
        float left=x+pad,available=Math.max(1,width-2*pad);
        float textTop=y+pad+(showIcon?iconSize+pad:0);
        canvas.clipRect(left,textTop,x+width-pad,y+height-pad);
        if(scroll){
            String value=message.text.replace('\n',' ');float length=text.measureText(value),position=left+Math.max(0,(available-length)/2);
            if(length>available){float distance=(SystemClock.elapsedRealtime()-message.started)/1000f*settings.number("speed",28,5,360)*scale;position=left-(distance%(length+available+30*scale));if(position+length<left)position+=length+available+30*scale;}
            Paint.FontMetrics fm=text.getFontMetrics();canvas.drawText(value,position,textTop+(textHeight-fm.ascent-fm.descent)/2,text);
            if(running&&length>available)postInvalidateOnAnimation();
        }else{
            StaticLayout layout=StaticLayout.Builder.obtain(message.text,0,message.text.length(),text,Math.max(1,Math.round(available)))
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
            canvas.translate(left,textTop+(textHeight-layout.getHeight())/2);layout.draw(canvas);
        }
        canvas.restore();
    }
}
