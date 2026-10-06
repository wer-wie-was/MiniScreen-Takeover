package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;
import java.lang.ref.WeakReference;

/** Event-first display watchdog; Handler fallback deliberately does not wake a sleeping CPU. */
public final class WatchService extends Service implements DisplayManager.DisplayListener,SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String CHANNEL="display_watch";
    private static final int NOTIFICATION=11;
    private static WeakReference<WatchService> current=new WeakReference<>(null);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable checkTask=()->check();
    private final Runnable attemptTask=()->advanceAttempt();
    private SharedPreferences prefs;
    private DisplayManager displays;
    private PowerManager.WakeLock wake;
    private boolean attempting,explicit,everVisible;
    private boolean sawOff,launched;
    private boolean waitingLocked;
    private long deadline,nextAllowed,sessionStart,launchAfter;
    private int failures;
    private String notificationText="";
    private final BroadcastReceiver screenEvents=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(!on()&&TakeoverControl.wanted(WatchService.this))check();else schedule(600);}};
    static void suspendNow(){WatchService s=current.get();if(s!=null){s.cancelAttempt();s.handler.removeCallbacks(s.checkTask);}}
    static void activityChanged(){WatchService s=current.get();if(s!=null)s.schedule(600);}
    @Override public void onCreate(){super.onCreate();current=new WeakReference<>(this);prefs=TakeoverControl.prefs(this);displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);sessionStart=SystemClock.elapsedRealtime();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(CHANNEL,I18n.get(R.string.msg_124),NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION,notification(I18n.get(R.string.msg_125)));
        prefs.registerOnSharedPreferenceChangeListener(this);displays.registerDisplayListener(this,handler);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_TIME_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenEvents,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenEvents,filter);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){String action=i==null?TakeoverControl.SYNC:i.getAction();
        if(TakeoverControl.STOP.equals(action)){TakeoverControl.stop(this);stopSelf();return START_NOT_STICKY;}
        if(TakeoverControl.PAUSE.equals(action)){prefs.edit().putLong("pause_until",Long.MAX_VALUE).apply();DisplaySchedule.plan(this);}
        if(TakeoverControl.RESUME.equals(action)){prefs.edit().putLong("pause_until",0).putBoolean("external_active",false).apply();DisplaySchedule.plan(this);}
        if(TakeoverControl.SHOW.equals(action)){cancelAttempt();explicit=true;failures=0;nextAllowed=0;sessionStart=SystemClock.elapsedRealtime();prefs.edit().putBoolean("yielded",false).apply();schedule(0);}
        else if(TakeoverControl.RESTORE.equals(action)){explicit=true;sessionStart=SystemClock.elapsedRealtime();schedule(2000);}
        else schedule(200);
        return prefs.getBoolean("enabled",false)&&needsMonitor()?START_STICKY:START_NOT_STICKY;
    }
    private int target(){return 1;}
    private Display display(){return displays.getDisplay(target());}
    private int reportedState(){Display d=display();return d==null?ScreenActivity.reportedState(target()):d.getState();}
    private boolean on(){return reportedState()==Display.STATE_ON;}
    private boolean healthy(){int state=reportedState();return visible()&&(state==Display.STATE_ON||state==Display.STATE_UNKNOWN);}
    private boolean visible(){return ScreenActivity.visibleOn(target());}
    private long interval(){return Config.clamp(prefs.getInt("check_interval",60),15,300)*1000L;}
    private boolean needsMonitor(){return !TakeoverControl.mode(prefs).equals("manual")||prefs.getBoolean("only_locked",false);}
    private void schedule(long ms){handler.removeCallbacks(checkTask);handler.postDelayed(checkTask,Math.max(0,ms));}
    private void check(){
        if(!prefs.getBoolean("enabled",false)){cancelAttempt();stopSelf();return;}
        if(DisplaySchedule.manualOff(prefs,System.currentTimeMillis())){cancelAttempt();state(I18n.get(R.string.power_intentional));stopSelf();return;}
        // Intentional schedule-off wins over the ordinary keep/force recovery modes.
        if(!TakeoverControl.paused(prefs)&&!prefs.getBoolean("external_active",false)&&!DisplaySchedule.allowed(prefs,System.currentTimeMillis())){DisplayPower.off(this,false);return;}
        if(prefs.getBoolean("external_active",false)){cancelAttempt();state(I18n.get(R.string.schedule_external));schedule(interval());return;}
        if(!TakeoverControl.lockEligible(this,prefs)){cancelAttempt();if(!waitingLocked){waitingLocked=true;explicit=true;everVisible=false;sawOff=false;nextAllowed=0;failures=0;prefs.edit().putBoolean("yielded",false).apply();}ScreenActivity.closeScreen();state(I18n.get(R.string.msg_126));schedule(interval());return;}
        if(TakeoverControl.paused(prefs)){cancelAttempt();state(I18n.get(R.string.msg_127));long until=prefs.getLong("pause_until",0);schedule(until==Long.MAX_VALUE?interval():Math.min(interval(),Math.max(100,until-System.currentTimeMillis())));return;}
        if(waitingLocked){waitingLocked=false;sessionStart=SystemClock.elapsedRealtime();}
        if(attempting)return;
        if(healthy()){everVisible=true;explicit=false;failures=0;nextAllowed=0;sawOff=false;
            if(!needsMonitor()){state(I18n.get(R.string.msg_128));stopSelf();return;}
            state(I18n.get(R.string.msg_129)+(TakeoverControl.mode(prefs).equals("manual")?I18n.get(R.string.msg_130):modeLabel()));schedule(interval());return;
        }
        String mode=TakeoverControl.mode(prefs);
        if(mode.equals("manual")&&!explicit){if(needsMonitor()){state(I18n.get(R.string.msg_131));schedule(interval());}else{state(I18n.get(R.string.msg_132));stopSelf();}return;}
        if(!explicit&&mode.equals("keep")){
            // Missing lifecycle after a process restart is not treated as proof of a competing app.
            if(on()&&everVisible&&!visible()&&!sawOff)prefs.edit().putBoolean("yielded",true).apply();
            if(prefs.getBoolean("yielded",false)){state(I18n.get(R.string.msg_133));schedule(interval());return;}
        }
        long now=SystemClock.elapsedRealtime();
        if(mode.equals("manual")&&now-sessionStart>60000){state(I18n.get(R.string.msg_134));stopSelf();return;}
        if(now<nextAllowed){schedule(Math.min(interval(),nextAllowed-now));return;}
        beginAttempt();
    }
    private void beginAttempt(){if(!TakeoverControl.wanted(this))return;attempting=true;launched=false;long now=SystemClock.elapsedRealtime();deadline=now+10000;launchAfter=now+(on()?0:700);
        try{
            if(!on()){
                sawOff=true;wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MiniScreenTakeover:RestoreDisplay");wake.acquire(12000);
                sendBroadcast(new Intent("com.yft.miniscreen.action.GC_SCREEN_ON").setPackage("com.yft.miniscreendisplay").putExtra("HALL_CLOSE",false));state(I18n.get(R.string.msg_135));
            }
            handler.postDelayed(attemptTask,on()?0:400);
        }catch(RuntimeException e){failed(I18n.get(R.string.msg_136)+e.getMessage());}
    }
    private void advanceAttempt(){
        if(!attempting)return;if(!TakeoverControl.wanted(this)){cancelAttempt();schedule(0);return;}
        if(healthy()){cancelAttempt();explicit=false;everVisible=true;sawOff=false;failures=0;nextAllowed=0;schedule(0);return;}
        // A private display may be absent until the manufacturer starts an activity on it.
        // Attempt the launch after a short wake grace period even if ON cannot be observed.
        if(!launched&&(on()||SystemClock.elapsedRealtime()>=launchAfter)){
            // keep mode wakes the display without evicting a competing app that took over during recovery.
            if(!explicit&&TakeoverControl.mode(prefs).equals("keep")&&prefs.getBoolean("yielded",false)){cancelAttempt();schedule(0);return;}
            try{launched=true;prefs.edit().putInt("actual",-1).apply();
                sendBroadcast(new Intent("com.yft.miniscreen.action.START_ACTIVITY").setPackage("com.yft.miniscreendisplay").putExtra("activity",new ComponentName(this,ScreenActivity.class).flattenToString()));
                state(I18n.get(R.string.msg_137)+(reportedState()==Display.STATE_UNKNOWN?I18n.get(R.string.msg_138):Integer.toString(reportedState()))+" …");
            }catch(RuntimeException e){failed(I18n.get(R.string.msg_139)+e.getMessage());return;}
        }
        if(SystemClock.elapsedRealtime()>=deadline){int state=reportedState();failed(state==Display.STATE_UNKNOWN?I18n.get(R.string.msg_140):state==Display.STATE_ON?I18n.get(R.string.msg_141):I18n.get(R.string.msg_142)+state);return;}
        handler.postDelayed(attemptTask,400);
    }
    private void failed(String message){cancelAttempt();failures++;long[] delays={5000,15000,30000,60000,120000,300000};long delay=delays[Math.min(failures-1,delays.length-1)];nextAllowed=SystemClock.elapsedRealtime()+delay;state(message+I18n.get(R.string.msg_143)+delay/1000+" s");schedule(Math.min(delay,interval()));}
    private void cancelAttempt(){handler.removeCallbacks(attemptTask);attempting=false;launched=false;if(wake!=null){if(wake.isHeld())wake.release();wake=null;}}
    private String modeLabel(){return TakeoverControl.mode(prefs).equals("force")?I18n.get(R.string.msg_017):I18n.get(R.string.msg_144);}
    private void state(String text){prefs.edit().putString("monitor_status",text).apply();if(!text.equals(notificationText)){notificationText=text;((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification(text));}}
    private PendingIntent action(String action,int code){return PendingIntent.getForegroundService(this,code,new Intent(this,WatchService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(String text){boolean paused=TakeoverControl.paused(prefs);PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_watch).setContentTitle("MiniScreen Takeover").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(0,paused?I18n.get(R.string.msg_145):I18n.get(R.string.msg_146),action(paused?TakeoverControl.RESUME:TakeoverControl.PAUSE,2)).build())
            .addAction(new Notification.Action.Builder(0,I18n.get(R.string.msg_147),action(TakeoverControl.STOP,3)).build()).build();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if(key.equals("enabled")||key.equals("monitor_mode")||key.equals("only_locked")||key.equals("pause_until")||key.equals("check_interval")||key.equals("language")||key.equals("manual_off")||key.equals("external_active")||key.startsWith("schedule_")){
        if(key.equals("language")){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(CHANNEL,I18n.get(R.string.msg_124),NotificationManager.IMPORTANCE_LOW));}
        if(key.equals("monitor_mode")&&TakeoverControl.mode(p).equals("manual")&&!explicit)cancelAttempt();
schedule(100);
    }}
    @Override public void onDisplayAdded(int id){if(id==target())schedule(400);}
    @Override public void onDisplayRemoved(int id){if(id==target()){sawOff=true;schedule(400);}}
    @Override public void onDisplayChanged(int id){if(id==target()){if(!on()){sawOff=true;if(TakeoverControl.wanted(this)){check();return;}}schedule(600);}}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);cancelAttempt();prefs.unregisterOnSharedPreferenceChangeListener(this);displays.unregisterDisplayListener(this);unregisterReceiver(screenEvents);if(current.get()==this)current.clear();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
