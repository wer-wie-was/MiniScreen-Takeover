package com.miniscreen.takeover;

import android.content.*;
import android.os.*;
import android.service.notification.*;

/** Android binds this service only after the user grants notification access. */
public final class TickerListener extends NotificationListenerService {
    private static TickerListener active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver lockEvents=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){NotificationTicker.lockState(c);}};
    private boolean registered;
    static void refresh(){TickerListener s=active;if(s!=null)s.main.post(s::readActive);}
    private void readActive(){try{StatusBarNotification[] all=getActiveNotifications();if(all!=null){java.util.Set<String> keys=new java.util.HashSet<>();for(StatusBarNotification n:all){keys.add(n.getKey());NotificationTicker.posted(this,n);}NotificationTicker.syncActive(keys);}}catch(RuntimeException ignored){}}
    @Override public void onListenerConnected(){super.onListenerConnected();main.post(()->{active=this;NotificationTicker.connected(true);NotificationTicker.lockState(this);IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_USER_PRESENT);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);if(!registered){if(Build.VERSION.SDK_INT>=33)registerReceiver(lockEvents,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(lockEvents,f);registered=true;}readActive();});}
    @Override public void onNotificationPosted(StatusBarNotification n){main.post(()->{if(active==this)NotificationTicker.posted(this,n);});}
    @Override public void onNotificationRemoved(StatusBarNotification n){main.post(()->NotificationTicker.removed(n.getKey()));}
    @Override public void onListenerDisconnected(){main.post(()->disconnect());super.onListenerDisconnected();}
    private void disconnect(){if(active==this){active=null;NotificationTicker.connected(false);}if(registered){unregisterReceiver(lockEvents);registered=false;}}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);disconnect();super.onDestroy();}
}
