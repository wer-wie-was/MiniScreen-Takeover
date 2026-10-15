package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.json.JSONObject;

/** Shared, validated watchface assets; profiles keep small content-hash references. */
final class WfzGallery {
    static final class Entry {
        final String id,title;final File folder;final boolean favorite;
        Entry(String id,String title,File folder,boolean favorite){this.id=id;this.title=title;this.folder=folder;this.favorite=favorite;}
    }
    static final class Result {final String id;final boolean duplicate;Result(String id,boolean duplicate){this.id=id;this.duplicate=duplicate;}}
    private static File root(Context c){return new File(c.getFilesDir(),"watchface-gallery");}
    private static File directory(Context c,String id){if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid watchface ID");return new File(new File(root(c),"faces"),id);}
    static File face(Context c,File profile,Config config){return config.wfzGallery.isEmpty()?new File(profile,config.wfz):directory(c,config.wfzGallery);}
    private static JSONObject info(File folder)throws Exception{return new JSONObject(ProfileStore.readText(new File(folder,"takeover-wfz.json"),65536));}
    private static File meta(Context c,String id){directory(c,id);return new File(new File(root(c),"metadata"),id+".json");}
    private static Entry entry(Context c,String id)throws Exception {
        File folder=directory(c,id);boolean favorite=false;try{favorite=new JSONObject(ProfileStore.readText(meta(c,id),4096)).optBoolean("favorite");}catch(Exception ignored){}
        return new Entry(id,info(folder).optString("title",I18n.get(R.string.wfz_unnamed)),folder,favorite);
    }
    static synchronized List<Entry> list(Context c){
        List<Entry> entries=new ArrayList<>();File[] folders=new File(root(c),"faces").listFiles();
        if(folders!=null)for(File folder:folders)if(folder.isDirectory()&&folder.getName().matches("[a-f0-9]{64}"))try{entries.add(entry(c,folder.getName()));}catch(Exception ignored){}
        entries.sort((a,b)->a.title.compareToIgnoreCase(b.title));return entries;
    }
    static synchronized Result importFile(Context context,Uri uri)throws Exception {
        Context c=context.getApplicationContext();root(c).mkdirs();File stage=new File(root(c),"stage-"+UUID.randomUUID());File unpack=new File(stage,"unpack");unpack.mkdirs();
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException(I18n.get(R.string.wfz_invalid));ProfileStore.extract(in,unpack,ProfileStore.MAX_BYTES,WfzImport.MAX_ENTRIES);}
            Set<File> files=new LinkedHashSet<>();ProfileStore.collectFiles(unpack,files);List<File> roots=new ArrayList<>();for(File f:files)if(f.getName().equals("watchface.xml"))roots.add(f.getParentFile());
            if(roots.size()!=1)throw new IOException(I18n.get(R.string.wfz_invalid));File face=roots.get(0);
            WfzImport.writeInfo(face,WfzImport.displayName(c,uri));return publish(c,face);
        }finally{ProfileStore.deleteTree(stage);}
    }
    private static Result publish(Context c,File folder)throws Exception {
        String id=fingerprint(folder);File dest=directory(c,id);if(dest.isDirectory()){WfzImport.refreshInfo(dest,WfzScene.load(dest));return new Result(id,true);}
        dest.getParentFile().mkdirs();if(!folder.renameTo(dest))throw new IOException("Cannot store watchface");return new Result(id,false);
    }
    /** Canonical archive content, independent of ZIP compression, entry order, and generated reports. */
    static String fingerprint(File folder)throws Exception {
        File base=folder.getCanonicalFile();Set<File> found=new LinkedHashSet<>();ProfileStore.collectFiles(base,found);List<File> files=new ArrayList<>(found);files.sort(Comparator.comparing(f->base.toPath().relativize(f.toPath()).toString()));
        if(files.size()>WfzImport.MAX_ENTRIES)throw new IOException("Too many watchface files");MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;byte[] buffer=new byte[8192];
        for(File file:files){
            if(!file.getCanonicalPath().startsWith(base.getPath()+File.separator))throw new IOException("Resource outside watchface");String path=base.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');
            if(path.equals("takeover-wfz.json")||path.equals("takeover-wfz.json.new")||path.equals("takeover-wfz.json.bak"))continue;
            total+=file.length();if(total>ProfileStore.MAX_BYTES)throw new IOException("Watchface exceeds size limit");
            hash.update(path.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);hash.update(java.nio.ByteBuffer.allocate(8).putLong(file.length()).array());
            try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}
        }
        StringBuilder result=new StringBuilder();for(byte b:hash.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    static void attach(Config config,String id){
        if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid watchface ID");
        if(!id.equals(config.wfzGallery)){config.wfzWidgets=new JSONObject();config.wfzVariants=new JSONObject();}
        config.wfzGallery=id;config.wfz="gallery/"+id;config.mode="wfz";
    }
    static synchronized void assign(ProfileStore store,String profile,String id,boolean activate)throws Exception {
        if(!directory(store.context,id).isDirectory())throw new IOException(I18n.get(R.string.wfz_invalid));
        if(!new File(store.dir(profile),"profile.json").isFile())throw new IOException(I18n.get(R.string.wfz_open_interrupted));
        Config config=store.load(profile);attach(config,id);store.save(profile,config);if(activate)store.select(profile);
    }
    static void use(Context c,String profile,String id)throws Exception {assign(new ProfileStore(c),profile,id,true);}
    static synchronized List<String> users(Context c,String id){List<String> result=new ArrayList<>();ProfileStore store=new ProfileStore(c);for(String profile:store.ids()){Config config=store.load(profile);if(id.equals(config.wfzGallery))result.add(config.name);}return result;}
    static synchronized void delete(Context c,String id)throws Exception {
        if(!users(c,id).isEmpty())throw new IOException(I18n.get(R.string.gallery_used));ProfileStore.deleteTree(directory(c,id));meta(c,id).delete();WfzThumbnails.remove(c,id);
    }
    static synchronized void favorite(Context c,String id,boolean value)throws Exception {
        if(!directory(c,id).isDirectory())throw new IOException(I18n.get(R.string.wfz_invalid));File file=meta(c,id);file.getParentFile().mkdirs();AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(new JSONObject().put("favorite",value).toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    /** Copy first, atomically change each profile, then remove only its old WFZ-owned directory. */
    static synchronized int migrate(Context context)throws Exception {
        Context c=context.getApplicationContext();ProfileStore store=new ProfileStore(c);int failures=0;
        for(String id:store.ids()){
            Config config=store.load(id);if(config.wfz.isEmpty()||!config.wfzGallery.isEmpty())continue;String legacy=config.wfz;File stage=new File(root(c),"stage-"+UUID.randomUUID());File copy=new File(stage,"face");
            try{
                File old=store.resolve(id,legacy);fingerprint(old);ProfileStore.copyTree(old,copy);String fallback="";try{fallback=info(old).optString("title","");}catch(Exception ignored){}WfzImport.writeInfo(copy,fallback);Result result=publish(c,copy);
                // Reload before writing, so unrelated settings edited during a migration survive.
                Config latest=store.load(id);if(!latest.wfz.equals(legacy)||!latest.wfzGallery.isEmpty())continue;
                latest.wfzGallery=result.id;latest.wfz="gallery/"+result.id;store.save(id,latest);
                if(legacy.startsWith("wfz-"))ProfileStore.deleteTree(store.resolve(id,legacy.split("/")[0]));
            }catch(Exception e){failures++;}finally{ProfileStore.deleteTree(stage);}
        }return failures;
    }
    static File preview(Entry entry){
        try{
            org.w3c.dom.NodeList list=WfzScene.xml(new File(entry.folder,"description.xml")).getElementsByTagName("preview");
            if(list.getLength()>0){File file=WfzScene.descriptions(entry.folder).path(list.item(0).getTextContent().trim());if(file.isFile())return file;}
        }catch(Exception ignored){}
        for(String name:new String[]{"sample-face.png","sample-face-lofi.png"}){File file=new File(entry.folder,name);if(file.isFile())return file;}return null;
    }
    static Bitmap thumbnail(Entry entry,int edge){
        File file=preview(entry);if(file==null)return null;BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
        if(options.outWidth<1||options.outHeight<1||options.outWidth>4096||options.outHeight>4096)return null;
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>edge*2)options.inSampleSize*=2;options.inJustDecodeBounds=false;
        Bitmap image=BitmapFactory.decodeFile(file.getPath(),options);if(image==null)return null;float ratio=Math.min(1,edge/(float)Math.max(image.getWidth(),image.getHeight()));
        if(ratio<1){Bitmap small=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*ratio)),Math.max(1,Math.round(image.getHeight()*ratio)),true);if(small!=image)image.recycle();return small;}return image;
    }
}
