package com.miniscreen.takeover;
import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.util.AtomicFile;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import org.json.*;

final class WfzImport {
    static final int MAX_ENTRIES=2000,RENDERER_VERSION=55;
    static String importFile(Context c,ProfileStore store,String id,Uri uri)throws Exception {
        File folder=new File(store.dir(id),"wfz-"+UUID.randomUUID());folder.mkdirs();
        try {
            InputStream input=c.getContentResolver().openInputStream(uri);if(input==null)throw new IOException("Cannot open WFZ");
            ProfileStore.extract(input,folder,ProfileStore.MAX_BYTES,MAX_ENTRIES);
            Set<File> files=new LinkedHashSet<>();ProfileStore.collectFiles(folder,files);List<File> roots=new ArrayList<>();
            for(File f:files)if(f.getName().equals("watchface.xml"))roots.add(f.getParentFile());
            if(roots.size()!=1)throw new IOException(I18n.get(R.string.wfz_invalid));
            File root=roots.get(0);writeInfo(root,displayName(c,uri));
            File profileRoot=store.dir(id).getCanonicalFile();
            File watchfaceRoot=root.getCanonicalFile();
            if(!watchfaceRoot.getPath().startsWith(profileRoot.getPath()+File.separator))throw new IOException("WFZ folder outside profile");
            String result=profileRoot.toPath().relativize(watchfaceRoot.toPath()).toString().replace(File.separatorChar,'/');
            if(Config.safePath(result).isEmpty())throw new IOException("Unsupported archive folder name");return result;
        } catch(Exception e){ProfileStore.deleteTree(folder);throw e;}
    }
    static void writeInfo(File root,String fallback)throws Exception {writeInfo(root,WfzScene.load(root),fallback);}
    private static synchronized void writeInfo(File root,WfzScene scene,String fallback)throws Exception {
        JSONObject info=new JSONObject().put("rendererVersion",RENDERER_VERSION).put("title",faceTitle(root,fallback)).put("width",scene.width).put("height",scene.height).put("components",scene.components).put("warnings",new JSONArray(scene.warnings));
        AtomicFile atomic=new AtomicFile(new File(root,"takeover-wfz.json"));FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(info.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    static void refreshInfo(File root,WfzScene scene)throws Exception {synchronized(WfzGallery.class){
        String title="";try{JSONObject old=info(root);if(old.optInt("rendererVersion",0)>=RENDERER_VERSION)return;title=old.optString("title","");}catch(Exception ignored){}
        writeInfo(root,scene,title);
    }}
    static String displayName(Context c,Uri uri){
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())return cleanTitle(cursor.getString(0).replaceFirst("(?i)\\.wfz$",""));
        }catch(Exception ignored){}
        return "";
    }
    private static String cleanTitle(String title){
        if(title==null)return "";
        String value=title.replaceAll("\\s+"," ").trim();
        return value.length()>80?value.substring(0,80):value;
    }
    private static String faceTitle(File root,String fallback){
        try{
            org.w3c.dom.NodeList titles=WfzScene.xml(new File(root,"description.xml")).getElementsByTagName("title");
            if(titles.getLength()>0){String title=cleanTitle(titles.item(0).getTextContent());if(!title.isEmpty())return title;}
        }catch(Exception ignored){}
        String title=cleanTitle(fallback);return title.isEmpty()?I18n.get(R.string.wfz_unnamed):title;
    }
    private static JSONObject info(File root)throws Exception{
        return new JSONObject(ProfileStore.readText(new File(root,"takeover-wfz.json"),65536));
    }
    private static String header(File root,JSONObject info)throws Exception{
        String title=cleanTitle(info.optString("title",""));
        if(title.isEmpty())title=faceTitle(root,"");
        return title+" · WFZ · "+info.getInt("width")+" × "+info.getInt("height")+(info.optBoolean("components")?" · Stratos 3":"");
    }
    private static WfzReport userReport(JSONObject info)throws Exception{
        WfzReport report=new WfzReport();JSONArray warnings=info.getJSONArray("warnings");
        for(int i=0;i<warnings.length();i++)report.add(warnings.getString(i));
        return report;
    }
    private static String status(JSONObject info)throws Exception{
        return I18n.get(userReport(info).status());
    }
    static String summary(File root){try{
        JSONObject info=info(root);return header(root,info)+"\n"+status(info);
    }catch(Exception e){return I18n.get(R.string.wfz_no_report);}}
    static String report(File root){try{
        JSONObject info=info(root);WfzReport report=userReport(info);
        StringBuilder text=new StringBuilder(header(root,info)).append("\n").append(status(info));
        if(info.optInt("rendererVersion",0)<RENDERER_VERSION)text.append("\n").append(I18n.get(R.string.wfz_report_old));
        text.append(report.details());return text.toString();
    }catch(Exception e){return I18n.get(R.string.wfz_no_report);}}
}
