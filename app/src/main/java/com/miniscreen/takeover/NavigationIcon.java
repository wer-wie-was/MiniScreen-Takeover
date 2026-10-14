package com.miniscreen.takeover;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small vector symbols for the five main destinations. */
final class NavigationIcon extends View {
    private final int tab;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    NavigationIcon(Context c,int tab,int color){super(c);this.tab=tab;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);
        if(tab==0){c.drawRoundRect(3,3,21,21,3,3,paint);c.drawCircle(8,8,1,paint);Path p=new Path();p.moveTo(4,18);p.lineTo(10,12);p.lineTo(14,16);p.lineTo(17,12);p.lineTo(21,16);c.drawPath(p,paint);}
        else if(tab==1){for(int y=4;y<=14;y+=10)for(int x=4;x<=14;x+=10)c.drawRoundRect(x,y,x+6,y+6,1,1,paint);}
        else if(tab==2){Path p=new Path();p.moveTo(2,11);p.lineTo(12,3);p.lineTo(22,11);p.moveTo(5,9);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,9);c.drawPath(p,paint);}
        else if(tab==3){Path p=new Path();p.moveTo(5,17);p.lineTo(7,14);p.lineTo(7,9);p.cubicTo(7,2,17,2,17,9);p.lineTo(17,14);p.lineTo(19,17);p.close();c.drawPath(p,paint);c.drawArc(10,18,14,22,0,180,false,paint);}
        else {c.drawCircle(12,12,3,paint);c.drawCircle(12,12,7,paint);for(int a=0;a<360;a+=45){double t=Math.toRadians(a);c.drawLine(12+7*(float)Math.cos(t),12+7*(float)Math.sin(t),12+10*(float)Math.cos(t),12+10*(float)Math.sin(t),paint);}}
        c.restore();
    }
}
