package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent memory-only notification presence. No message text is retained or rendered. */
final class NotificationDot {
    private static final class Entry {String app,fingerprint;boolean acknowledged;}
    private static final Map<String,Entry> entries=new LinkedHashMap<>();
    private static boolean connected,locked,known;
    private static String cycleKey="";private static long cycleStart;
    static Set<String> apps(Context c){return new HashSet<>(TakeoverControl.prefs(c).getStringSet("dot_apps",Collections.emptySet()));}
    static boolean enabled(Context c){return TakeoverControl.prefs(c).getBoolean("dot_enabled",false);}
    static void connection(boolean value){connected=value;if(!value){entries.clear();cycleKey="";}}
    static void lockState(Context c){boolean next=((KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();
        if(known&&locked&&!next&&TakeoverControl.prefs(c).getBoolean("dot_until_unlock",true))for(Entry e:entries.values())e.acknowledged=true;
        known=true;locked=next;
    }
    static void changed(Context c){if(!enabled(c))entries.clear();else entries.values().removeIf(e->!apps(c).contains(e.app));TickerListener.refresh();}
    private static String fingerprint(Notification n){
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");
            for(String key:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT,Notification.EXTRA_TEXT_LINES,Notification.EXTRA_MESSAGES}){
                hashValue(d,n.extras.get(key));d.update((byte)0xff);
            }
            return android.util.Base64.encodeToString(d.digest(),android.util.Base64.NO_WRAP);
        }catch(Exception e){return "";}
    }
    private static void hashValue(MessageDigest d,Object value){
        if(value instanceof android.os.Bundle){android.os.Bundle b=(android.os.Bundle)value;for(String k:new TreeSet<>(b.keySet())){hashValue(d,k);hashValue(d,b.get(k));}}
        else if(value instanceof Object[])for(Object item:(Object[])value)hashValue(d,item);
        else if(value!=null)d.update(value.toString().getBytes(StandardCharsets.UTF_8));
        d.update((byte)0);
    }
    static void posted(Context c,StatusBarNotification n){lockState(c);SharedPreferences p=TakeoverControl.prefs(c);String key=n.getKey(),app=n.getPackageName();int flags=n.getNotification().flags;
        if(!enabled(c)||!apps(c).contains(app)||app.equals(c.getPackageName())||!p.getBoolean("dot_ongoing",false)&&(flags&Notification.FLAG_ONGOING_EVENT)!=0||!p.getBoolean("dot_groups",false)&&(flags&Notification.FLAG_GROUP_SUMMARY)!=0){entries.remove(key);return;}
        String hash=fingerprint(n.getNotification());Entry e=entries.get(key);if(e!=null&&e.fingerprint.equals(hash))return;
        e=new Entry();e.app=app;e.fingerprint=hash;entries.put(key,e);
        while(entries.size()>512)entries.remove(entries.keySet().iterator().next());
    }
    static void removed(String key){entries.remove(key);}
    static void sync(Set<String> keys){entries.keySet().retainAll(keys);}
    static String color(Context c,boolean preview){lockState(c);SharedPreferences p=TakeoverControl.prefs(c);
        if(!preview&&(!enabled(c)||!connected))return null;
        Set<String> selected=apps(c);TreeSet<String> active=new TreeSet<>();
        if(preview)active.addAll(selected);else for(Entry e:entries.values())if(selected.contains(e.app)&&(!p.getBoolean("dot_until_unlock",true)||!e.acknowledged))active.add(e.app);
        if(active.isEmpty())return preview?"#63E6DC":null;
        String key=active.toString();long now=SystemClock.elapsedRealtime();if(preview){List<String> list=new ArrayList<>(active);return Config.color(p.getString("dot_color_"+list.get((int)(now/(Config.clamp(p.getInt("dot_interval",3),1,30)*1000L)%list.size())),"#63E6DC"),"#63E6DC");}if(!key.equals(cycleKey)){cycleKey=key;cycleStart=now;}
        List<String> list=new ArrayList<>(active);int index=(int)((now-cycleStart)/(Config.clamp(p.getInt("dot_interval",3),1,30)*1000L)%list.size());
        return Config.color(p.getString("dot_color_"+list.get(index),"#63E6DC"),"#63E6DC");
    }
}
