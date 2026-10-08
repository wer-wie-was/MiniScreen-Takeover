package com.miniscreen.takeover;
import org.json.*;
import java.util.*;
/** Minutes of local day, ordered unique points, cyclic midnight endpoint. */
final class BrightnessCurve {
 static JSONArray normalize(JSONArray input,int fallback){TreeMap<Integer,Integer> values=new TreeMap<>();if(input!=null)for(int i=0;i<Math.min(input.length(),100);i++){JSONArray p=input.optJSONArray(i);if(p!=null&&p.length()==2)values.put(Config.clamp(p.optInt(0),0,1440),Config.clamp(p.optInt(1,fallback),0,100));}int midnight=values.containsKey(0)?values.get(0):values.containsKey(1440)?values.get(1440):fallback;values.put(0,midnight);values.put(1440,midnight);JSONArray result=new JSONArray();for(Map.Entry<Integer,Integer> p:values.entrySet())result.put(new JSONArray().put(p.getKey()).put(p.getValue()));return result;}
 static int value(Config c,Calendar now){if(!c.brightnessCurveEnabled)return c.brightness;double minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE)+now.get(Calendar.SECOND)/60.0;JSONArray p=normalize(c.brightnessPoints,c.brightness);for(int i=1;i<p.length();i++){JSONArray a=p.optJSONArray(i-1),b=p.optJSONArray(i);if(minute<=b.optInt(0)){double fraction=(minute-a.optInt(0))/(b.optInt(0)-a.optInt(0));return Config.clamp((int)Math.round(a.optInt(1)+(b.optInt(1)-a.optInt(1))*fraction),0,100);}}return c.brightness;}
}
