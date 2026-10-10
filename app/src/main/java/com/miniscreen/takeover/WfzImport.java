package com.miniscreen.takeover;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.*;
import org.json.*;

final class WfzImport {
    static final int MAX_ENTRIES=2000;
    static String importFile(Context c,ProfileStore store,String id,Uri uri)throws Exception {
        File folder=new File(store.dir(id),"wfz-"+UUID.randomUUID());folder.mkdirs();
        try {
            InputStream input=c.getContentResolver().openInputStream(uri);if(input==null)throw new IOException("Cannot open WFZ");
            ProfileStore.extract(input,folder,ProfileStore.MAX_BYTES,MAX_ENTRIES);
            Set<File> files=new LinkedHashSet<>();ProfileStore.collectFiles(folder,files);List<File> roots=new ArrayList<>();
            for(File f:files)if(f.getName().equals("watchface.xml"))roots.add(f.getParentFile());
            if(roots.size()!=1)throw new IOException(I18n.get(R.string.wfz_invalid));
            File root=roots.get(0);WfzScene scene=WfzScene.load(root);
            JSONObject info=new JSONObject().put("rendererVersion",40).put("width",scene.width).put("height",scene.height).put("components",scene.components).put("warnings",new JSONArray(scene.warnings));
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(root,"takeover-wfz.json")),"UTF-8")){out.write(info.toString());}
            File profileRoot=store.dir(id).getCanonicalFile();
            File watchfaceRoot=root.getCanonicalFile();
            if(!watchfaceRoot.getPath().startsWith(profileRoot.getPath()+File.separator))throw new IOException("WFZ folder outside profile");
            String result=profileRoot.toPath().relativize(watchfaceRoot.toPath()).toString().replace(File.separatorChar,'/');
            if(Config.safePath(result).isEmpty())throw new IOException("Unsupported archive folder name");return result;
        } catch(Exception e){ProfileStore.deleteTree(folder);throw e;}
    }
    static String report(File root){try{
        JSONObject info=new JSONObject(ProfileStore.readText(new File(root,"takeover-wfz.json"),65536));JSONArray warnings=info.getJSONArray("warnings");
        StringBuilder text=new StringBuilder(I18n.get(warnings.length()==0?R.string.wfz_supported:R.string.wfz_partial));
        if(info.optInt("rendererVersion",0)<40)text.append("\nImport report from an older renderer. Re-import this WFZ for the 0.6.5 compatibility report.");
        text.append("\nWFZ · ").append(info.getInt("width")).append(" × ").append(info.getInt("height"));
        if(info.optBoolean("components"))text.append(" · Stratos 3");
        for(int i=0;i<warnings.length();i++)text.append("\n• ").append(warnings.getString(i));return text.toString();
    }catch(Exception e){return I18n.get(R.string.wfz_no_report);}}
}
