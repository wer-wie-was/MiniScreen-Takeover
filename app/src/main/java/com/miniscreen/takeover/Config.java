package com.miniscreen.takeover;

import android.graphics.Color;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Portable profile settings. All layout lengths are relative to a 340px square. */
final class Config {
    String name=I18n.get(R.string.msg_046), mode="native", datePattern="EEE, dd.MM.yyyy", font="sans", imageFit="cover";
    String timeColor="#63E6DC", dateColor="#C7D0DF", backgroundColor="#000000";
    String image="", fontFile="", design="",wfz="";
    boolean wfzSeconds=false,wfzCrop=false,wfzHealth=false;int wfzSize=0,wfzStepGoal=10000;String wfzWidgetColor="#FF3B30";JSONObject wfzWidgets=new JSONObject();
    boolean twentyFour=true, seconds=false, leadingZero=true, showClock=true, showDate=true, bold=false, shifting=true, shiftBackground=false;
    int timeSize=68, dateSize=20, timeX=50, timeY=45, dateX=50, dateY=66;
    int shiftRange=8, shiftInterval=60, dim=45, brightness=25;
    boolean brightnessCurveEnabled=false;int brightnessCurveMinutes=1;org.json.JSONArray brightnessPoints;
    int windowZoom=180,windowSpeed=20;
    boolean windowMotion=true,chargeEnabled=false,chargeAlways=false,chargeGradient=false,chargeCircle=false,chargeAnimated=false,chargeBold=false;
    String chargeColor="#63E6DC",chargeSymbolColor="#63E6DC",chargeRingColor="#63E6DC",chargeRingColorSecond="#A78BFA",chargeAccentColor="#FFFFFF",chargeFont="sans",chargeFontFile="";
    int chargeSize=18,chargeX=50,chargeY=85,chargeRingWidth=3,chargeAnimationSeconds=10;
    static Config from(JSONObject j) {
        Config c=new Config();
        c.wfz=safePath(j.optString("wfz"));c.wfzSeconds=j.optBoolean("wfzSeconds",false);c.wfzCrop=j.optBoolean("wfzCrop",false);c.wfzHealth=j.optBoolean("wfzHealth",false);c.wfzSize=j.optInt("wfzSize",0);if(c.wfzSize!=0)c.wfzSize=clamp(c.wfzSize,100,1024);
        c.wfzStepGoal=clamp(j.optInt("wfzStepGoal",10000),1000,50000);c.wfzWidgetColor=color(j.optString("wfzWidgetColor"),c.wfzWidgetColor);JSONObject choices=j.optJSONObject("wfzWidgets");if(choices!=null){java.util.Iterator<String> keys=choices.keys();int count=0;while(keys.hasNext()&&count++<256){String key=keys.next(),value=choices.optString(key,"");if(key.matches("widget-[0-9]{1,3}")&&value.matches("[0-9]{1,3}:[0-9]{1,3}"))try{c.wfzWidgets.put(key,value);}catch(org.json.JSONException ignored){}}}
        c.name=j.optString("name",c.name); c.mode="wfz".equals(j.optString("mode"))?"wfz":"pebble".equals(j.optString("mode"))?"pebble":"html".equals(j.optString("mode"))?"html":"native";
        c.datePattern=j.optString("datePattern",c.datePattern); c.font=j.optString("font",c.font);
        if(c.name.isEmpty())c.name=I18n.get(R.string.msg_046);if(c.name.length()>80)c.name=c.name.substring(0,80);
        if(c.datePattern.isEmpty()||c.datePattern.length()>120)c.datePattern="dd.MM.yyyy";
        String fit=j.optString("imageFit");c.imageFit=fit.equals("contain")||fit.equals("window")?fit:"cover";
        c.timeColor=color(j.optString("timeColor"),c.timeColor); c.dateColor=color(j.optString("dateColor"),c.dateColor);
        c.backgroundColor=color(j.optString("backgroundColor"),c.backgroundColor);
        c.image=safePath(j.optString("image")); c.fontFile=safePath(j.optString("fontFile")); c.design=safePath(j.optString("design"));
        c.twentyFour=j.optBoolean("twentyFour",true); c.seconds=j.optBoolean("seconds",false); c.leadingZero=j.optBoolean("leadingZero",true);
        c.showClock=j.optBoolean("showClock",true); c.showDate=j.optBoolean("showDate",true); c.bold=j.optBoolean("bold",false); c.shifting=j.optBoolean("shifting",true); c.shiftBackground=j.optBoolean("shiftBackground",false);
        c.timeSize=clamp(j.optInt("timeSize",68),12,130); c.dateSize=clamp(j.optInt("dateSize",20),8,50);
        c.timeX=clamp(j.optInt("timeX",50),0,100); c.timeY=clamp(j.optInt("timeY",45),0,100);
        c.dateX=clamp(j.optInt("dateX",50),0,100); c.dateY=clamp(j.optInt("dateY",66),0,100);
        c.shiftRange=clamp(j.optInt("shiftRange",8),1,30); c.shiftInterval=clamp(j.optInt("shiftInterval",60),10,600);
        c.dim=clamp(j.optInt("dim",45),0,100); c.brightness=clamp(j.optInt("brightness",25),1,100);
        c.brightnessCurveEnabled=j.optBoolean("brightnessCurveEnabled",false);c.brightnessCurveMinutes=clamp(j.optInt("brightnessCurveMinutes",1),1,30);c.brightnessPoints=BrightnessCurve.normalize(j.optJSONArray("brightnessPoints"),c.brightness);
        c.windowZoom=clamp(j.optInt("windowZoom",180),100,3000);c.windowSpeed=clamp(j.optInt("windowSpeed",20),1,100);c.windowMotion=j.optBoolean("windowMotion",true);
        c.chargeEnabled=j.optBoolean("chargeEnabled",false);c.chargeAlways=j.optBoolean("chargeAlways",false);c.chargeGradient=j.optBoolean("chargeGradient",false);c.chargeRingColorSecond=color(j.optString("chargeRingColorSecond"),c.chargeRingColorSecond);c.chargeAccentColor=color(j.optString("chargeAccentColor"),c.chargeAccentColor);c.chargeCircle=j.optBoolean("chargeCircle",false);c.chargeAnimated=j.optBoolean("chargeAnimated",false);c.chargeBold=j.optBoolean("chargeBold",false);
        c.chargeColor=color(j.optString("chargeColor"),c.chargeColor);c.chargeSymbolColor=color(j.optString("chargeSymbolColor"),c.chargeSymbolColor);c.chargeRingColor=color(j.optString("chargeRingColor"),c.chargeRingColor);
        c.chargeFont=j.optString("chargeFont",c.chargeFont);c.chargeFontFile=safePath(j.optString("chargeFontFile"));
        c.chargeSize=clamp(j.optInt("chargeSize",18),8,50);c.chargeX=clamp(j.optInt("chargeX",50),0,100);c.chargeY=clamp(j.optInt("chargeY",85),0,100);
        c.chargeRingWidth=clamp(j.optInt("chargeRingWidth",3),1,12);c.chargeAnimationSeconds=clamp(j.optInt("chargeAnimationSeconds",10),2,60);
        try {new SimpleDateFormat(c.datePattern,I18n.locale());} catch(IllegalArgumentException e) {c.datePattern="dd.MM.yyyy";}
        return c;
    }
    JSONObject json() {
        JSONObject j=new JSONObject();
        try {
            j.put("wfzWidgets",wfzWidgets).put("wfzStepGoal",wfzStepGoal).put("wfzWidgetColor",wfzWidgetColor).put("wfz",wfz).put("wfzSeconds",wfzSeconds).put("wfzCrop",wfzCrop).put("wfzHealth",wfzHealth).put("wfzSize",wfzSize).put("schema",1).put("name",name).put("mode",mode).put("datePattern",datePattern).put("font",font).put("imageFit",imageFit)
             .put("timeColor",timeColor).put("dateColor",dateColor).put("backgroundColor",backgroundColor).put("image",image).put("fontFile",fontFile).put("design",design)
             .put("twentyFour",twentyFour).put("seconds",seconds).put("leadingZero",leadingZero).put("showClock",showClock).put("showDate",showDate).put("bold",bold)
             .put("shifting",shifting).put("shiftBackground",shiftBackground).put("timeSize",timeSize).put("dateSize",dateSize)
             .put("timeX",timeX).put("timeY",timeY).put("dateX",dateX).put("dateY",dateY).put("shiftRange",shiftRange)
             .put("shiftInterval",shiftInterval).put("dim",dim).put("brightness",brightness)
             .put("brightnessCurveEnabled",brightnessCurveEnabled).put("brightnessCurveMinutes",brightnessCurveMinutes).put("brightnessPoints",BrightnessCurve.normalize(brightnessPoints,brightness))
             .put("windowZoom",windowZoom).put("windowSpeed",windowSpeed).put("windowMotion",windowMotion)
             .put("chargeAlways",chargeAlways).put("chargeGradient",chargeGradient).put("chargeRingColorSecond",chargeRingColorSecond).put("chargeAccentColor",chargeAccentColor).put("chargeEnabled",chargeEnabled).put("chargeCircle",chargeCircle).put("chargeAnimated",chargeAnimated).put("chargeBold",chargeBold)
             .put("chargeColor",chargeColor).put("chargeSymbolColor",chargeSymbolColor).put("chargeRingColor",chargeRingColor).put("chargeFont",chargeFont).put("chargeFontFile",chargeFontFile)
             .put("chargeSize",chargeSize).put("chargeX",chargeX).put("chargeY",chargeY).put("chargeRingWidth",chargeRingWidth).put("chargeAnimationSeconds",chargeAnimationSeconds);
        } catch(org.json.JSONException e) {throw new IllegalStateException(e);}
        return j;
    }
    String time(Date d) {
        String h=twentyFour?(leadingZero?"HH":"H"):(leadingZero?"hh":"h");
        return new SimpleDateFormat(h+":mm"+(seconds?":ss":"")+(twentyFour?"":" a"),I18n.locale()).format(d);
    }
    String date(Date d) {return new SimpleDateFormat(datePattern,I18n.locale()).format(d);}
    static int clamp(int n,int lo,int hi) {return Math.max(lo,Math.min(hi,n));}
    static String color(String s,String fallback) {try {int v=Color.parseColor(s); return String.format(Locale.ROOT,"#%06X",v&0xffffff);} catch(Exception e) {return fallback;}}
    static String safePath(String s) {return s.matches("[A-Za-z0-9_./-]+")&&!s.startsWith("/")&&!s.contains("..")?s:"";}
}
