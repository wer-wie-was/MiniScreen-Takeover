package com.miniscreen.takeover;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Bounded imports; never extracts PBW resources into executable Android paths. */
final class PebbleFiles {
    static final String[] BUNDLED={"gabbro","emery","flint"};
    static final String[] RUNTIME_FILES={"qemu-system-arm.js","qemu-system-arm.wasm","qemu-system-arm.worker.js","qemu_micro_flash.bin","qemu_spi_flash.bin"};
    static final String[] PLATFORMS={"aplite","basalt","chalk","diorite","emery","flint","gabbro"};
    static File root(Context c){File f=new File(c.getFilesDir(),"pebble");f.mkdirs();return f;}
    static File runtime(Context c){return new File(root(c),"runtime");}
    static File watchface(Context c){return new File(root(c),"watchface.pbw");}
    static void copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("Import exceeds size limit");out.write(b,0,n);}}
    static JSONObject inspect(File f)throws Exception{
        try(ZipFile z=new ZipFile(f)){long total=0;int count=0;Enumeration<? extends ZipEntry> entries=z.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();if(++count>2000||entry.getSize()<0||entry.getSize()>32L*1024*1024||(total+=entry.getSize())>64L*1024*1024)throw new IOException("PBW resource limit exceeded");}
            ZipEntry info=z.getEntry("appinfo.json");if(info==null)throw new IOException("Missing appinfo.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=z.getInputStream(info)){copy(in,bytes,256*1024);}JSONObject meta=new JSONObject(bytes.toString("UTF-8"));
            JSONArray boards=new JSONArray();for(String platform:PLATFORMS)if(z.getEntry(platform+"/pebble-app.bin")!=null)boards.put(platform);
            if(z.getEntry("pebble-app.bin")!=null&&boards.length()==0)boards.put("aplite");if(boards.length()==0)throw new IOException("No Pebble app binary");meta.put("platforms",boards);return meta;}
    }
    static void importPbw(Context c,Uri uri)throws Exception{
        File tmp=new File(root(c),"incoming.pbw");try{try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("Cannot open file");copy(in,out,32*1024*1024);}JSONObject meta=inspect(tmp);
            File target=watchface(c);java.nio.file.Files.move(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            JSONArray boards=meta.getJSONArray("platforms");String board=choosePlatform(c,boards);
            TakeoverControl.prefs(c).edit().putString("pebble_name",meta.optString("shortName",meta.optString("longName","Pebble"))).putString("pebble_platform",board).putLong("pebble_revision",System.currentTimeMillis()).apply();
        }finally{tmp.delete();}
    }
    static void importRuntime(Context c,Uri uri)throws Exception{
        File stage=new File(root(c),"runtime-stage"),dest=runtime(c),old=new File(root(c),"runtime-old");remove(stage);stage.mkdirs();long total=0;int count=0;boolean done=false;
        try{try(InputStream input=c.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(input)){ZipEntry e;while((e=zip.getNextEntry())!=null){if(++count>256)throw new IOException("Too many runtime files");String name=e.getName();if(name.startsWith("/")||name.contains("\\"))throw new IOException("Invalid ZIP path");File file=new File(stage,name).getCanonicalFile();if(!file.getPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("Invalid ZIP path");if(e.isDirectory()){file.mkdirs();continue;}if(!allowed(name))throw new IOException("Unexpected runtime file: "+name);file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){byte[] b=new byte[32768];int n;while((n=zip.read(b))!=-1){total+=n;if(total>256L*1024*1024)throw new IOException("Runtime too large");out.write(b,0,n);}}}}
            if(!new File(stage,"runtime.json").isFile())throw new IOException("Missing runtime.json");JSONObject manifest=new JSONObject(ProfileStore.readText(new File(stage,"runtime.json"),64*1024));if(manifest.optInt("schema")!=1)throw new IOException("Unsupported runtime schema");
            JSONArray platforms=manifest.getJSONArray("platforms");if(platforms.length()==0)throw new IOException("No platforms in runtime");for(int i=0;i<platforms.length();i++){String platform=platforms.getString(i);if(!Arrays.asList(PLATFORMS).contains(platform))throw new IOException("Unknown platform");for(String name:new String[]{"qemu-system-arm.js","qemu-system-arm.wasm","qemu_micro_flash.bin","qemu_spi_flash.bin"})if(!new File(stage,platform+"/"+name).isFile())throw new IOException("Missing "+platform+"/"+name);}
            remove(old);if(dest.exists()&&!dest.renameTo(old))throw new IOException("Cannot replace runtime");if(!stage.renameTo(dest)){old.renameTo(dest);throw new IOException("Cannot install runtime");}done=true;remove(old);TakeoverControl.prefs(c).edit().putLong("pebble_revision",System.currentTimeMillis()).apply();
        }finally{if(!done)remove(stage);}
    }
    static String stateKey(Context c,String platform){
        File dir=new File(runtime(c),platform);
        if(importedReady(c,platform))return platform+"-custom-"+new File(dir,"qemu_micro_flash.bin").lastModified()+"-"+new File(dir,"qemu_spi_flash.bin").lastModified();
        return platform+"-bundled-v1";
    }
    private static boolean allowed(String n){if(n.equals("runtime.json")||n.equals("LICENSES.txt"))return true;String[] parts=n.split("/");return parts.length==2&&Arrays.asList(PLATFORMS).contains(parts[0])&&Arrays.asList("qemu-system-arm.js","qemu-system-arm.wasm","qemu-system-arm.worker.js","qemu_micro_flash.bin","qemu_spi_flash.bin").contains(parts[1]);}
    static boolean importedReady(Context c,String platform){
        if(!Arrays.asList(PLATFORMS).contains(platform))return false;
        for(String name:new String[]{"qemu-system-arm.js","qemu-system-arm.wasm","qemu_micro_flash.bin","qemu_spi_flash.bin"})if(!new File(runtime(c),platform+"/"+name).isFile())return false;
        return true;
    }
    static boolean ready(Context c,String platform){return importedReady(c,platform)||Arrays.asList(BUNDLED).contains(platform);}
    static String choosePlatform(Context c,JSONArray boards)throws JSONException{
        // Prefer a runnable round target, then a runnable rectangular target. Never substitute a different PBW binary.
        for(String preferred:new String[]{"gabbro","chalk","emery","basalt","flint","diorite","aplite"})
            for(int i=0;i<boards.length();i++)if(preferred.equals(boards.getString(i))&&ready(c,preferred))return preferred;
        return boards.getString(0);
    }
    static InputStream openRuntime(Context c,String platform,String name)throws IOException{
        if(!Arrays.asList(PLATFORMS).contains(platform)||!Arrays.asList(RUNTIME_FILES).contains(name))throw new FileNotFoundException("Unknown runtime resource");
        // A complete user runtime overrides the whole platform; never mix its firmware with bundled engine files.
        if(importedReady(c,platform))return new FileInputStream(new File(runtime(c),platform+"/"+name));
        if(!Arrays.asList(BUNDLED).contains(platform))throw new FileNotFoundException("Platform is not bundled");
        return c.getAssets().open("pebble/bundled/"+(name.startsWith("qemu-system-arm.")?name:platform+"/"+name));
    }
    static void remove(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)remove(child);}f.delete();}
}
