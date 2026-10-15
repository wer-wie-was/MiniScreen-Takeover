package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.widget.*;
import java.io.*;
import java.util.*;

/** File-manager/share import. All incoming bytes still pass through the existing WFZ validator. */
public final class WfzOpenActivity extends Activity {
    static final String IMPORTED="com.miniscreen.takeover.WFZ_IMPORTED";
    private ProfileStore store;
    private Uri uri;
    private String selected="",name="";
    private ImportJob job;
    private boolean resumed,errorShown;
    private final Runnable completion=this::complete;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(I18n.wrap(base));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=new ProfileStore(this);
        try{
            uri=saved==null?incoming(getIntent()):Uri.parse(saved.getString("uri",""));
            if(uri==null||!("content".equals(uri.getScheme())||"file".equals(uri.getScheme())))throw new IOException(I18n.get(R.string.wfz_open_invalid));
            name=filename(uri);
            if(!name.isEmpty()&&name.contains(".")&&!name.toLowerCase(Locale.ROOT).endsWith(".wfz")&&!name.toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IOException(I18n.get(R.string.wfz_open_invalid));
            selected=saved==null?store.active():saved.getString("profile",store.active());
            Object retained=getLastNonConfigurationInstance();if(retained instanceof ImportJob){job=(ImportJob)retained;job.listener=completion;}
            // Do not silently repeat a confirmed import after process death.
            if(job==null&&saved!=null&&saved.getBoolean("importing",false))throw new IOException(I18n.get(R.string.wfz_open_interrupted));
            content();
        }catch(Exception e){failure(e);}
    }
    @SuppressWarnings("deprecation")
    static Uri incoming(Intent intent){
        if(intent==null)return null;
        if(Intent.ACTION_VIEW.equals(intent.getAction()))return intent.getData();
        if(Intent.ACTION_SEND.equals(intent.getAction())){
            Object stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(stream instanceof Uri)return (Uri)stream;
            ClipData clip=intent.getClipData();if(clip!=null&&clip.getItemCount()==1)return clip.getItemAt(0).getUri();
        }
        return null;
    }
    private String filename(Uri source){
        try(Cursor cursor=getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){String value=cursor.getString(0);if(value!=null)return value.length()>160?value.substring(0,160):value;}
        }catch(Exception ignored){}
        return "file".equals(source.getScheme())?new File(source.getPath()).getName():"";
    }
    private void content(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int padding=Math.round(20*getResources().getDisplayMetrics().density);box.setPadding(padding,padding,padding,padding);
        TextView title=new TextView(this);title.setText(I18n.get(R.string.wfz_open_title));title.setTextSize(22);box.addView(title);
        TextView file=new TextView(this);file.setText(name.isEmpty()?I18n.get(R.string.wfz_unnamed):name);file.setTextSize(16);box.addView(file);
        TextView help=new TextView(this);help.setText(I18n.get(R.string.wfz_open_help));box.addView(help);
        if(job!=null){ProgressBar progress=new ProgressBar(this);box.addView(progress);setContentView(box);return;}
        List<String> ids=store.ids();if(ids.isEmpty()){store.active();ids=store.ids();}
        final List<String> profiles=ids;List<String> names=new ArrayList<>();for(String id:profiles)names.add(store.load(id).name);
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,profiles.indexOf(selected)));box.addView(spinner);
        Button confirm=new Button(this);confirm.setText(I18n.get(R.string.wfz_import));box.addView(confirm);
        confirm.setOnClickListener(v->{
            selected=profiles.get(spinner.getSelectedItemPosition());
            job=new ImportJob(getApplicationContext(),uri,selected);job.listener=completion;content();job.start();
        });
        Button cancel=new Button(this);cancel.setText(android.R.string.cancel);cancel.setOnClickListener(v->finish());box.addView(cancel);setContentView(box);
    }
    private void complete(){
        if(!resumed||job==null||!job.done||job.delivered||isFinishing())return;
        if(job.error!=null){if(!errorShown)failure(job.error);return;}
        job.delivered=true;
        try{store.select(job.profile);}catch(Exception e){failure(e);return;}
        store.prefs.edit().putBoolean("wfz_open_design",true).apply();
        Intent intent=new Intent(this,MainActivity.class).setAction(IMPORTED).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);finish();
    }
    private void failure(Exception e){
        errorShown=true;new AlertDialog.Builder(this).setTitle(I18n.get(R.string.wfz_open_title)).setMessage(e.getMessage()==null?I18n.get(R.string.wfz_invalid):e.getMessage()).setPositiveButton(android.R.string.ok,(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
    @Override public void onBackPressed(){if(job!=null&&!job.done){Toast.makeText(this,I18n.get(R.string.msg_114),Toast.LENGTH_SHORT).show();return;}super.onBackPressed();}
    @Override protected void onResume(){super.onResume();resumed=true;complete();}
    @Override protected void onPause(){resumed=false;super.onPause();}
    @Override public Object onRetainNonConfigurationInstance(){return job;}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(uri!=null)out.putString("uri",uri.toString());out.putString("profile",selected);out.putBoolean("importing",job!=null);}
    @Override protected void onDestroy(){if(job!=null&&job.listener==completion)job.listener=null;super.onDestroy();}
    private static final class ImportJob {
        final Context context;final Uri source;final String profile;
        Runnable listener;boolean done,delivered;Exception error;
        ImportJob(Context context,Uri source,String profile){this.context=context;this.source=source;this.profile=profile;}
        void start(){new Thread(()->{
            ProfileStore store=new ProfileStore(context);String imported="";
            try{
                if(!new File(store.dir(profile),"profile.json").isFile())throw new IOException(I18n.get(R.string.wfz_open_interrupted));
                imported=WfzGallery.importFile(context,source).id;
                if(!new File(store.dir(profile),"profile.json").isFile())throw new IOException(I18n.get(R.string.wfz_open_interrupted));
                WfzGallery.assign(store,profile,imported,false);
            }catch(Exception e){error=e;}
            new Handler(Looper.getMainLooper()).post(()->{done=true;if(listener!=null)listener.run();});
        },"WFZ file import").start();}
    }
}
