package com.miniscreen.takeover;

import android.content.*;
import android.os.UserManager;
import android.os.Handler;
import android.os.Looper;

public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!((UserManager)c.getSystemService(Context.USER_SERVICE)).isUserUnlocked())return;
        String action=i.getAction();
        boolean time=Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action);
        if(!time&&!DisplaySchedule.ALARM.equals(action)&&!"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action))return;
        PendingResult pending=goAsync();
        try{if(time)DisplaySchedule.timeChanged(c);else DisplaySchedule.reconcile(c);}
        finally{new Handler(Looper.getMainLooper()).postDelayed(pending::finish,2500);}
    }
}
