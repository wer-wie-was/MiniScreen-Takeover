package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import org.json.*;
import java.util.*;

/** Bounded, memory-only queue. All callers run on the main looper. */
final class NotificationTicker {
    static final class Message {
        final String app,label,text;final int count;final long started;
        Message(String app,String label,String text,int count,long started){this.app=app;this.label=label;this.text=text;this.count=count;this.started=started;}
        JSONObject json(){JSONObject j=new JSONObject();try{j.put("app",app).put("label",label).put("text",text).put("count",count).put("iconUrl","https://miniscreen.local/ticker/icon/"+android.net.Uri.encode(app));}catch(JSONException ignored){}return j;}
    }
    private static final class Entry {
        String key,app,label,title,body;long posted,next,expires;boolean acknowledged;
    }
    private static final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private static Entry current;
    private static long until,gap,started;
    private static final long DEMO_STARTED=SystemClock.elapsedRealtime();
    private static boolean locked,knownLock,connected;
    static final Set<String> observedApps=new HashSet<>();
    static void connected(boolean value){connected=value;if(!value)clear();}
    static void clear(){entries.clear();current=null;gap=0;until=0;}
    static void dismissQueue(){for(Entry e:entries.values()){e.acknowledged=true;e.next=Long.MAX_VALUE;}current=null;gap=0;}
    static void syncActive(Set<String> keys){for(String key:new ArrayList<>(entries.keySet()))if(!keys.contains(key))removed(key);}
    static void settingsChanged(Context c){
        TickerSettings s=new TickerSettings(c);
        if(!s.enabled()){clear();return;}
        entries.values().removeIf(e->!s.apps().contains(e.app));
        if(current!=null&&!entries.containsKey(current.key))current=null;
        // Drop cached content immediately when a privacy level is reduced.
        for(Entry e:entries.values()){int max=Math.max(s.level(e.app,true),s.level(e.app,false));if(max<2)e.title="";if(max<3)e.body="";}
        TickerListener.refresh();
    }
    private static String plain(CharSequence value){if(value==null)return "";String s=value.toString().replaceAll("[\\p{Cc}&&[^\\n\\t]]","").trim();return s.length()>600?s.substring(0,600):s;}
    static void posted(Context c,StatusBarNotification sbn){
        lockState(c);String app=sbn.getPackageName();observedApps.add(app);TickerSettings s=new TickerSettings(c);
        Notification n=sbn.getNotification();String key=sbn.getKey();
        if(!s.enabled()||!s.apps().contains(app)||app.equals(c.getPackageName())||
            (!s.bool("ongoing",false)&&(n.flags&Notification.FLAG_ONGOING_EVENT)!=0)||
            (!s.bool("groups",false)&&(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)){removed(key);return;}
        int max=Math.max(s.level(app,true),s.level(app,false));
        String title=max>=2?plain(n.extras.getCharSequence(Notification.EXTRA_TITLE)):"";
        String body="";
        if(max>=3){
            body=plain(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if(body.isEmpty())body=plain(n.extras.getCharSequence(Notification.EXTRA_TEXT));
            if(body.isEmpty()){CharSequence[] lines=n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null){StringBuilder b=new StringBuilder();for(CharSequence line:lines){if(b.length()>600)break;b.append(plain(line)).append(' ');}body=plain(b);}}
        }
        Entry old=entries.get(key);
        if(old!=null&&old.title.equals(title)&&old.body.equals(body))return;
        String label=app;try{label=plain(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(app,0)));}catch(Exception ignored){}
        if(old==current)current=null;
        Entry e=new Entry();e.key=key;e.app=app;e.label=label;e.title=title;e.body=body;
        e.posted=SystemClock.elapsedRealtime();e.next=e.posted;e.expires=e.posted+s.number("limit",30,1,180)*60000L;
        entries.remove(key);entries.put(key,e);
        while(entries.size()>50){String first=entries.keySet().iterator().next();removed(first);}
    }
    static void removed(String key){Entry e=entries.remove(key);if(current==e)current=null;}
    static void lockState(Context c){
        boolean next=((KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();
        if(knownLock&&locked&&!next&&new TickerSettings(c).mode()==1){
            for(Entry e:entries.values()){e.acknowledged=true;e.next=Long.MAX_VALUE;}
            current=null;gap=0;
        }
        knownLock=true;locked=next;
    }
    static Message present(Context c){
        TickerSettings s=new TickerSettings(c);lockState(c);
        if(!s.enabled()||!connected)return null;
        if(s.bool("only_locked",false)&&!locked)return null;
        long now=SystemClock.elapsedRealtime();
        entries.values().removeIf(e->now-e.posted>86400000L||!s.apps().contains(e.app));
        if(current!=null&&(!entries.containsKey(current.key)||current.acknowledged||s.mode()==3&&now>=current.expires))current=null;
        if(current!=null&&now>=until){
            Entry done=current;current=null;int mode=s.mode();
            done.next=mode==0||mode==1&&!locked?Long.MAX_VALUE:now+s.interval()*1000L;
            gap=now+s.pause()*1000L;
        }
        if(current==null&&now>=gap){
            for(Entry e:entries.values())if(!e.acknowledged&&e.next<=now&&(s.mode()!=3||now<e.expires)){current=e;started=now;until=now+s.duration()*1000L;break;}
        }
        return current==null?null:filtered(s,current,started);
    }
    private static Message filtered(TickerSettings s,Entry e,long start){
        int level=s.level(e.app,locked),count=0;for(Entry other:entries.values())if(other.app.equals(e.app))count++;
        String label=level==0?"":e.label,text=level==0?String.valueOf(count):level==1?e.label+" · "+I18n.get(R.string.ticker_new):e.label;
        if(level>=2&&!e.title.isEmpty())text+=" · "+e.title;
        if(level>=3&&!e.body.isEmpty())text+=" · "+e.body;
        return new Message(e.app,label,text,count,start);
    }
    static Message demo(){return new Message("android","MiniScreen Takeover",I18n.get(R.string.ticker_demo_text),2,DEMO_STARTED);}
    static JSONArray filteredQueue(Context c){
        JSONArray a=new JSONArray();TickerSettings s=new TickerSettings(c);lockState(c);
        if(!s.enabled()||!connected||s.bool("only_locked",false)&&!locked)return a;
        long now=SystemClock.elapsedRealtime();for(Entry e:entries.values())if(s.apps().contains(e.app)&&!e.acknowledged&&e.next!=Long.MAX_VALUE&&(s.mode()!=3||now<e.expires))a.put(filtered(s,e,started).json());return a;
    }
}
