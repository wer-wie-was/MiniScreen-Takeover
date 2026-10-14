package com.miniscreen.takeover;

import java.io.*;
import java.util.*;
import org.w3c.dom.*;

/** Archive-declared alternatives; paths retain the same containment checks as scene assets. */
final class WfzVariants {
    static final class Group {
        final Element element;final String key,type;final List<String> options;
        Group(Element element,String key,String type,List<String> options){this.element=element;this.key=key;this.type=type;this.options=options;}
        int selected(Config config){return Math.max(0,Math.min(options.size()-1,config.wfzVariants.optInt(key,0)));}
    }
    static List<Group> read(File root)throws Exception {
        return read(WfzScene.descriptions(root),WfzScene.xml(new File(root,"watchface.xml")));
    }
    static List<Group> read(WfzScene scene,Element root)throws Exception {
        List<Group> result=new ArrayList<>();NodeList nodes=root.getElementsByTagName("*");
        for(int i=0;i<nodes.getLength();i++){
            Element element=(Element)nodes.item(i);String list=element.getAttribute("configList");
            String type=element.getAttribute("type").toLowerCase(Locale.ROOT);
            if(list.isEmpty()||!Arrays.asList("background","timehand").contains(type))continue;
            if(result.size()>=64)throw new IOException("Too many WFZ variant groups");
            Element choices=WfzScene.xml(scene.path(list));NodeList items=choices.getElementsByTagName("WatchFaceItem");List<String> options=new ArrayList<>();
            if(items.getLength()>64)throw new IOException("Too many WFZ variants");
            for(int n=0;n<items.getLength();n++){
                Element item=(Element)items.item(n);if(!item.getAttribute("type").equalsIgnoreCase(type))continue;
                String path=item.getAttribute("config");scene.path(path);options.add(path);
            }
            if(options.isEmpty())throw new IOException("Empty WFZ variant list");
            // Missing selections preserve the archive's declared default, even if it is not first.
            String original=element.getAttribute("config");int index=options.indexOf(original);
            if(index>0){options.remove(index);options.add(0,original);}
            result.add(new Group(element,"variant-"+result.size(),type,options));
        }
        return result;
    }
}
