package com.miniscreen.takeover;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Large Clay pages and settings stay in private storage, never in Binder parcels. */
final class PebbleConfigStore {
    private static final int LIMIT=2*1024*1024;
    private static File directory(Context c){File dir=new File(c.getCacheDir(),"pebble-config");dir.mkdirs();return dir;}
    private static File file(Context c,String id)throws IOException{if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IOException("Invalid configuration identifier");return new File(directory(c),id);}
    static String write(Context c,String value)throws IOException{
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>LIMIT)throw new IOException("Configuration exceeds limit");
        File[] old=directory(c).listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>24L*60*60*1000)f.delete();
        String id=UUID.randomUUID().toString();File target=file(c,id);try(OutputStream out=new FileOutputStream(target)){out.write(bytes);}catch(IOException e){target.delete();throw e;}return id;
    }
    static String read(Context c,String id)throws IOException{try(InputStream in=new FileInputStream(file(c,id));ByteArrayOutputStream out=new ByteArrayOutputStream()){PebbleFiles.copy(in,out,LIMIT);return out.toString("UTF-8");}}
    static void remove(Context c,String id){try{file(c,id).delete();}catch(IOException ignored){}}
}
