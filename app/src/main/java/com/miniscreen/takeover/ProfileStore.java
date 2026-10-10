package com.miniscreen.takeover;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

final class ProfileStore {
    static final int MAX_BYTES=20*1024*1024, MAX_FILES=200;
    final Context context;
    final SharedPreferences prefs;
    private final File root;
    ProfileStore(Context c) {context=c.getApplicationContext();prefs=context.getSharedPreferences("takeover",0);root=new File(context.getFilesDir(),"profiles");root.mkdirs();}
    String active() {
        String id=prefs.getString("active","");
        if(id.isEmpty()||!new File(dir(id),"profile.json").isFile()) {
            id=UUID.randomUUID().toString(); save(id,new Config()); prefs.edit().putString("active",id).apply();
        }
        return id;
    }
    File dir(String id) {if(!id.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException(I18n.get(R.string.msg_161));return new File(root,id);}
    Config load() {return load(active());}
    Config load(String id) {try {return Config.from(new JSONObject(readText(new File(dir(id),"profile.json"),1024*1024)));} catch(Exception e) {return new Config();}}
    void save(Config c) {save(active(),c);}
    void save(String id,Config c) {
        File folder=dir(id); folder.mkdirs(); AtomicFile a=new AtomicFile(new File(folder,"profile.json")); FileOutputStream out=null;
        try {out=a.startWrite();out.write(c.json().toString(2).getBytes(StandardCharsets.UTF_8));a.finishWrite(out);}
        catch(Exception e) {if(out!=null)a.failWrite(out);throw new IllegalStateException(I18n.get(R.string.msg_162),e);}
        prefs.edit().putLong("revision",prefs.getLong("revision",0)+1).apply();
    }
    void select(String id) {if(!new File(dir(id),"profile.json").isFile())throw new IllegalArgumentException(I18n.get(R.string.msg_163));prefs.edit().putString("active",id).putLong("revision",prefs.getLong("revision",0)+1).apply();}
    List<String> ids() {List<String> ids=new ArrayList<>(); File[] ds=root.listFiles();if(ds!=null)for(File d:ds)if(new File(d,"profile.json").isFile())ids.add(d.getName());Collections.sort(ids);return ids;}
    String create(String name,boolean duplicate) throws IOException {
        String id=UUID.randomUUID().toString(); Config c=duplicate?load():new Config();
        if(duplicate)copyTree(dir(active()),dir(id)); c.name=name;save(id,c);select(id);return id;
    }
    void deleteActive() {String old=active(); List<String> all=ids();all.remove(old);if(all.isEmpty())try {create(I18n.get(R.string.msg_046),false);}catch(IOException e){throw new IllegalStateException(e);}else select(all.get(0));deleteTree(dir(old));}
    File resolve(String id,String path) throws IOException {File folder=dir(id).getCanonicalFile(), f=new File(folder,path).getCanonicalFile();if(!f.getPath().startsWith(folder.getPath()+File.separator))throw new IOException(I18n.get(R.string.msg_164));return f;}
    String importAsset(String id,Uri uri,String prefix,String ext) throws IOException {
        File folder=dir(id);folder.mkdirs();String path=prefix+"-"+UUID.randomUUID()+ext;File dest=resolve(id,path);
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)) {if(in==null)throw new IOException(I18n.get(R.string.msg_165));copyLimited(in,out,MAX_BYTES);}
        catch(IOException e){dest.delete();throw e;}return path;
    }
    String importDesign(String id,Uri uri,boolean zip) throws IOException {
        String path="design-"+UUID.randomUUID();File folder=resolve(id,path);folder.mkdirs();
        try {
            if(zip)try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException(I18n.get(R.string.msg_165));extract(in,folder);}
            else try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(new File(folder,"index.html"))) {if(in==null)throw new IOException(I18n.get(R.string.msg_165));copyLimited(in,out,1024*1024);}
            List<File> entries=new ArrayList<>();findIndex(folder,entries);
            File entry=new File(folder,"index.html");
            if(!entry.isFile()) {if(entries.size()!=1)throw new IOException(I18n.get(R.string.msg_166));entry=entries.get(0);}
            String rel=folder.toPath().relativize(entry.toPath()).toString().replace(File.separatorChar,'/');
            String result=path+"/"+rel;
            if(Config.safePath(result).isEmpty())throw new IOException(I18n.get(R.string.msg_167));
            if(entry.length()>1024*1024)throw new IOException(I18n.get(R.string.msg_168));return result;
        }catch(IOException e){deleteTree(folder);throw e;}
    }
    String builtIn(String id) throws IOException {
        String path="design-"+UUID.randomUUID();File folder=resolve(id,path);folder.mkdirs();
        try(InputStream in=context.getAssets().open("designs/classic/index.html");OutputStream out=new FileOutputStream(new File(folder,"index.html"))) {copyLimited(in,out,1024*1024);}return path+"/index.html";
    }
    String importProfile(Uri uri) throws IOException {
        String id=UUID.randomUUID().toString();File folder=dir(id);folder.mkdirs();
        try {
            try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException(I18n.get(R.string.msg_165));extract(in,folder,64*1024*1024,2000);}
            JSONObject j=new JSONObject(readText(new File(folder,"profile.json"),1024*1024));
            if(j.optInt("schema")!=1)throw new IOException(I18n.get(R.string.msg_169));Config c=Config.from(j);
            if(!c.design.isEmpty()&&c.design.indexOf('/')<1)throw new IOException(I18n.get(R.string.msg_170));
            for(String path:new String[]{c.image,c.fontFile,c.chargeFontFile,c.design})if(!path.isEmpty()&&!resolve(id,path).isFile())throw new IOException(I18n.get(R.string.msg_171));
            if(c.mode.equals("html")&&c.design.isEmpty())throw new IOException(I18n.get(R.string.msg_172));
            if(!c.wfz.isEmpty())WfzScene.load(resolve(id,c.wfz));
            save(id,c);return id;
        }catch(Exception e){deleteTree(folder);throw new IOException(I18n.get(R.string.msg_173)+e.getMessage(),e);}
    }
    void exportProfile(String id,Uri uri) throws IOException {
        Config c=load(id);File folder=dir(id).getCanonicalFile();
        try(OutputStream out=context.getContentResolver().openOutputStream(uri,"wt")) {if(out==null)throw new IOException(I18n.get(R.string.msg_174));try(ZipOutputStream z=new ZipOutputStream(out)){
            Set<File> files=new LinkedHashSet<>();files.add(new File(folder,"profile.json").getCanonicalFile());
            if(!c.image.isEmpty())files.add(resolve(id,c.image));
            if(!c.fontFile.isEmpty())files.add(resolve(id,c.fontFile));
            if(!c.chargeFontFile.isEmpty())files.add(resolve(id,c.chargeFontFile));
            if(!c.design.isEmpty())collectFiles(resolve(id,c.design.substring(0,c.design.indexOf('/'))),files);
            if(!c.wfz.isEmpty())collectFiles(resolve(id,c.wfz.split("/")[0]),files);
            for(File file:files)zipFile(folder,file,z);
        }}
    }
    static String readText(File f,int max) throws IOException {try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){copyLimited(in,out,max);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    static long copyLimited(InputStream in,OutputStream out,long max) throws IOException {byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException(I18n.get(R.string.msg_175)+max/1024+" KiB)");out.write(buffer,0,n);}return total;}
    static void extract(InputStream source,File folder) throws IOException {extract(source,folder,MAX_BYTES,MAX_FILES);}
    static void extract(InputStream source,File folder,int maxBytes,int maxFiles) throws IOException {
        long total=0;int count=0;Set<String> seen=new HashSet<>();String base=folder.getCanonicalPath()+File.separator;
        try(ZipInputStream z=new ZipInputStream(source)){ZipEntry e;while((e=z.getNextEntry())!=null){if(++count>maxFiles)throw new IOException(I18n.get(R.string.msg_176)+maxFiles+")");
            String name=e.getName();if(name.startsWith("/")||name.contains("\\")||name.indexOf('\0')>=0||name.length()>240||name.split("/").length>12)throw new IOException(I18n.get(R.string.msg_177));
            File f=new File(folder,name).getCanonicalFile();if(!f.getPath().startsWith(base)||!seen.add(f.getPath()))throw new IOException(I18n.get(R.string.msg_178));
            if(e.isDirectory()){f.mkdirs();continue;}f.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(f)){long fileLimit=Math.min(MAX_BYTES,maxBytes-total);String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith(".html")||lower.endsWith(".htm"))fileLimit=Math.min(fileLimit,1024*1024);total+=copyLimited(z,out,fileLimit);}z.closeEntry();
        }}
    }
    static void findIndex(File f,List<File> found) {File[] files=f.listFiles();if(files==null)return;for(File x:files)if(x.isDirectory())findIndex(x,found);else if(x.getName().equals("index.html"))found.add(x);}
    static void copyTree(File from,File to) throws IOException {to.mkdirs();File[] files=from.listFiles();if(files==null)return;for(File f:files){File dest=new File(to,f.getName());if(f.isDirectory())copyTree(f,dest);else try(InputStream in=new FileInputStream(f);OutputStream out=new FileOutputStream(dest)){copyLimited(in,out,MAX_BYTES);}}}
    static void deleteTree(File f) {File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);f.delete();}
    static void zipFile(File root,File f,ZipOutputStream z) throws IOException {z.putNextEntry(new ZipEntry(root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/')));try(InputStream in=new FileInputStream(f)){copyLimited(in,z,MAX_BYTES);}z.closeEntry();}
    static void collectFiles(File folder,Set<File> result) throws IOException {File[] files=folder.listFiles();if(files==null)return;for(File f:files)if(f.isDirectory())collectFiles(f,result);else result.add(f.getCanonicalFile());}
}
