package com.miniscreen.takeover;

import android.content.*;
import java.util.*;

/** Device settings; notification data and app choices never enter portable design profiles. */
final class TickerSettings {
    final SharedPreferences p;
    TickerSettings(Context c){p=TakeoverControl.prefs(c);}
    boolean enabled(){return p.getBoolean("ticker_enabled",false);}
    boolean bool(String key,boolean value){return p.getBoolean("ticker_"+key,value);}
    int number(String key,int value,int min,int max){return Config.clamp(p.getInt("ticker_"+key,value),min,max);}
    String color(String key,String value){return Config.color(p.getString("ticker_"+key,value),value);}
    Set<String> apps(){return new HashSet<>(p.getStringSet("ticker_apps",Collections.emptySet()));}
    int level(String app,boolean locked){String key="ticker_privacy_"+(locked?"locked_":"unlocked_")+app;return Config.clamp(p.getInt(key,p.getInt("ticker_privacy_"+(locked?"locked":"unlocked"),1)),0,3);}
    int mode(){return number("reminder",0,0,3);}
    int duration(){return number("duration",10,3,60);}
    int interval(){return number("interval",60,10,3600);}
    int pause(){return number("pause",3,0,60);}
    org.json.JSONObject json(){
        org.json.JSONObject j=new org.json.JSONObject();try{
            j.put("enabled",enabled()).put("fontSize",number("size",18,8,50)).put("font",p.getString("ticker_font","sans"))
             .put("bold",bool("bold",false)).put("color",color("color","#FFFFFF")).put("background",color("background","#000000"))
             .put("opacity",number("opacity",75,0,100)).put("width",number("width",90,20,100)).put("x",number("x",50,0,100)).put("y",number("y",82,0,100))
             .put("heightAuto",bool("height_auto",true)).put("height",number("height",100,20,300))
             .put("scroll",bool("scroll",true)).put("speed",number("speed",28,5,1080)).put("lines",number("lines",2,1,5))
             .put("icon",bool("icon",true)).put("iconSize",number("icon_size",20,8,50)).put("duration",duration()).put("clockMode",number("clock",0,0,2));
        }catch(org.json.JSONException ignored){}return j;
    }
    boolean html(){return bool("html",false);}
}
