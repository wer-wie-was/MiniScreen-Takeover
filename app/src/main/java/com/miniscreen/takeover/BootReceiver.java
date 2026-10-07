package com.miniscreen.takeover;

import android.content.*;
import android.os.UserManager;
import android.os.Handler;
import android.os.Looper;

/** Ordinary BOOT_COMPLETED is delivered after first unlock; no access to locked profile storage. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){String action=i.getAction();if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        SharedPreferences p=TakeoverControl.prefs(c);UserManager users=(UserManager)c.getSystemService(Context.USER_SERVICE);
        if(!users.isUserUnlocked())return;
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
            // Updating is independent of the user's opt-in for boot restoration.
            // Restore the existing intent; never reset pause, lock, external-app or power controls.
            DisplaySchedule.plan(c);
            if(p.getBoolean("enabled",false)&&!p.getBoolean("external_active",false)
                    &&!DisplaySchedule.manualOff(p,System.currentTimeMillis())&&!p.getBoolean("yielded",false))
                TakeoverControl.start(c,TakeoverControl.RESTORE);
            return;
        }
        if(DisplaySchedule.enabled(p)){PendingResult pending=goAsync();try{DisplaySchedule.reconcile(c);}finally{new Handler(Looper.getMainLooper()).postDelayed(pending::finish,2500);}return;}
        if(!p.getBoolean("restore_boot",false)||!p.getBoolean("enabled",false))return;
        // A deliberate indefinite pause survives a reboot. Timed pauses retain their original deadline.
        TakeoverControl.start(c,TakeoverControl.RESTORE);
    }
}
