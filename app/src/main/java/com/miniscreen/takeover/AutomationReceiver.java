package com.miniscreen.takeover;

import android.content.*;
import android.os.*;

/** Opt-in, explicit broadcast endpoint for Tasker and other automation apps. */
public final class AutomationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!AutomationControl.known(intent.getAction()))return;
        if(!TakeoverControl.prefs(c).getBoolean(AutomationControl.ALLOWED,false))return;
        if(!((UserManager)c.getSystemService(Context.USER_SERVICE)).isUserUnlocked())return;
        PendingResult pending=goAsync();
        try{AutomationControl.command(c,intent.getAction());}
        catch(RuntimeException e){TakeoverControl.prefs(c).edit().putString("monitor_status",I18n.get(R.string.automation_error)+": "+e.getMessage()).apply();}
        // DisplayPower's generation-guarded OFF completes after its 400 ms + 1000 ms callbacks.
        finally{new Handler(Looper.getMainLooper()).postDelayed(pending::finish,2500);}
    }
}
