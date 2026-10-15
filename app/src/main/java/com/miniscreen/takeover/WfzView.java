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
    private final Typeface weekdayTypeface;
    private WfzScene scene;private Config config;private String key="",error="";private int generation,battery=-1;
    private float dx,dy;private boolean running,healthPending;private long lastHealth,lastBucket=Long.MIN_VALUE;
    private JSONObject health=new JSONObject();
    WfzView(Context c,boolean preview){super(c);this.preview=preview;weekdayTypeface=c.getResources().getFont(R.font.wfz_weekday);}
    /** One offline frame; does not configure a profile, attach a surface or read health data. */
    static Bitmap snapshot(Context context,WfzScene scene,int edge){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Snapshot requires main thread");
        Config config=new Config();config.mode="wfz";config.shifting=false;config.wfzHealth=false;config.wfzSeconds=true;
        WfzView view=new WfzView(context,true);view.config=config;view.scene=scene;
        int spec=MeasureSpec.makeMeasureSpec(edge,MeasureSpec.EXACTLY);view.measure(spec,spec);view.layout(0,0,edge,edge);
        Bitmap image=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
        Path circle=new Path();circle.addCircle(edge/2f,edge/2f,edge/2f,Path.Direction.CW);canvas.clipPath(circle);view.draw(canvas);
        view.dispose();return image;
    }
    void configure(Config c,File profile){config=c;String path=c.wfz.isEmpty()?"":WfzGallery.face(getContext(),profile,c).getAbsolutePath();String next=path.isEmpty()?"":path+"|"+I18n.locale().toLanguageTag()+"|"+c.wfzVariants.toString();
        if(!key.equals(next)){key=next;scene=null;error="";int request=++generation;health=new JSONObject();lastHealth=0;
            Config snapshot=Config.from(c.json());
            if(!path.isEmpty())LOADER.execute(()->{try{WfzScene loaded=WfzScene.load(new File(path),snapshot);try{WfzImport.refreshInfo(new File(path),loaded);}catch(Exception ignored){}post(()->{if(request==generation){scene=loaded;update(battery,dx,dy,running);invalidate();}});}catch(Exception ex){post(()->{if(request==generation){error=I18n.get(R.string.wfz_invalid)+"\n"+ex.getMessage();invalidate();}});}});
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
        scale*=Config.clamp(config.wfzScale,50,100)/100f;
        canvas.save();Path circle=new Path();circle.addCircle(getWidth()/2f,getHeight()/2f,size/2f,Path.Direction.CW);canvas.clipPath(circle);
        // Center the actual scene, including when a source-resolution override
        // changes its scale. Pixel shifting remains a display-space offset.
        canvas.translate((getWidth()-scene.width*scale)/2+dx,(getHeight()-scene.height*scale)/2+dy);canvas.scale(scale,scale);
        Calendar now=Calendar.getInstance();
        for(WfzScene.Part p:scene.parts){
            // Native clock hands belong above the face and its widgets, even
            // when the WFZ lists timehand before datawidget elements.
            if(p.hour!=null||p.type.equals("hand"))continue;
            if(p.type.equals("arc")){
                float progress=progress(p.source,now);if(progress<0)continue;
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(p.stroke);paint.setStrokeCap(p.cap==0?Paint.Cap.BUTT:p.cap==1?Paint.Cap.ROUND:Paint.Cap.SQUARE);
                paint.setColor(config.wfzOriginalColors?p.color:Color.parseColor(config.wfzWidgetColor));
                canvas.drawArc(new RectF(p.x-p.w,p.y-p.h,p.x+p.w,p.y+p.h),p.start-90,(p.end-p.start)*progress,false,paint);
                paint.setStyle(Paint.Style.FILL);paint.setStrokeCap(Paint.Cap.BUTT);continue;
            }
            if(p.type.equals("weekfloat")&&p.threshold!=now.get(Calendar.DAY_OF_WEEK)-1)continue;
            if(p.type.equals("weathericon")){drawText(canvas,p,"--");continue;}
            if(p.type.equals("additive")){
                float fraction=progress(p.source,now);
                if(p.source.equals("battery")){if(p.threshold>=WfzMath.litBatterySegments(fraction,p.count))continue;}
                else if(fraction<0||fraction<(p.threshold+1f)/p.count)continue;
            }
            if(p.widget!=null){WfzWidgets.Choice choice=p.widget.selected(config);String v=widgetValue(choice,now);float progress=-1;
                if((choice.type.equals("battery")||choice.type.equals("statusbar"))&&battery>=0)progress=battery/100f;
                if(choice.type.equals("steps")&&!v.equals("--"))try{progress=Float.parseFloat(v)/config.wfzStepGoal;}catch(Exception ignored){}
                WfzWidgetPainter.draw(canvas,p,config,v,progress,weekdayTypeface);continue;}
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
            if(p.type.equals("second")&&!config.wfzSeconds)continue;
            String text=value(p.type,now);if(p.type.equals("day")&&!p.fillZero)text=""+now.get(Calendar.DAY_OF_MONTH);
            if(p.digit>=0&&p.digit<text.length())text=text.substring(p.digit,p.digit+1);
            drawText(canvas,p,text);
        }
        for(WfzScene.Part p:scene.parts)if(p.type.equals("hand")){
            if(p.source.equals("second")&&!config.wfzSeconds)continue;
            float second=config.wfzSeconds?now.get(Calendar.SECOND):0,minute=now.get(Calendar.MINUTE)+second/60f;
            float angle=p.source.equals("second")?second*6:p.source.equals("minute")?minute*6:(now.get(Calendar.HOUR)+minute/60f)*30;
            canvas.save();canvas.rotate(angle,p.x,p.y);canvas.drawBitmap(p.image,p.x-p.pivotX,p.y-p.pivotY,paint);canvas.restore();
        }
        for(WfzScene.Part p:scene.parts)if(p.hour!=null){
            float second=config.wfzSeconds?now.get(Calendar.SECOND):0;
            float minute=now.get(Calendar.MINUTE)+second/60f;
            hand(canvas,p.hour,p.x,p.y,p.w,p.h,(now.get(Calendar.HOUR)+minute/60f)*30);
            hand(canvas,p.minute,p.x,p.y,p.w,p.h,minute*6);
            if(config.wfzSeconds&&p.second!=null)hand(canvas,p.second,p.x,p.y,p.w,p.h,second*6);
        }
        for(WfzScene.Part p:scene.parts)if(p.type.equals("statusbar"))WfzWidgetPainter.status(canvas,p.x,p.y,p.w,p.h,battery<0?"--":battery+"%",battery<0?-1:battery/100f);
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
    private float progress(String type,Calendar now){
        if(type.equals("battery"))return battery<0?-1:Math.max(0,Math.min(1,battery/100f));
        if(type.equals("steps"))try{return Math.max(0,Math.min(1,Float.parseFloat(value(type,now))/config.wfzStepGoal));}catch(Exception ignored){}
        return -1;
    }
    private String widgetValue(WfzWidgets.Choice c,Calendar now){
        if(c.type.equals("date"))return WfzWidgetDate.value(c.model,now);
        String result=value(c.type.equals("statusbar")?"battery":c.type,now);
        if((c.type.equals("battery")||c.type.equals("statusbar"))&&!result.equals("--"))return result+"%";
        if(c.type.equals("distance")&&!result.equals("--")){
            double km=health.optDouble("distanceKm",Double.NaN);
            return Double.isNaN(km)?"--":String.format(Locale.ROOT,c.model==0?"%.1f":"%.2f",km);
        }
        return result;
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
        Map<String,Bitmap> glyphs=scene.fonts.get(p.font);
        if(p.nativeText&&p.type.equals("battery")&&!value.equals("--")&&glyphs!=null&&glyphs.containsKey(":"))value+=":";
        float width=0,height=0;boolean bitmap=glyphs!=null;
        if(bitmap)for(int i=0;i<value.length();i++){Bitmap b=glyphs.get(value.substring(i,i+1));if(b==null){bitmap=false;break;}width+=b.getWidth()+p.space;height=Math.max(height,b.getHeight());}
        canvas.save();if(!p.nativeText)canvas.clipRect(p.x,p.y,p.x+p.w,p.y+p.h);
        if(bitmap){width=Math.max(1,width-p.space);float scale=p.nativeText?1:Math.min(1,Math.min(p.w/width,p.h/Math.max(1,height)));float x=WfzMath.textX(p.x,p.w,width*scale,p.align);float y=p.nativeText?p.y:p.y+(p.h-height*scale)/2;
            for(int i=0;i<value.length();i++){Bitmap b=glyphs.get(value.substring(i,i+1));canvas.drawBitmap(b,null,new RectF(x,y,x+b.getWidth()*scale,y+b.getHeight()*scale),paint);x+=(b.getWidth()+p.space)*scale;}
        }else{paint.setColor(Color.WHITE);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(Math.max(1,p.h*.8f));float w=paint.measureText(value);if(!p.nativeText&&w>p.w)paint.setTextSize(paint.getTextSize()*p.w/w);w=paint.measureText(value);Paint.FontMetrics fm=paint.getFontMetrics();float x=WfzMath.textX(p.x,p.w,w,p.align);canvas.drawText(value,x,p.y+(p.h-fm.ascent-fm.descent)/2,paint);}
        canvas.restore();
    }
}
