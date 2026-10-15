package com.miniscreen.takeover;

import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Rebuildable generated images, deliberately outside gallery assets and portable profiles. */
final class WfzThumbnails {
    static final int EDGE=320;
    private static final int VERSION=59,MAX_FILES=128;
    private static final long MAX_BYTES=32L*1024*1024;
    private static File root(Context c){return new File(c.getCacheDir(),"wfz-thumbnails");}
    private static File file(Context c,String id,String language){
        if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid watchface ID");
        language=language.replaceAll("[^a-zA-Z0-9-]","_");
        return new File(root(c),id+"-"+VERSION+"-"+language+".png");
    }
    static synchronized Bitmap read(Context c,String id,String language){
        File file=file(c,id,language);if(!file.isFile())return null;AtomicFile atomic=new AtomicFile(file);
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=atomic.openRead()){BitmapFactory.decodeStream(in,null,options);}
            if(options.outWidth!=EDGE||options.outHeight!=EDGE)throw new IOException("Invalid thumbnail size");
            Bitmap result;try(InputStream in=atomic.openRead()){result=BitmapFactory.decodeStream(in);}
            if(result==null)throw new IOException("Invalid thumbnail");file.setLastModified(System.currentTimeMillis());return result;
        }catch(IOException|RuntimeException e){atomic.delete();return null;}
    }
    static synchronized void write(Context c,String id,Bitmap image,String language)throws IOException {
        if(image.getWidth()!=EDGE||image.getHeight()!=EDGE)throw new IOException("Invalid thumbnail size");
        File folder=root(c);if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create thumbnail cache");
        AtomicFile atomic=new AtomicFile(file(c,id,language));FileOutputStream out=null;
        try{out=atomic.startWrite();if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Cannot encode thumbnail");atomic.finishWrite(out);}
        catch(IOException|RuntimeException e){if(out!=null)atomic.failWrite(out);throw e;}
        trim(folder);
    }
    static synchronized void remove(Context c,String id){
        if(!id.matches("[a-f0-9]{64}"))return;File[] files=root(c).listFiles();
        if(files!=null)for(File file:files)if(file.getName().startsWith(id+"-"))file.delete();
    }
    private static void trim(File folder){
        File[] files=folder.listFiles(f->f.isFile()&&f.getName().endsWith(".png"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long bytes=0;for(File file:files)bytes+=file.length();int count=files.length;
        for(File file:files){if(count<=MAX_FILES&&bytes<=MAX_BYTES)break;long length=file.length();if(file.delete()){bytes-=length;count--;}}
    }
    private WfzThumbnails(){}
}
