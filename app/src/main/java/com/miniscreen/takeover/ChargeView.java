package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.File;

/** Native overlay, independent of both clock and user-supplied HTML. */
final class ChargeView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Config config;
    private boolean plugged;
    private int level;
    private float shiftX,shiftY;
    private Typeface font=Typeface.DEFAULT;
    private String fontKey="";
    ChargeView(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void configure(Config c,File folder){
        config=c;String key=folder.getPath()+":"+c.chargeFontFile+":"+c.chargeFont+":"+c.chargeBold;
        if(!key.equals(fontKey)){
            fontKey=key;
            try{font=c.chargeFontFile.isEmpty()?Typeface.create(c.chargeFont,c.chargeBold?Typeface.BOLD:Typeface.NORMAL):Typeface.createFromFile(new File(folder,c.chargeFontFile));if(c.chargeBold)font=Typeface.create(font,Typeface.BOLD);}
            catch(RuntimeException e){font=Typeface.create(Typeface.DEFAULT,c.chargeBold?Typeface.BOLD:Typeface.NORMAL);}
        }
        invalidate();
    }
    void battery(boolean plugged,int level){this.plugged=plugged;this.level=Config.clamp(level,0,100);invalidate();}
    void shift(float x,float y){shiftX=x;shiftY=y;invalidate();}
    boolean animated(){return config!=null&&config.chargeEnabled&&config.chargeCircle&&config.chargeAnimated&&plugged&&level>0;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);Config c=config;if(c==null||!c.chargeEnabled||(!plugged&&!c.chargeAlways))return;
        float scale=Math.min(getWidth(),getHeight())/340f;
        canvas.save();canvas.translate(shiftX,shiftY);
        paint.setShader(null);
        if(c.chargeCircle&&plugged){
            float width=c.chargeRingWidth*scale,margin=3*scale+(c.shifting?c.shiftRange*scale:0)+width/2;
            RectF ring=new RectF(margin,margin,getWidth()-margin,getHeight()-margin);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(Color.parseColor(c.chargeRingColor));
            if(c.chargeGradient){int first=Color.parseColor(c.chargeRingColor),second=Color.parseColor(c.chargeRingColorSecond);SweepGradient gradient=new SweepGradient(ring.centerX(),ring.centerY(),new int[]{first,second,first},new float[]{0,.5f,1});Matrix rotation=new Matrix();rotation.setRotate(-90,ring.centerX(),ring.centerY());gradient.setLocalMatrix(rotation);paint.setShader(gradient);}
            paint.setAlpha(45);canvas.drawOval(ring,paint);
            float sweep=360*level/100f;paint.setAlpha(255);canvas.drawArc(ring,-90,sweep,false,paint);
            if(c.chargeAnimated&&sweep>0){
                double phase=(System.currentTimeMillis()%(c.chargeAnimationSeconds*1000L))/(c.chargeAnimationSeconds*1000.0);
                // The accent stays inside the charged portion, including at low battery levels.
                float accent=Math.min(22,sweep),start=-90+(sweep-accent)*(float)(.5-.5*Math.cos(phase*2*Math.PI));
                paint.setShader(null);paint.setColor(Color.parseColor(c.chargeAccentColor));paint.setAlpha(200);canvas.drawArc(ring,start,accent,false,paint);
            }
        }
        paint.setShader(null);paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setTypeface(font);paint.setTextSize(c.chargeSize*scale);
        String text=String.format(I18n.locale(),"%d%%",level);Paint.FontMetrics fm=paint.getFontMetrics();
        float height=fm.descent-fm.ascent,iconW=height*1.55f,gap=height*.3f,total=iconW+gap+paint.measureText(text);
        float margin=3*scale+(c.shifting?c.shiftRange*scale:0),fit=Math.min(1,Math.min(Math.max(1,getWidth()-2*margin)/total,Math.max(1,getHeight()-2*margin)/height));
        height*=fit;iconW*=fit;gap*=fit;paint.setTextSize(c.chargeSize*scale*fit);fm=paint.getFontMetrics();total=iconW+gap+paint.measureText(text);
        float x=Math.max(margin,Math.min(getWidth()-margin-total,getWidth()*c.chargeX/100f-total/2));
        float y=Math.max(margin,Math.min(getHeight()-margin-height,getHeight()*c.chargeY/100f-height/2));
        float tip=height*.12f,stroke=Math.max(scale,height*.08f),bodyW=iconW-tip;
        paint.setColor(Color.parseColor(c.chargeSymbolColor));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);
        canvas.drawRoundRect(new RectF(x+stroke/2,y+stroke/2,x+bodyW-stroke/2,y+height-stroke/2),height*.13f,height*.13f,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawRect(x+bodyW,y+height*.32f,x+iconW,y+height*.68f,paint);
        float inset=stroke*2;canvas.drawRect(x+inset,y+inset,x+inset+Math.max(0,bodyW-2*inset)*level/100f,y+height-inset,paint);
        paint.setColor(Color.parseColor(c.chargeColor));canvas.drawText(text,x+iconW+gap,y+(height-fm.ascent-fm.descent)/2,paint);
        canvas.restore();
    }
}
