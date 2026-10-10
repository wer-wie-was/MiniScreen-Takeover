package com.miniscreen.takeover;

import java.io.*;
import java.util.*;
import org.w3c.dom.*;

/** Shared XML widget descriptions. Choices never mutate cached scenes. */
final class WfzWidgets {
    static final class Choice {
        final int data,model;final String type;
        Choice(int d,int m){data=d;model=m;type=type(d);}
        String token(){return data+":"+model;}
        String label(){return name(type)+" · "+I18n.get(R.string.wfz_variant)+" "+model;}
    }
    static final class Spec {
        String key;int x,y,width,height;boolean explicitWidth,explicitHeight;
        Choice initial;final List<Choice> choices=new ArrayList<>();
        Choice selected(Config c){String token=c.wfzWidgets.optString(key,initial.token());for(Choice v:choices)if(v.token().equals(token))return v;return initial;}
        void add(Choice c){for(Choice v:choices)if(v.token().equals(c.token()))return;if(choices.size()<64)choices.add(c);}
    }
    static String type(int n){switch(n){case 1:return "steps";case 2:return "distance";case 3:return "workoutdistance";case 4:return "calories";case 5:return "heart";case 6:return "date";case 8:return "weather";case 10:return "battery";default:return "unsupported-data-"+n;}}
    static boolean health(String type){return Arrays.asList("steps","distance","calories","heart").contains(type);}
    static String name(String type){switch(type){
        case "steps":return I18n.get(R.string.pebble_steps);
        case "heart":return I18n.get(R.string.pebble_heart);
        case "distance":return I18n.get(R.string.wfz_distance);
        case "workoutdistance":return I18n.get(R.string.wfz_workout_distance);
        case "calories":return I18n.get(R.string.wfz_calories);
        case "weather":return I18n.get(R.string.wfz_weather);
        case "date":return I18n.get(R.string.wfz_date);
        case "battery":return I18n.get(R.string.wfz_battery);
        default:return type;
    }}
    static Spec spec(WfzScene scene,Element e,String key)throws Exception {
        Spec s=new Spec();s.key=key;s.x=WfzScene.integer(e,"x",0);s.y=WfzScene.integer(e,"y",0);
        s.initial=new Choice(WfzScene.integer(e,"dataType",-1),WfzScene.integer(e,"model",0));s.add(s.initial);
        s.explicitWidth=e.hasAttribute("width");s.explicitHeight=e.hasAttribute("height");
        s.width=WfzScene.integer(e,"width",width(s.initial));s.height=WfzScene.integer(e,"height",height(s.initial));
        if(s.width<=0||s.height<=0||s.width>1024||s.height>1024||Math.abs(s.x)>2048||Math.abs(s.y)>2048)throw new IOException("Invalid widget rectangle: "+key);
        String list=e.getAttribute("configList");
        if(!list.isEmpty()){
            Element root=WfzScene.xml(scene.path(list));NodeList items=root.getElementsByTagName("WatchFaceItem");
            if(items.getLength()>64)throw new IOException("Too many widget alternatives");
            for(int i=0;i<items.getLength();i++){Element item=(Element)items.item(i);if(item.getAttribute("type").equalsIgnoreCase("datawidget"))s.add(new Choice(WfzScene.integer(item,"dataType",-1),WfzScene.integer(item,"model",0)));}
        }
        for(Choice c:s.choices){
            if(c.type.startsWith("unsupported"))scene.warnings.add(key+": unsupported dataType="+c.data+" (placeholder)");
            if(c.type.equals("weather"))scene.warnings.add(key+": weather source unavailable; --");
            if(c.type.equals("workoutdistance"))scene.warnings.add(key+": workout distance unavailable; -- (daily distance is a separate choice)");
            if(!Arrays.asList(0,1,2,5,6,7).contains(c.model))scene.warnings.add(key+": model="+c.model+" uses a generic native layout");
        }
        return s;
    }
    static int width(Choice c){if(!c.type.equals("date"))return c.model==1||c.model==2?88:80;return c.model==1?74:c.model==2?70:c.model==5?32:c.model==6||c.model==7?116:80;}
    static int height(Choice c){if(c.type.equals("date"))return c.model==5?30:c.model==0?80:24;return c.model==1||c.model==2?28:80;}
    static List<Spec> read(File folder)throws Exception {
        // XML-only scan for settings; image decoding stays on the scene loader.
        WfzScene scene=WfzScene.descriptions(folder);Element root=WfzScene.xml(new File(folder,"watchface.xml"));
        List<Spec> result=new ArrayList<>();NodeList list=root.getElementsByTagName("WatchFaceItem");
        for(int i=0;i<list.getLength();i++){Element e=(Element)list.item(i);if(e.getAttribute("type").equalsIgnoreCase("datawidget")){if(result.size()>=256)throw new IOException("Too many widgets");result.add(spec(scene,e,"widget-"+result.size()));}}
        return result;
    }
}
