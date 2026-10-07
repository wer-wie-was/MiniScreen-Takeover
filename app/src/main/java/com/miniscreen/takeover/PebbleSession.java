package com.miniscreen.takeover;

import android.content.*;
import android.graphics.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One main-process connection and health poll, regardless of how many display surfaces exist. */
final class PebbleSession implements SharedPreferences.OnSharedPreferenceChangeListener {
    interface Observer{void changed(Bitmap frame,String status);}
    private static PebbleSession instance;static PebbleSession get(Context c){if(instance==null)instance=new PebbleSession(c.getApplicationContext());return instance;}
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());private final Map<Observer,Boolean> observers=new LinkedHashMap<>();
    private final ExecutorService health=Executors.newSingleThreadExecutor();private Messenger remote;private boolean bound,reading;private long healthGeneration;private Bitmap frame;private String status="";
    private final Runnable poll=()->readHealth();
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),m->{
        if(m.what==PebbleService.FRAME){byte[] png=m.getData().getByteArray("png");if(png!=null&&png.length<512*1024)frame=BitmapFactory.decodeByteArray(png,0,png.length);notifyObservers();}
        else if(m.what==PebbleService.STATUS){status=m.getData().getString("status","");if(Arrays.asList("booting","runtime_missing","watchface_missing","engine_crashed","engine_failed","webview_incompatible","webview_update_required","webview_isolation_failed","webview_secure_context_missing","webview_shared_memory_missing","webview_wasm_missing","install_failed").contains(status))frame=null;notifyObservers();}
        else if(m.what==PebbleService.CONFIG){String url=m.getData().getString("url","");openConfig(url);}
        return true;
    }));
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){remote=new Messenger(binder);send(PebbleService.CONNECT,null);settings();readHealth();}
        public void onServiceDisconnected(ComponentName name){remote=null;frame=null;status="engine_crashed";notifyObservers();}
        public void onBindingDied(ComponentName name){onServiceDisconnected(name);if(bound){context.unbindService(this);bound=false;}if(!observers.isEmpty())bind();}
    };
    private void openConfig(String url){if(!url.isEmpty()&&observers.containsValue(true))context.startActivity(new Intent(context,PebbleConfigActivity.class).putExtra("url",url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private PebbleSession(Context c){context=c;TakeoverControl.prefs(c).registerOnSharedPreferenceChangeListener(this);}
    void subscribe(Observer o,boolean foreground){observers.put(o,foreground);o.changed(frame,status);if(!bound)bind();if(remote!=null){settings();readHealth();}}
    private void bind(){bound=context.bindService(new Intent(context,PebbleService.class),connection,Context.BIND_AUTO_CREATE);if(!bound){status="engine_failed";notifyObservers();}}
    void unsubscribe(Observer o){observers.remove(o);if(observers.isEmpty()){main.removeCallbacks(poll);healthGeneration++;if(bound){send(PebbleService.DISCONNECT,null);context.unbindService(connection);bound=false;remote=null;}frame=null;}}
    private void send(int what,JSONObject json){if(remote==null)return;try{Message m=Message.obtain(null,what);m.replyTo=receiver;if(json!=null){Bundle b=new Bundle();b.putString("json",json.toString());m.setData(b);}remote.send(m);}catch(RemoteException e){status="engine_crashed";notifyObservers();}}
    void command(String type,String value){try{send(PebbleService.COMMAND,new JSONObject().put("type",type).put("value",value));}catch(JSONException ignored){}}
    private void settings(){android.content.SharedPreferences p=TakeoverControl.prefs(context);try{send(PebbleService.SETTINGS,new JSONObject().put("platform",p.getString("pebble_platform","gabbro")).put("revision",p.getLong("pebble_revision",0)).put("network",p.getBoolean("pebble_network",false)).put("fps",Config.clamp(p.getInt("pebble_fps",10),1,30)).put("twentyFour",android.text.format.DateFormat.is24HourFormat(context)).put("battery",battery()).put("healthMaxAge",Config.clamp(p.getInt("pebble_health_max_age",15),1,120)));}catch(JSONException ignored){}}
    private JSONObject battery(){JSONObject data=new JSONObject();Intent i=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i!=null)try{int total=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);data.put("level",total>0?Config.clamp(i.getIntExtra(BatteryManager.EXTRA_LEVEL,0)*100/total,0,100):0).put("charging",i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0);}catch(JSONException ignored){}return data;}
    private void readHealth(){main.removeCallbacks(poll);if(remote==null||observers.isEmpty())return;if(reading){main.postDelayed(poll,1000);return;}reading=true;settings();long generation=healthGeneration;PebbleHealth.read(context,observers.containsValue(true),health,json->main.post(()->{reading=false;if(generation==healthGeneration&&remote!=null)try{send(PebbleService.COMMAND,new JSONObject().put("type","health").put("value",json));}catch(JSONException ignored){}if(!observers.isEmpty())main.postDelayed(poll,60000);}));}
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if(key.startsWith("pebble_")){settings();if(key.startsWith("pebble_health")){healthGeneration++;readHealth();}}}
    private void notifyObservers(){for(Observer observer:new ArrayList<>(observers.keySet()))observer.changed(frame,status);}
}
