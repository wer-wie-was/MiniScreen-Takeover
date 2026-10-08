package com.miniscreen.takeover;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.ref.WeakReference;

public final class ScreenActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static WeakReference<ScreenActivity> current=new WeakReference<>(null);
    private ProfileStore store;
    private DisplaySurface surface;
    private int display;
    private boolean started;
    private final Handler brightnessHandler=new Handler(Looper.getMainLooper());private int lastBrightness=-1;private boolean timeRegistered;
    private final Runnable brightnessTick=()->updateBrightness();
    private final BroadcastReceiver timeChanges=new BroadcastReceiver(){public void onReceive(Context c,Intent i){updateBrightness();}};
    private void updateBrightness(){brightnessHandler.removeCallbacks(brightnessTick);if(store==null||surface==null)return;Config c=store.load();int value=BrightnessCurve.value(c,java.util.Calendar.getInstance());if(value!=lastBrightness){WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=Math.max(1,value)/100f;getWindow().setAttributes(p);lastBrightness=value;}if(started&&c.brightnessCurveEnabled)brightnessHandler.postDelayed(brightnessTick,c.brightnessCurveMinutes*60000L);}
    static boolean visibleOn(int id){ScreenActivity a=current.get();return a!=null&&!a.isFinishing()&&a.started&&a.display==id;}
    static int reportedState(int id){ScreenActivity a=current.get();if(a==null||a.isFinishing()||a.display!=id)return Display.STATE_UNKNOWN;try{return a.getWindowManager().getDefaultDisplay().getState();}catch(RuntimeException e){return Display.STATE_UNKNOWN;}}
    static void closeScreen(){ScreenActivity a=current.get();if(a!=null){a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);a.started=false;if(a.surface!=null)a.surface.stop();a.finishAndRemoveTask();}}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new ProfileStore(this);display=getWindowManager().getDefaultDisplay().getDisplayId();
        int requested=1;
        if(!TakeoverControl.wanted(this)){finish();return;}
        if(display==Display.DEFAULT_DISPLAY||display!=requested){store.prefs.edit().putString("status",I18n.get(R.string.msg_151)+requested+I18n.get(R.string.msg_152)+display).apply();finish();return;}
        current=new WeakReference<>(this);store.prefs.edit().putBoolean("enabled",true).putString("status",I18n.get(R.string.msg_153)+display).putInt("actual",display).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=27)setShowWhenLocked(true);else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        immersive();surface=new DisplaySurface(this,false);setContentView(surface);apply();store.prefs.registerOnSharedPreferenceChangeListener(this);
    }
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(store.prefs.getBoolean("enabled",false)&&TakeoverControl.paused(store.prefs)&&TakeoverControl.lockEligible(this,store.prefs))return;setIntent(intent);int requested=1;if(!TakeoverControl.wanted(this)||requested!=display){store.prefs.edit().putString("status",I18n.get(R.string.msg_154)).apply();finishAndRemoveTask();return;}store.prefs.edit().putInt("actual",display).putString("status",I18n.get(R.string.msg_153)+display).apply();apply();WatchService.activityChanged();}
    private void apply(){if(surface==null)return;Config c=store.load();updateBrightness();surface.apply(c);}
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if((key.startsWith("ticker_")||key.startsWith("dot_")||key.startsWith("widget_"))&&surface!=null)surface.tick();if(key.equals("enabled")&&!p.getBoolean("enabled",false)){finishAndRemoveTask();return;}if(key.equals("revision")||key.equals("active")||key.equals("language"))apply();}
    @Override protected void onStart(){super.onStart();started=true;IntentFilter tf=new IntentFilter();tf.addAction(Intent.ACTION_TIME_CHANGED);tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);if(!timeRegistered){if(Build.VERSION.SDK_INT>=33)registerReceiver(timeChanges,tf,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(timeChanges,tf);timeRegistered=true;}updateBrightness();if(surface!=null){store.prefs.edit().putString("status",I18n.get(R.string.msg_155)+display).apply();surface.start();WatchService.activityChanged();}}
    @Override protected void onStop(){started=false;brightnessHandler.removeCallbacks(brightnessTick);if(timeRegistered){unregisterReceiver(timeChanges);timeRegistered=false;}if(surface!=null){surface.stop();if(TakeoverControl.wanted(this))store.prefs.edit().putString("status",I18n.get(R.string.msg_156)+display+")").apply();WatchService.activityChanged();}super.onStop();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override public boolean dispatchTouchEvent(MotionEvent e){return true;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getKeyCode()==KeyEvent.KEYCODE_BACK||e.getKeyCode()==KeyEvent.KEYCODE_ENTER||e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER)return true;return super.dispatchKeyEvent(e);}
    @Override public void onBackPressed() { /* Control stays on the main display. */ }
    @Override protected void onDestroy(){started=false;brightnessHandler.removeCallbacksAndMessages(null);if(timeRegistered){unregisterReceiver(timeChanges);timeRegistered=false;}if(store!=null)store.prefs.unregisterOnSharedPreferenceChangeListener(this);if(surface!=null)surface.dispose();if(current.get()==this){current.clear();SharedPreferences.Editor edit=store.prefs.edit().putInt("actual",-1);
        if(!DisplaySchedule.manualOff(store.prefs,System.currentTimeMillis())&&DisplaySchedule.allowed(store.prefs,System.currentTimeMillis()))edit.putString("status",I18n.get(R.string.msg_157));edit.apply();WatchService.activityChanged();}super.onDestroy();}
}
