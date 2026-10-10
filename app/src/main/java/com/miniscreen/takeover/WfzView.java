package com.miniscreen.takeover;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Native retained WFZ scene shared by preview and rear display; no frame loop or firmware. */
final class WfzView extends View {
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor();
    private final boolean preview;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private WfzScene scene;private Config config;private String key="",error="";private int generation,battery=-1;
    private float dx,dy;private boolean running,healthPending;private long lastHealth,lastBucket=Long.MIN_VALUE;
    private JSONObject health=new JSONObject();
    WfzView(Context c,boolean preview){super(c);this.preview=preview;}
    void configure(Config c,File profile){config=c;String next=c.wfz.isEmpty()?"":new File(profile,c.wfz).getAbsolutePath();
        if(!key.equals(next)){key=next;scene=null;error="";int request=++generation;health=new JSONObject();lastHealth=0;
            if(!next.isEmpty())LOADER.execute(()->{try{WfzScene loaded=WfzScene.load(new File(next));post(()->{if(request==generation){scene=loaded;update(battery,dx,dy,running);invalidate();}});}catch(Exception ex){post(()->{if(request==generation){error=I18n.get(R.string.wfz_invalid)+"\n"+ex.getMessage();invalidate();}});}});
        }lastHealth=0;lastBucket=Long.MIN_VALUE;invalidate();
    }
    void update(int level,float x,float y,boolean active){
        long now=System.currentTimeMillis(),bucket=now/(config!=null&&config.wfzSeconds?1000:60000);
        boolean changed=battery!=level||dx!=x||dy!=y||running!=active||bucket!=lastBucket;
        battery=level;dx=x;dy=y;running=active;lastBucket=bucket;
        if(config!=null&&!config.wfzHealth&&health.length()>0){health=new JSONObject();changed=true;}
        if(active&&config!=null&&config.wfzHealth&&scene!=null&&scene.health&&!healthPending&&(lastHealth==0||SystemClock.elapsedRealtime()-lastHealth>=60000)){
            lastHealth=SystemClock.elapsedRealtime();healthPending=true;int request=generation;
            WfzHealth.read(getContext().getApplicationContext(),preview,LOADER,data->post(()->{healthPending=false;if(request==generation&&running&&config!=null&&config.wfzHealth){health=data;invalidate();}}));
        }
        if(changed)invalidate();
    }
    void stop(){running=false;lastHealth=0;health=new JSONObject();}
    void dispose(){stop();generation++;scene=null;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(config==null)return;
        canvas.drawColor(Color.BLACK);
        if(scene==null){paint.setColor(Color.WHITE);paint.setTextSize(Math.max(10,getWidth()/24f));canvas.drawText(error.isEmpty()?I18n.get(key.isEmpty()?R.string.wfz_empty:R.string.wfz_loading):I18n.get(R.string.wfz_invalid),8,getHeight()/2f,paint);return;}
        float size=Math.min(getWidth(),getHeight()),margin=config.shifting?config.shiftRange*size/340f*1.414214f:0;
        int designW=config.wfzSize>0?config.wfzSize:scene.width,designH=config.wfzSize>0?config.wfzSize:scene.height;
        float available=Math.max(1,size-2*margin);float scale=config.wfzCrop?Math.max(available/designW,available/designH):Math.min(available/designW,available/designH);
        canvas.save();Path circle=new Path();circle.addCircle(getWidth()/2f,getHeight()/2f,size/2f,Path.Direction.CW);canvas.clipPath(circle);
        canvas.translate((getWidth()-designW*scale)/2+dx,(getHeight()-designH*scale)/2+dy);canvas.scale(scale,scale);
        Calendar now=Calendar.getInstance();
        for(WfzScene.Part p:scene.parts){
            if(p.widget!=null){WfzWidgets.Choice choice=p.widget.selected(config);String v=widgetValue(choice,now);float progress=-1;
                if(choice.type.equals("battery")&&battery>=0)progress=battery/100f;
                if(choice.type.equals("steps")&&!v.equals("--"))try{progress=Float.parseFloat(v)/config.wfzStepGoal;}catch(Exception ignored){}
                WfzWidgetPainter.draw(canvas,p,config,v,progress);continue;}
            if(p.type.equals("statusbar"))continue;
            if((p.type.equals("am")||p.type.equals("pm"))&&(config.twentyFour||!p.type.equals(now.get(Calendar.AM_PM)==Calendar.AM?"am":"pm")))continue;
            if(p.frames!=null){
                int index;
                if(p.type.equals("batteryimage")){index=WfzMath.batteryFrame(battery,p.frames.length);}
                else index=p.type.equals("week")?now.get(Calendar.DAY_OF_WEEK)-1:now.get(Calendar.MONTH);
                Bitmap frame=p.frames[index];paint.setColor(Color.WHITE);canvas.drawBitmap(frame,null,new RectF(p.x,p.y,p.x+p.w,p.y+p.h),paint);
                if(p.type.equals("batteryimage")&&battery<0){WfzScene.Part missing=new WfzScene.Part();missing.x=p.x;missing.y=p.y;missing.w=Math.min(40,p.w);missing.h=16;drawText(canvas,missing,"--");}
                continue;
            }
            if(p.image!=null){paint.setColor(Color.WHITE);canvas.drawBitmap(p.image,null,new RectF(p.x,p.y,p.x+p.w,p.y+p.h),paint);continue;}
            if(p.hour!=null){float second=config.wfzSeconds?now.get(Calendar.SECOND):0;float minute=now.get(Calendar.MINUTE)+second/60f;
                hand(canvas,p.hour,p.x,p.y,p.w,p.h,(now.get(Calendar.HOUR)+minute/60f)*30);hand(canvas,p.minute,p.x,p.y,p.w,p.h,minute*6);
                if(config.wfzSeconds&&p.second!=null)hand(canvas,p.second,p.x,p.y,p.w,p.h,second*6);continue;}
            if(p.type.equals("second")&&!config.wfzSeconds)continue;
            String text=value(p.type,now);
            if(p.digit>=0&&p.digit<text.length())text=text.substring(p.digit,p.digit+1);
            drawText(canvas,p,text);
        }
        for(WfzScene.Part p:scene.parts)if(p.type.equals("statusbar"))WfzWidgetPainter.status(canvas,p.x,p.y,battery);
        canvas.restore();
    }
    private void hand(Canvas canvas,Bitmap image,float x,float y,float width,float height,float angle){
        // Fit each hand into the XML reference canvas without stretching narrow PNGs.
        float scale=Math.min(width/image.getWidth(),height/image.getHeight());
        float drawWidth=image.getWidth()*scale,drawHeight=image.getHeight()*scale;
        canvas.save();canvas.rotate(angle,x,y);
        canvas.drawBitmap(image,null,new RectF(x-drawWidth/2f,y-drawHeight/2f,x+drawWidth/2f,y+drawHeight/2f),paint);
        canvas.restore();
    }
    private String value(String type,Calendar t){switch(type){
        case "timedigital":return two(config.twentyFour?t.get(Calendar.HOUR_OF_DAY):(t.get(Calendar.HOUR)==0?12:t.get(Calendar.HOUR)))+":"+two(t.get(Calendar.MINUTE));
        case "hour":return two(config.twentyFour?t.get(Calendar.HOUR_OF_DAY):(t.get(Calendar.HOUR)==0?12:t.get(Calendar.HOUR)));
        case "minute":return two(t.get(Calendar.MINUTE));case "second":return two(t.get(Calendar.SECOND));
        case "day":return two(t.get(Calendar.DAY_OF_MONTH));case "month":return two(t.get(Calendar.MONTH)+1);case "year":return ""+t.get(Calendar.YEAR);
        case "weekday":return new java.text.SimpleDateFormat("EEE",I18n.locale()).format(t.getTime());
        case "date":return new java.text.SimpleDateFormat("dd.MM",I18n.locale()).format(t.getTime());
        case "distance":return metric("distance","wfz_health_distance","distanceKm",true);
        case "calories":return metric("calories","wfz_health_calories","activeCalories",false);
        case "weather":case "workoutdistance":return "--";
        case "battery":return battery>=0?""+battery:"--";
        case "steps":return TakeoverControl.prefs(getContext()).getBoolean("pebble_health_steps",false)&&validHealth("stepsTime",86400)&&health.optLong("dayStart",-1)==java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()?health.optString("steps","--"):"--";
        case "heart":return TakeoverControl.prefs(getContext()).getBoolean("pebble_health_heart",false)&&validHealth("heartTime",Config.clamp(TakeoverControl.prefs(getContext()).getInt("pebble_health_max_age",15),1,120)*60)?health.optString("heartRate","--"):"--";
        default:return "--";}}
    private String widgetValue(WfzWidgets.Choice c,Calendar now){
        if(c.type.equals("date")){String pattern=c.model==1?"MM-dd":c.model==2?"EEE":c.model==5?"dd":c.model==6||c.model==7?"dd.MM.yyyy":"dd.MM";return new java.text.SimpleDateFormat(pattern,I18n.locale()).format(now.getTime());}
        String result=value(c.type,now);return c.type.equals("battery")&&!result.equals("--")?result+"%":result;
    }
    private String metric(String prefix,String pref,String field,boolean decimal){
        long today=java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond();
        if(!TakeoverControl.prefs(getContext()).getBoolean(pref,false)||!validHealth(prefix+"Time",86400)||health.optLong(prefix+"Day",-1)!=today||!health.has(field))return "--";
        double number=health.optDouble(field,-1);if(Double.isNaN(number)||Double.isInfinite(number)||number<0)return "--";
        return decimal?String.format(I18n.locale(),"%.1f",number):""+Math.round(number);
    }
    private boolean validHealth(String field,int seconds){long age=System.currentTimeMillis()/1000-health.optLong(field,0);return config.wfzHealth&&age>=0&&age<=seconds;}
    private static String two(int n){return String.format(Locale.ROOT,"%02d",n);}
    private void drawText(Canvas canvas,WfzScene.Part p,String value){
        Map<String,Bitmap> glyphs=scene.fonts.get(p.font);float width=0,height=0;boolean bitmap=glyphs!=null;
        if(bitmap)for(int i=0;i<value.length();i++){Bitmap b=glyphs.get(value.substring(i,i+1));if(b==null){bitmap=false;break;}width+=b.getWidth()+p.space;height=Math.max(height,b.getHeight());}
        canvas.save();canvas.clipRect(p.x,p.y,p.x+p.w,p.y+p.h);
        if(bitmap){width=Math.max(1,width-p.space);float scale=Math.min(1,Math.min(p.w/width,p.h/Math.max(1,height)));float x=p.x+(p.align==72?p.w-width*scale:p.align==68?(p.w-width*scale)/2:0);float y=p.y+(p.h-height*scale)/2;
            for(int i=0;i<value.length();i++){Bitmap b=glyphs.get(value.substring(i,i+1));canvas.drawBitmap(b,null,new RectF(x,y,x+b.getWidth()*scale,y+b.getHeight()*scale),paint);x+=(b.getWidth()+p.space)*scale;}
        }else{paint.setColor(Color.WHITE);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(Math.max(1,p.h*.8f));float w=paint.measureText(value);if(w>p.w)paint.setTextSize(paint.getTextSize()*p.w/w);w=paint.measureText(value);Paint.FontMetrics fm=paint.getFontMetrics();float x=p.x+(p.align==72?p.w-w:p.align==68?(p.w-w)/2:0);canvas.drawText(value,x,p.y+(p.h-fm.ascent-fm.descent)/2,paint);}
        canvas.restore();
    }
}
