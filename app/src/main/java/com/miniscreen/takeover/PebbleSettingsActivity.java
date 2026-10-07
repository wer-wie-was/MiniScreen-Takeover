package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class PebbleSettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private PebbleView preview;private TextView status;private boolean busy,resumed;
    private final PebbleSession.Observer observer=(frame,state)->{if(status!=null)status.setText(PebbleText.status(state));};
    @Override protected void attachBaseContext(Context c){super.attachBaseContext(I18n.wrap(c));}
    @Override public void onCreate(Bundle state){super.onCreate(state);build();}
    private String t(int id){return I18n.get(id);}
    private TextView text(LinearLayout box,String label){TextView v=new TextView(this);v.setText(label);v.setPadding(0,12,0,12);box.addView(v);return v;}
    private void button(LinearLayout box,int label,Runnable action){Button b=new Button(this);b.setText(t(label));b.setAllCaps(false);box.addView(b);b.setOnClickListener(v->{if(!busy)action.run();});}
    private void toggle(LinearLayout box,int label,String key){Switch s=new Switch(this);s.setText(t(label));s.setChecked(TakeoverControl.prefs(this).getBoolean(key,false));box.addView(s);s.setOnCheckedChangeListener((v,on)->{TakeoverControl.prefs(this).edit().putBoolean(key,on).apply();if(preview!=null)preview.invalidate();});}
    private void slider(LinearLayout box,int label,String key,int min,int max,int fallback){TextView title=text(box,t(label));SeekBar seek=new SeekBar(this);seek.setMax(max-min);int current=Config.clamp(TakeoverControl.prefs(this).getInt(key,fallback),min,max);seek.setProgress(current-min);title.setText(t(label)+": "+current);box.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){title.setText(t(label)+": "+(n+min));if(user)TakeoverControl.prefs(PebbleSettingsActivity.this).edit().putInt(key,n+min).apply();}});}
    private void build(){if(preview!=null)preview.stop();LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);setContentView(outer);preview=new PebbleView(this,true);outer.addView(preview,new LinearLayout.LayoutParams(-1,Math.round(180*getResources().getDisplayMetrics().density)));Config c=new ProfileStore(this).load();preview.update(c,0,0,resumed);status=text(outer,"");
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(24,12,24,24);scroll.addView(body);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));text(body,t(R.string.pebble_section));text(body,TakeoverControl.prefs(this).getString("pebble_name",t(R.string.pebble_no_face)));
        button(body,R.string.pebble_import,()->pick(31,"application/octet-stream"));text(body,t(R.string.pebble_bundled));text(body,"WebView: "+PebbleWebView.version());button(body,R.string.pebble_runtime,()->pick(32,"application/zip"));
        List<String> boards=new ArrayList<>();try{JSONObject info=PebbleFiles.inspect(PebbleFiles.watchface(this));JSONArray all=info.getJSONArray("platforms");for(int i=0;i<all.length();i++)boards.add(all.getString(i));}catch(Exception ignored){}if(boards.isEmpty())boards.addAll(Arrays.asList(PebbleFiles.PLATFORMS));
        text(body,t(R.string.pebble_platform));Spinner select=new Spinner(this);select.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,boards));String selected=TakeoverControl.prefs(this).getString("pebble_platform","gabbro");int index=Math.max(0,boards.indexOf(selected));select.setSelection(index);body.addView(select);int[] old={index};select.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> v){}public void onItemSelected(AdapterView<?> p,android.view.View v,int i,long id){if(old[0]!=i){old[0]=i;TakeoverControl.prefs(PebbleSettingsActivity.this).edit().putString("pebble_platform",boards.get(i)).putLong("pebble_revision",System.currentTimeMillis()).apply();}}});
        toggle(body,R.string.pebble_crop,"pebble_crop");toggle(body,R.string.pebble_network,"pebble_network");slider(body,R.string.pebble_fps,"pebble_fps",1,30,10);
        button(body,R.string.pebble_configure,()->PebbleSession.get(this).command("configuration",""));
        for(String name:new String[]{"back","up","select","down"}){Button key=new Button(this);key.setText(name.equals("back")?"◀":name.equals("up")?"▲":name.equals("down")?"▼":"●");body.addView(key);key.setOnClickListener(v->PebbleSession.get(this).command("button",name));}
        button(body,R.string.pebble_health,()->startActivity(new Intent(this,PebbleHealthActivity.class)));slider(body,R.string.pebble_max_age,"pebble_health_max_age",1,120,15);text(body,t(R.string.pebble_help));
    }
    private void pick(int request,String type){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),request);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;busy=true;status.setText(t(R.string.msg_007));worker.execute(()->{try{if(request==31)PebbleFiles.importPbw(this,data.getData());else if(request==32)PebbleFiles.importRuntime(this,data.getData());runOnUiThread(()->{if(isDestroyed())return;busy=false;build();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;new AlertDialog.Builder(this).setMessage(t(R.string.pebble_import_failed)+"\n"+e.getMessage()).setPositiveButton(android.R.string.ok,null).show();});}});}
    @Override protected void onResume(){super.onResume();resumed=true;PebbleSession.get(this).subscribe(observer,true);if(preview!=null)preview.update(new ProfileStore(this).load(),0,0,true);}
    @Override protected void onPause(){resumed=false;if(preview!=null)preview.stop();PebbleSession.get(this).unsubscribe(observer);super.onPause();}
    @Override protected void onDestroy(){if(preview!=null)preview.stop();worker.shutdown();super.onDestroy();}
}
