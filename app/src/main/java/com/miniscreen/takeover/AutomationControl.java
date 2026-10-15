package com.miniscreen.takeover;

import android.content.*;

/** Device-local automation state. Does not belong to an exported design profile. */
final class AutomationControl {
    static final String PREFIX="com.miniscreen.takeover.";
    static final String ALLOWED="automation_allowed",SUSPENDED="automation_suspended",SCREEN_OFF="automation_screen_off";
    static boolean blocked(SharedPreferences p){return p.getBoolean(SUSPENDED,false)||p.getBoolean(SCREEN_OFF,false);}
    static boolean known(String action){return (PREFIX+"ENABLE").equals(action)||(PREFIX+"DISABLE").equals(action)
        ||(PREFIX+"SUSPEND").equals(action)||(PREFIX+"RESUME").equals(action)
        ||(PREFIX+"SCREEN_ON").equals(action)||(PREFIX+"SCREEN_OFF").equals(action);}
    static void command(Context context,String action){
        if(!known(action))return;Context c=context.getApplicationContext();SharedPreferences p=TakeoverControl.prefs(c);
        switch(action.substring(PREFIX.length())){
            case "ENABLE":p.edit().putBoolean("enabled",true).apply();restore(c);break;
            case "DISABLE":p.edit().putBoolean("enabled",false).apply();release(c,R.string.automation_disabled);break;
            case "SUSPEND":p.edit().putBoolean(SUSPENDED,true).apply();release(c,R.string.automation_suspended);break;
            case "RESUME":p.edit().putBoolean(SUSPENDED,false).apply();restore(c);break;
            case "SCREEN_OFF":
                p.edit().putBoolean(SCREEN_OFF,true).apply();DisplayPower.off(c,true);break;
            case "SCREEN_ON":
                long now=System.currentTimeMillis(),override=p.getLong("schedule_override_until",0);
                if(DisplaySchedule.enabled(p)&&!DisplaySchedule.inside(p,now)){
                    DisplaySchedule.Edge edge=DisplaySchedule.next(p,now,false);if(edge!=null)override=edge.time;
                }
                // Invalidate any delayed OFF before asking the manufacturer to turn the panel on.
                p.edit().putBoolean(SCREEN_OFF,false).putBoolean("manual_off",false).putLong("manual_off_until",0)
                    .putLong("schedule_override_until",override).putLong("power_generation",p.getLong("power_generation",0)+1).apply();
                c.sendBroadcast(new Intent("com.yft.miniscreen.action.GC_SCREEN_ON").setPackage("com.yft.miniscreendisplay").putExtra("HALL_CLOSE",false));
                restore(c);break;
        }
    }
    private static void release(Context c,int message){
        SharedPreferences p=TakeoverControl.prefs(c);
        p.edit().putLong("power_generation",p.getLong("power_generation",0)+(p.getBoolean(SCREEN_OFF,false)?0:1)).putInt("actual",-1)
            .putString("status",I18n.get(message)).putString("monitor_status",I18n.get(message)).apply();
        WatchService.suspendNow();ScreenActivity.closeScreen();c.stopService(new Intent(c,WatchService.class));DisplaySchedule.plan(c);
    }
    private static void restore(Context c){
        SharedPreferences p=TakeoverControl.prefs(c);DisplaySchedule.plan(c);
        if(blocked(p)||!p.getBoolean("enabled",false))return;
        if(MusicControl.blocked(p)&&TakeoverControl.wantedWithoutMusic(c)){
            // Keep an explicit resume pending behind the music gate, including
            // manual mode after SUSPEND stopped the foreground service.
            TakeoverControl.start(c,TakeoverControl.RESTORE);return;
        }
        if(TakeoverControl.wanted(c)){
            p.edit().putBoolean("yielded",false).apply();TakeoverControl.start(c,TakeoverControl.RESTORE);
        }else DisplaySchedule.reconcile(c);
    }
}
