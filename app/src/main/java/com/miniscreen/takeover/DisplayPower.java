package com.miniscreen.takeover;

import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;

/** Shared manual/scheduled OFF path, matching Second Screen Test's confirmed GC_SCREEN_OFF test. */
final class DisplayPower {
    private static final long CLOSE_DELAY_MS=400;
    private static final long STATE_DELAY_MS=1000;
    static void off(Context context,boolean manual){
        Context c=context.getApplicationContext();SharedPreferences p=TakeoverControl.prefs(c);long now=System.currentTimeMillis();
        long generation=p.getLong("power_generation",0)+1;
        SharedPreferences.Editor edit=p.edit().putLong("power_generation",generation).putLong("schedule_override_until",0);
        if(manual){DisplaySchedule.Edge next=DisplaySchedule.enabled(p)?DisplaySchedule.next(p,now,true):null;
            edit.putBoolean("manual_off",true).putLong("manual_off_until",next==null?Long.MAX_VALUE:next.time);
            edit.putBoolean("external_active",false);
        }
        // Persist the intentional stop BEFORE closing windows or notifying the monitor.
        edit.putString("monitor_status",I18n.get(R.string.power_intentional)).putString("status",I18n.get(R.string.power_intentional)).apply();
        WatchService.suspendNow();
        PowerManager.WakeLock wake=((PowerManager)c.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MiniScreenTakeover:PowerOff");
        try{
            // CPU only: the locked-main-screen test works without waking either screen.
            wake.acquire(10000);
            ScreenActivity.closeScreen();c.stopService(new Intent(c,WatchService.class));DisplaySchedule.plan(c);
            Handler h=new Handler(Looper.getMainLooper());
            h.postDelayed(()->{
                if(p.getLong("power_generation",0)!=generation){release(wake);return;}
                try{
                    c.sendBroadcast(new Intent("com.yft.miniscreen.action.GC_SCREEN_OFF")
                        .setPackage("com.yft.miniscreendisplay").putExtra("HALL_CLOSE",false));
                    p.edit().putString("status",I18n.get(R.string.power_requested)).apply();
                    h.postDelayed(()->{
                        try{
                            if(p.getLong("power_generation",0)!=generation)return;
                            Display d=((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(1);
                            int state=d==null?Display.STATE_UNKNOWN:d.getState();
                            // A private/missing display is unknown, never a failure or proof of OFF.
                            p.edit().putString("status",I18n.get(state==Display.STATE_OFF?R.string.power_confirmed:
                                state==Display.STATE_UNKNOWN?R.string.power_unknown:R.string.power_unconfirmed)).apply();
                        }catch(RuntimeException e){
                            if(p.getLong("power_generation",0)==generation)p.edit().putString("status",I18n.get(R.string.power_unknown)).apply();
                        }finally{release(wake);}
                    },STATE_DELAY_MS);
                }catch(RuntimeException e){
                    p.edit().putString("status",I18n.get(R.string.power_failed)+e.getMessage()).apply();release(wake);
                }
            },CLOSE_DELAY_MS);
        }catch(RuntimeException e){
            release(wake);ScreenActivity.closeScreen();c.stopService(new Intent(c,WatchService.class));
            p.edit().putString("status",I18n.get(R.string.power_failed)+e.getMessage()).apply();DisplaySchedule.plan(c);
        }
    }
    private static void release(PowerManager.WakeLock wake){if(wake.isHeld())wake.release();}
}
