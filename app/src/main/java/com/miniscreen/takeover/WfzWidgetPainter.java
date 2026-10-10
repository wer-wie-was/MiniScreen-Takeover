package com.miniscreen.takeover;
import android.graphics.*;

/** Own vector replacements for firmware-owned Amazfit widgets, not copied OEM assets. */
final class WfzWidgetPainter {
    static void draw(Canvas c,WfzScene.Part p,Config config,String value,float progress){
        WfzWidgets.Choice choice=p.widget.selected(config);
        float w=p.widget.explicitWidth?p.w:WfzWidgets.width(choice),h=p.widget.explicitHeight?p.h:WfzWidgets.height(choice);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        int accent=Color.parseColor(config.wfzWidgetColor);
        c.save();c.clipRect(p.x,p.y,p.x+w,p.y+h);
        boolean round=choice.model==0||!choice.type.equals("date")&&choice.model!=1&&choice.model!=2;
        if(round){
            float radius=Math.min(w,h)/2f-3;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0x99666666);
            c.drawCircle(p.x+w/2,p.y+h/2,radius,paint);
            if(progress>=0){paint.setColor(accent);RectF r=new RectF(p.x+w/2-radius,p.y+h/2-radius,p.x+w/2+radius,p.y+h/2+radius);c.drawArc(r,-90,Math.min(1,progress)*360,false,paint);}
            paint.setStyle(Paint.Style.FILL);
            icon(c,paint,choice.type,p.x+w/2,p.y+h*.30f,Math.min(w,h)*.22f,accent);
            text(c,paint,value,p.x+w/2,p.y+h*.60f,w*.83f,h*.26f);
            String unit=unit(choice.type);if(!unit.isEmpty())text(c,paint,unit,p.x+w/2,p.y+h*.80f,w*.8f,h*.13f);
        }else if(choice.type.equals("date")){
            text(c,paint,value,p.x+w/2,p.y+h/2,w-2,h*.78f);
        }else{
            icon(c,paint,choice.type,p.x+12,p.y+h/2,18,accent);
            String unit=unit(choice.type);text(c,paint,value+(unit.isEmpty()?"":" "+unit),p.x+22+(w-24)/2,p.y+h/2,w-24,h*.65f);
        }
        if(p.mask!=null)c.drawBitmap(p.mask,null,new RectF(p.x,p.y,p.x+w,p.y+h),paint);
        c.restore();
    }
    static void status(Canvas c,float x,float y,int level){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);icon(c,p,"battery",x,y+7,14,0xFF70E090);
        text(c,p,level<0?"--":level+"%",x+24,y+7,30,11);
    }
    private static String unit(String type){switch(type){case "distance":case "workoutdistance":return "km";case "heart":return "bpm";case "calories":return "kcal";case "weather":return "°C";default:return "";}}
    private static void text(Canvas c,Paint p,String value,float x,float y,float width,float size){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(1,size));float measured=p.measureText(value);if(measured>width)p.setTextSize(p.getTextSize()*Math.max(1,width)/measured);
        Paint.FontMetrics fm=p.getFontMetrics();c.drawText(value,x,y-(fm.ascent+fm.descent)/2,p);
    }
    private static void icon(Canvas c,Paint p,String type,float x,float y,float size,int color){
        c.save();c.translate(x-size/2,y-size/2);c.scale(size/24,size/24);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(2);
        if(type.equals("heart")){
            Path a=new Path();a.moveTo(12,21);a.cubicTo(-4,10,3,-2,12,7);a.cubicTo(21,-2,28,10,12,21);c.drawPath(a,p);
        }else if(type.equals("battery")){
            p.setStyle(Paint.Style.STROKE);c.drawRoundRect(new RectF(2,6,20,18),2,2,p);p.setStyle(Paint.Style.FILL);c.drawRect(21,10,23,14,p);c.drawRect(5,9,16,15,p);
        }else if(type.equals("weather")){
            c.drawCircle(8,11,5,p);c.drawCircle(14,8,6,p);c.drawCircle(19,12,4,p);c.drawRoundRect(new RectF(3,10,23,17),3,3,p);
        }else if(type.equals("calories")){
            Path a=new Path();a.moveTo(12,1);a.cubicTo(15,8,22,10,19,18);a.cubicTo(16,25,4,23,4,16);a.cubicTo(3,12,8,6,9,5);a.lineTo(10,12);a.close();c.drawPath(a,p);
        }else if(type.equals("date")){
            p.setStyle(Paint.Style.STROKE);c.drawRoundRect(new RectF(3,4,21,22),2,2,p);c.drawLine(3,9,21,9,p);c.drawLine(7,1,7,6,p);c.drawLine(17,1,17,6,p);
        }else if(type.equals("steps")||type.equals("distance")||type.equals("workoutdistance")){
            c.save();c.rotate(-25,7,13);c.drawOval(new RectF(3,5,10,18),p);c.drawCircle(7,21,3,p);c.restore();
            c.save();c.rotate(25,17,10);c.drawOval(new RectF(14,1,21,14),p);c.drawCircle(17,17,3,p);c.restore();
        }else{
            p.setStyle(Paint.Style.STROKE);c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,2,p);
        }
        p.setStyle(Paint.Style.FILL);c.restore();
    }
}
