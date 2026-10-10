package com.miniscreen.takeover;

import android.graphics.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Declarative WFZ subset. Never loads executable code or manufacturer libraries. */
final class WfzScene {
    static final Map<String,WeakReference<WfzScene>> CACHE=new LinkedHashMap<>();
    final File root;
    final List<Part> parts=new ArrayList<>();
    final Set<String> warnings=new LinkedHashSet<>();
    final Map<String,Bitmap> images=new HashMap<>();
    final Map<String,Map<String,Bitmap>> fonts=new HashMap<>();
    int width=320,height=320;long pixels;boolean seconds,health,components;int widgetCount;
    static final class Part {
        String type,font="",source="";float x,y,w,h;int align=0,space,digit=-1;Bitmap image,hour,minute,second;Bitmap[] frames;WfzWidgets.Spec widget;Bitmap mask;
    }
    private WfzScene(File root){this.root=root;}
    static WfzScene descriptions(File root){return new WfzScene(root);}
    static synchronized WfzScene load(File root)throws Exception {
        String key=root.getCanonicalPath();WeakReference<WfzScene> ref=CACHE.get(key);WfzScene old=ref==null?null:ref.get();if(old!=null)return old;
        WfzScene scene=new WfzScene(root);Element xml=xml(new File(root,"watchface.xml"));
        if(!xml.getTagName().equalsIgnoreCase("WatchFace"))throw new IOException("Invalid WatchFace root");
        scene.width=integer(xml,"width",320);scene.height=integer(xml,"height",scene.width);
        scene.parse(xml,0,"");
        if(scene.health)scene.warnings.add("health: optional Health Connect data; -- when unavailable");
        if(scene.width<100||scene.height<100||scene.width>1024||scene.height>1024)throw new IOException("Unsupported canvas dimensions");
        if(scene.parts.isEmpty())throw new IOException("No supported WFZ elements");
        if(CACHE.size()>=4)CACHE.clear();CACHE.put(key,new WeakReference<>(scene));return scene;
    }
    static Element xml(File file)throws Exception {
        if(file.length()>512*1024)throw new IOException("XML exceeds 512 KiB");
        String source=ProfileStore.readText(file,512*1024);
        if(source.startsWith("\uFEFF"))source=source.substring(1);
        if(source.indexOf('\0')>=0||java.util.regex.Pattern.compile("<!\\s*(DOCTYPE|ENTITY)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(source).find())throw new IOException("DTD/entities are not supported");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);
        DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((pub,sys)->{throw new org.xml.sax.SAXException("External entities disabled");});
        return b.parse(new org.xml.sax.InputSource(new StringReader(source))).getDocumentElement();
    }
    File path(String value)throws IOException {
        String rel=value.startsWith("@wfz/")?value.substring(5):value;
        if(rel.isEmpty()||rel.startsWith("/")||rel.contains("\\")||rel.contains(":"))throw new IOException("Invalid WFZ resource path");
        File file=new File(root,rel).getCanonicalFile();if(!file.getPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("WFZ resource outside archive");return file;
    }
    Bitmap bitmap(String name)throws Exception {
        if(images.containsKey(name))return images.get(name);File file=path(name);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
        long area=(long)bounds.outWidth*bounds.outHeight;
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>2048||bounds.outHeight>2048||pixels+area>6*1024*1024)throw new IOException("Missing, invalid or oversized image: "+name);
        Bitmap image=BitmapFactory.decodeFile(file.getPath());if(image==null)throw new IOException("Invalid image: "+name);pixels+=area;images.put(name,image);return image;
    }
    String resource(Element e){String s=e.getAttribute("config");return s.isEmpty()?e.getAttribute("src"):s;}
    Map<String,Bitmap> font(String folder)throws Exception {
        if(fonts.containsKey(folder))return fonts.get(folder);Map<String,Bitmap> map=new LinkedHashMap<>();
        Element root=xml(path(folder.endsWith(".xml")?folder:folder+"/font.xml"));NodeList list=root.getElementsByTagName("WatchFaceItem");
        if(list.getLength()>256)throw new IOException("Too many font glyphs");
        for(int i=0;i<list.getLength();i++){Element e=(Element)list.item(i);String ch=e.getAttribute("charset");if(!ch.isEmpty())map.put(ch,bitmap(resource(e)));}
        if(map.isEmpty())throw new IOException("Empty bitmap font");fonts.put(folder,map);return map;
    }
    static int integer(Element e,String name,int def){try{return Integer.parseInt(e.getAttribute(name));}catch(Exception ex){return def;}}
    void parse(Element parent,int depth,String inherited)throws Exception {
        if(depth>16)throw new IOException("WFZ nesting too deep");NodeList list=parent.getChildNodes();
        for(int i=0;i<list.getLength();i++){
            if(!(list.item(i) instanceof Element))continue;Element e=(Element)list.item(i);String type=e.getAttribute("type").toLowerCase(Locale.ROOT);
            if(parts.size()>256)throw new IOException("Too many WFZ elements");
            boolean component=e.getTagName().equalsIgnoreCase("WatchFaceComponent");components|=component;
            if(component&&(type.equals("timedigital")||type.equals("timedis")||type.equals("date")||type.equals("gtrwidget"))){
                String data=type.equals("gtrwidget")?dataType(integer(e,"dataType",-1)):"";
                if(hasElements(e)){parse(e,depth+1,data);continue;}
                if(type.equals("gtrwidget"))type=data;
            }
            if(type.equals("sec")&&e.hasAttribute("high.x")&&e.hasAttribute("low.x")){
                String folder=e.getAttribute("font");Map<String,Bitmap> glyphs=font(folder);
                for(int digit=0;digit<2;digit++){
                    Part split=new Part();split.type="second";split.font=folder;split.digit=digit;
                    String prefix=digit==0?"high":"low";split.x=integer(e,prefix+".x",0);split.y=integer(e,prefix+".y",0);
                    for(Bitmap glyph:glyphs.values()){split.w=Math.max(split.w,glyph.getWidth());split.h=Math.max(split.h,glyph.getHeight());}
                    parts.add(split);
                }seconds=true;continue;
            }
            if(type.equals("datawidget")){
                Part p=new Part();p.type="widget";p.widget=WfzWidgets.spec(this,e,"widget-"+(widgetCount++));
                p.x=p.widget.x;p.y=p.widget.y;p.w=p.widget.width;p.h=p.widget.height;
                if(!e.getAttribute("mask").isEmpty())try{p.mask=bitmap(e.getAttribute("mask"));}catch(Exception ex){warnings.add("missing widget mask: "+e.getAttribute("mask"));}
                for(WfzWidgets.Choice choice:p.widget.choices)health|=WfzWidgets.health(choice.type);
                warnings.add("native widget replacement: "+p.widget.key+" (original Amazfit system graphics unavailable)");
                parts.add(p);continue;
            }
            if(type.equals("statusbar")){
                Part p=new Part();p.type="statusbar";p.x=integer(e,"x",width/2);p.y=integer(e,"y",height-34);p.w=48;p.h=16;parts.add(p);warnings.add("statusbar: phone battery; watch connectivity indicators unavailable");continue;
            }
            Part p=new Part();p.type=type;p.source=type;p.x=integer(e,"x",integer(e,"x0",0));p.y=integer(e,"y",integer(e,"y0",0));
            p.w=integer(e,"width",integer(e,"x1",(int)p.x)-(int)p.x);p.h=integer(e,"height",integer(e,"y1",(int)p.y)-(int)p.y);
            p.align=integer(e,"align",0);p.space=integer(e,"space",0);p.font=e.getAttribute("font");
            if(type.equals("batteryimage")||type.equals("month_image")||type.equals("week")){
                int count=type.equals("batteryimage")?integer(e,"count",0):type.equals("week")?7:12;
                if(count<1||count>256)throw new IOException("Invalid WFZ image sequence count");
                String folder=type.equals("batteryimage")?e.getAttribute("bitmapArray"):type.equals("week")?e.getAttribute("font"):resource(e);
                if(type.equals("week")){
                    String language=I18n.locale().getLanguage().toUpperCase(Locale.ROOT);
                    if(path(folder+"/"+language).isDirectory())folder+="/"+language;
                    else if(path(folder+"/EN").isDirectory()){folder+="/EN";warnings.add("weekday images: English fallback");}
                }
                p.frames=new Bitmap[count];
                for(int index=0;index<count;index++){
                    String candidate=folder+"/"+String.format(Locale.ROOT,"%02d",index)+".png";
                    if(!path(candidate).isFile())candidate=folder+"/"+index+".png";
                    p.frames[index]=bitmap(candidate);
                }
                if(p.w<=0)p.w=p.frames[0].getWidth();if(p.h<=0)p.h=p.frames[0].getHeight();
                if(type.equals("batteryimage")&&integer(e,"NewDisplayStyle",0)!=1)warnings.add("battery image sequence: proportional level mapping");
            }else if(type.equals("am")||type.equals("pm")){
                p.image=bitmap(resource(e));if(p.w<=0)p.w=p.image.getWidth();if(p.h<=0)p.h=p.image.getHeight();
            }else if(type.equals("background")||type.equals("image")||type.equals("addtiveimage")){
                if(type.equals("addtiveimage")){warnings.add("addtiveimage");continue;}
                String name=resource(e);if(name.isEmpty()){warnings.add(type);continue;}p.image=bitmap(name);
                if(type.equals("background")){width=p.image.getWidth();height=p.image.getHeight();p.w=width;p.h=height;}
                else {if(p.w<=0)p.w=p.image.getWidth();if(p.h<=0)p.h=p.image.getHeight();}
            }else if(type.equals("timehand")){
                String folder=resource(e);p.hour=bitmap(folder+"/hour.png");
                // The XML describes the hand canvas, independently of PNG resolution.
                p.w=integer(e,"width",p.hour.getWidth());p.h=integer(e,"height",p.hour.getHeight());
                if(p.w<=0||p.h<=0)throw new IOException("Invalid WFZ hand dimensions");
                p.minute=bitmap(folder+(path(folder+"/minute.png").isFile()?"/minute.png":"/minutes.png"));
                if(path(folder+"/seconds.png").isFile()){p.second=bitmap(folder+"/seconds.png");seconds=true;}
                if(p.x==0&&p.y==0){p.x=width/2f;p.y=height/2f;}
            }else {
                if((type.equals("text")||type.equals("number")||type.equals("level"))&&!inherited.isEmpty())p.type=inherited;
                if(type.equals("hour_text")||type.equals("hour"))p.type="hour";
                if(type.equals("minute_text")||type.equals("minute"))p.type="minute";
                if(type.equals("second_text")||type.equals("second")){p.type="second";seconds=true;}
                if(type.equals("day_text"))p.type="day";
                if(type.equals("month_text"))p.type="month";
                if(type.equals("week_text"))p.type="weekday";
                if(!Arrays.asList("timedigital","hour","minute","second","day","month","year","weekday","date","steps","distance","workoutdistance","calories","heart","battery","weather").contains(p.type)){
                    warnings.add(type.isEmpty()?e.getTagName():type+(e.hasAttribute("dataType")?" dataType="+e.getAttribute("dataType"):""));continue;
                }
                health|=WfzWidgets.health(p.type);
                if(!p.font.isEmpty()){try{font(p.font);}catch(Exception ex){warnings.add("font: "+p.font);p.font="";}}
                else warnings.add("system font: "+p.type);
                if(p.type.equals("month")||p.type.equals("weekday"))warnings.add("numeric/localized calendar: "+p.type);
                if(p.w<=0)p.w=width-p.x;if(p.h<=0)p.h=24;
                if(!Arrays.asList(0,66,68,72).contains(p.align))warnings.add("alignment: "+p.align);
            }
            if(p.w>2048||p.h>2048||Math.abs(p.x)>2048||Math.abs(p.y)>2048)throw new IOException("Invalid WFZ coordinates");
            Set<String> allowed=new HashSet<>(Arrays.asList("type","x","y","x0","y0","x1","y1","width","height","font","config","src","align","space","id","dataType","model","configList","bitmapArray","count","NewDisplayStyle"));
            NamedNodeMap attrs=e.getAttributes();for(int a=0;a<attrs.getLength();a++)if(!allowed.contains(attrs.item(a).getNodeName()))warnings.add(p.type+" @"+attrs.item(a).getNodeName());
            if(hasElements(e))warnings.add("nested elements: "+p.type);
            parts.add(p);
        }
    }
    static boolean hasElements(Element e){NodeList n=e.getChildNodes();for(int i=0;i<n.getLength();i++)if(n.item(i) instanceof Element)return true;return false;}
    static String dataType(int n){return WfzWidgets.type(n);}
    String report(){return (warnings.isEmpty()?I18n.get(R.string.wfz_supported):I18n.get(R.string.wfz_partial))+"\n"+width+" × "+height+" · WFZ"+(components?" · Stratos 3 components":"")+"\n"+String.join("\n",warnings);}
}
