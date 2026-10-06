package com.miniscreen.takeover;

import android.content.*;
import android.os.*;

/** Device-wide operating state, deliberately separate from portable design profiles. */
final class TakeoverControl {
    static final String SHOW="com.miniscreen.takeover.SHOW", SYNC="com.miniscreen.takeover.SYNC";
    static final String RESTORE="com.miniscreen.takeover.RESTORE", PAUSE="com.miniscreen.takeover.PAUSE";
    static final String RESUME="com.miniscreen.takeover.RESUME", STOP="com.miniscreen.takeover.STOP";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("takeover",0);}
    static String mode(SharedPreferences p){String m=p.getString("monitor_mode","keep");return m.equals("manual")||m.equals("force")?m:"keep";}
    static boolean paused(SharedPreferences p){return p.getLong("pause_until",0)>System.currentTimeMillis();}
    static boolean lockEligible(Context c,SharedPreferences p){if(!p.getBoolean("only_locked",false))return true;android.app.KeyguardManager k=(android.app.KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);return k.isKeyguardLocked()||!power.isInteractive();}
    static boolean wanted(Context c){SharedPreferences p=prefs(c);return p.getBoolean("enabled",false)&&!paused(p)&&!p.getBoolean("external_active",false)&&!DisplaySchedule.manualOff(p,System.currentTimeMillis())&&DisplaySchedule.allowed(p,System.currentTimeMillis())&&lockEligible(c,p);}
    static void show(Context c){
        SharedPreferences p=prefs(c);long now=System.currentTimeMillis(),override=0;
        if(DisplaySchedule.enabled(p)&&!DisplaySchedule.inside(p,now)){DisplaySchedule.Edge next=DisplaySchedule.next(p,now,false);if(next!=null)override=next.time;}
        p.edit().putBoolean("enabled",true).putLong("pause_until",0).putBoolean("yielded",false).putBoolean("manual_off",false).putBoolean("external_active",false)
            .putLong("schedule_override_until",override).putLong("power_generation",p.getLong("power_generation",0)+1).apply();
        DisplaySchedule.plan(c);start(c,SHOW);
    }
    static void sync(Context c){if(prefs(c).getBoolean("enabled",false))start(c,SYNC);}
    static void pause(Context c,long duration){prefs(c).edit().putLong("pause_until",duration==0?Long.MAX_VALUE:System.currentTimeMillis()+duration).apply();DisplaySchedule.plan(c);sync(c);}
    static void resume(Context c){prefs(c).edit().putLong("pause_until",0).putBoolean("external_active",false).apply();DisplaySchedule.reconcile(c);sync(c);}
    static void stop(Context c){prefs(c).edit().putBoolean("enabled",false).putBoolean("schedule_enabled",false).putBoolean("external_active",false).putBoolean("manual_off",false).putLong("power_generation",prefs(c).getLong("power_generation",0)+1).putLong("pause_until",0).putBoolean("yielded",false).putInt("actual",-1).putString("status",I18n.get(R.string.msg_148)).putString("monitor_status",I18n.get(R.string.msg_149)).apply();DisplaySchedule.cancel(c);WatchService.suspendNow();ScreenActivity.closeScreen();c.stopService(new Intent(c,WatchService.class));}
    static void start(Context c,String action){try{c.startForegroundService(new Intent(c,WatchService.class).setAction(action));}catch(RuntimeException e){prefs(c).edit().putString("monitor_status",I18n.get(R.string.msg_150)+e.getMessage()).apply();}}
}
