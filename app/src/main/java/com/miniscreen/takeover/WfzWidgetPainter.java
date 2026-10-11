package com.miniscreen.takeover;
import android.graphics.*;

/** Independent vector/pixel reconstructions; no editor or firmware assets. */
final class WfzWidgetPainter {
    private static final int RED=0xFFFF0000,GREEN=0xFF00FF00,CYAN=0xFF00FFFF,YELLOW=0xFFFFFF00;
    static void draw(Canvas c,WfzScene.Part part,Config config,String value,float progress){
        WfzWidgets.Choice q=part.widget.selected(config);
        float bw=WfzWidgets.width(q),bh=WfzWidgets.height(q);
        float w=part.widget.explicitWidth?part.w:bw,h=part.widget.explicitHeight?part.h:bh;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int custom=Color.parseColor(config.wfzWidgetColor);
        c.save();c.translate(part.x,part.y);c.clipRect(0,0,w,h);c.scale(w/bw,h/bh);
        if(!WfzWidgetCatalog.known(q.data,q.model))text(c,p,value,2,2,bw-4,bh-4,Color.WHITE,0,false,false);
        else if(q.data==0)status(c,0,0,bw,bh,value,progress);
        else if(q.data==6)date(c,p,q.model,value,bw,bh,config.wfzOriginalColors?YELLOW:custom);
        else if(q.model==1||q.model==4)row(c,p,q,value,progress,config.wfzOriginalColors?GREEN:custom);
        else dial(c,p,q,value,progress,config.wfzOriginalColors,custom,bw);
        if(part.mask!=null){p.reset();p.setFilterBitmap(true);c.drawBitmap(part.mask,null,new RectF(0,0,bw,bh),p);}
        c.restore();
    }
    private static void date(Canvas c,Paint p,int m,String v,float w,float h,int yellow){
        int color=WfzWidgetCatalog.black(6,m)?Color.BLACK:m==6||m==7?yellow:Color.WHITE;
        // x/y is the widget box, not the first visible pixel. Keep internal padding.
        switch(m){
            case 1:text(c,p,v,31,3,67,16,color,0,false,false);break;
            case 2:text(c,p,v,2,2,w-4,16,color,1,true,false);break;
            case 3:text(c,p,v,4,1,w-8,16,color,1,false,false);break;
            case 4:lcd(c,p,v,24,2,108,16,color);break;
            case 5:text(c,p,v,2,7,28,19,color,1,false,false);break;
            case 6:text(c,p,v,13,7,w-26,18,color,1,true,true);break;
            case 7:text(c,p,v,5,5,w-10,22,color,1,true,true);break;
            case 8:text(c,p,v,19,1,w-19,16,color,0,false,false);break;
            case 9:lcd(c,p,v,50,1,112,16,color);break;
            case 10:text(c,p,v,32,1,w-32,16,color,0,false,false);break;
            case 11:
                int split=v.lastIndexOf("  ");
                if(split>0){lcd(c,p,v.substring(0,split),20,1,99,16,color);text(c,p,v.substring(split+2),126,1,30,16,color,0,false,false);}
                else lcd(c,p,v,20,1,w-20,16,color);
                break;
            case 12:text(c,p,v,10,2,w-10,17,color,0,false,false);break;
            default:text(c,p,"--",0,0,w,h,color,1,false,false);
        }
    }
    private static void row(Canvas c,Paint p,WfzWidgets.Choice q,String v,float progress,int accent){
        boolean black=q.model==4;int color=black?Color.BLACK:Color.WHITE,ic=black?Color.BLACK:accent;
        float ix=24,tx=45,y=2,h=18;
        switch(q.data){case 10:ix=28;tx=49;y=1;break;case 5:ix=33;tx=56;break;
            case 12:ix=38;tx=64;break;case 3:ix=22;tx=49;y=4;h=17;break;
            case 2:ix=23;tx=47;break;case 8:ix=22;tx=48;break;default:break;}
        if(black&&q.data==5){lcd(c,p,v,40,2,60,17,color);return;}
        icon(c,p,q.type,ix,y,18,ic,progress);
        String label=v+(q.data==8&&!v.equals("--")?"°C":"");
        if(black)lcd(c,p,label,tx,y,100-tx,h,color);
        else text(c,p,label,tx,y,100-tx,h,color,0,false,false);
    }
    private static void dial(Canvas c,Paint p,WfzWidgets.Choice q,String v,float progress,boolean original,int custom,float size){
        int m=q.model,accent=original?(m==0?RED:m==2?CYAN:YELLOW):custom;
        if(m==0)ring(c,p,size,q.data==10&&progress>=0?accent:Color.WHITE,4,q.data==10?progress:-1,false);
        else if(WfzWidgetCatalog.progressRing(q.data,m)){
            int color=(q.data==10||q.data==1)&&progress<0?0xFF888888:accent;
            ring(c,p,size,color,m==2?2:5,progress,m==2);
        }
        float is=m==3?20:18,iy=m==3?27:21;
        icon(c,p,q.type,(size-is)/2,iy,is,m==0?accent:Color.WHITE,progress);
        String label=v+(q.data==8&&!v.equals("--")?"°C":"");
        text(c,p,label,4,m==3?54:49,size-8,m==0?24:m==2?18:22,m==3?accent:Color.WHITE,1,m!=2,m==3);
    }
    private static void ring(Canvas c,Paint p,float size,int color,float stroke,float progress,boolean ticks){
        float radius=size/2-stroke/2-1;boolean known=progress>=0&&!Float.isNaN(progress)&&!Float.isInfinite(progress);
        float fraction=known?Math.max(0,Math.min(1,progress)):1;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.BUTT);
        if(ticks){for(int i=0;i<60;i++){
            p.setColor(known&&i>=Math.round(fraction*60)?Color.WHITE:color);
            double a=Math.toRadians(i*6-90);float x=size/2,y=size/2;
            c.drawLine(x+(radius-3)*(float)Math.cos(a),y+(radius-3)*(float)Math.sin(a),x+radius*(float)Math.cos(a),y+radius*(float)Math.sin(a),p);
        }}else{
            RectF b=new RectF(size/2-radius,size/2-radius,size/2+radius,size/2+radius);
            if(known){p.setColor(Color.WHITE);c.drawOval(b,p);}p.setColor(color);c.drawArc(b,-90,360*fraction,false,p);
        }p.setStyle(Paint.Style.FILL);
    }
    static void status(Canvas c,float x,float y,float w,float h,String value,float progress){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);c.save();c.translate(x,y);c.clipRect(0,0,w,h);c.scale(w/320,h/20);
        // No invented watch connectivity or airplane state.
        icon(c,p,"battery",155,1,16,GREEN,progress);text(c,p,value,175,2,48,16,Color.WHITE,0,false,false);c.restore();
    }
    private static void lcd(Canvas c,Paint p,String v,float x,float y,float w,float h,int color){
        if(!WfzLcdFont.supports(v)){text(c,p,v,x,y,w,h,color,0,false,false);return;}
        p.setColor(color);WfzLcdFont.draw(c,p,v,x,y,w,h);
    }
    private static void text(Canvas c,Paint p,String v,float x,float y,float w,float h,int color,int align,boolean bold,boolean italic){
        if(w<=0||h<=0||v.isEmpty())return;
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextAlign(Paint.Align.LEFT);p.setTextScaleX(1);
        p.setTypeface(Typeface.create("sans-serif-condensed",(bold?Typeface.BOLD:Typeface.NORMAL)|(italic?Typeface.ITALIC:0)));
        // Fit to font line metrics, so placeholder hyphens keep normal proportions.
        p.setTextSize(h);Paint.FontMetrics fm=p.getFontMetrics();
        float lineHeight=fm.descent-fm.ascent;
        if(lineHeight>h){p.setTextSize(p.getTextSize()*h/lineHeight);fm=p.getFontMetrics();}
        float measured=p.measureText(v);if(measured>w)p.setTextScaleX(w/measured);
        float used=p.measureText(v);
        float baseline=y+(h-(fm.descent-fm.ascent))/2-fm.ascent;
        c.drawText(v,x+(align==1?(w-used)/2:0),baseline,p);p.setTextScaleX(1);
    }
    private static void icon(Canvas c,Paint p,String type,float x,float y,float size,int color,float progress){
        c.save();c.translate(x,y);c.scale(size/24,size/24);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(2);
        switch(type){
            case "heart":{Path a=new Path();a.moveTo(12,22);a.cubicTo(-4,10,3,-2,12,7);a.cubicTo(21,-2,28,10,12,22);c.drawPath(a,p);break;}
            case "battery":{p.setStyle(Paint.Style.STROKE);c.drawRect(5,4,19,23,p);p.setStyle(Paint.Style.FILL);c.drawRect(9,1,15,4,p);
                if(progress>=0&&!Float.isNaN(progress)){float f=Math.max(0,Math.min(1,progress));c.drawRect(8,20-13*f,16,20,p);}break;}
            case "weather":{p.setStyle(Paint.Style.STROKE);c.drawCircle(17,6,4,p);p.setStyle(Paint.Style.FILL);c.drawCircle(8,13,5,p);c.drawCircle(13,11,6,p);c.drawCircle(19,15,4,p);c.drawRoundRect(new RectF(3,13,23,20),3,3,p);break;}
            case "calories":{Path a=new Path();a.moveTo(12,1);a.cubicTo(15,8,22,10,19,18);a.cubicTo(16,25,4,23,4,16);a.cubicTo(3,12,8,6,9,5);a.lineTo(10,12);a.close();c.drawPath(a,p);break;}
            case "steps":{c.drawRoundRect(new RectF(2,6,10,17),3,3,p);c.drawOval(new RectF(4,19,9,23),p);c.drawRoundRect(new RectF(14,1,22,12),3,3,p);c.drawOval(new RectF(15,14,20,18),p);break;}
            case "distance":{c.drawCircle(15,3,3,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);
                Path a=new Path();a.moveTo(7,11);a.lineTo(11,7);a.lineTo(17,11);a.lineTo(22,10);a.moveTo(12,8);a.lineTo(10,15);a.lineTo(4,20);a.moveTo(10,15);a.lineTo(17,16);a.lineTo(20,22);c.drawPath(a,p);p.setStrokeCap(Paint.Cap.BUTT);break;}
            case "workoutdistance":{Path a=new Path();a.moveTo(13,0);a.cubicTo(-7,10,29,9,6,24);a.lineTo(18,24);a.cubicTo(37,9,1,10,17,0);a.close();c.drawPath(a,p);break;}
            case "floors":{Path a=new Path();a.moveTo(2,22);a.lineTo(2,16);a.lineTo(8,16);a.lineTo(8,10);a.lineTo(14,10);a.lineTo(14,4);a.lineTo(20,4);a.lineTo(20,22);a.close();c.drawPath(a,p);break;}
            default:break;
        }p.setStyle(Paint.Style.FILL);c.restore();
    }
    private WfzWidgetPainter(){}
}
