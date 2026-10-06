package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import java.util.*;

/** Device-local weekly windows. A selected weekday owns its start, including overnight windows. */
final class DisplaySchedule {
    static final String ALARM="com.miniscreen.takeover.SCHEDULE";
    static final class Edge {
        final long time;final boolean on;
        Edge(long time,boolean on){this.time=time;this.on=on;}
    }
    static boolean enabled(SharedPreferences p){return p.getBoolean("schedule_enabled",false);}
    private static int minute(SharedPreferences p,String key,int fallback){return Config.clamp(p.getInt(key,fallback),0,1439);}
    static boolean valid(SharedPreferences p){return (p.getInt("schedule_days",127)&127)!=0&&minute(p,"schedule_on",420)!=minute(p,"schedule_off",1380);}
    private static List<Edge> edges(SharedPreferences p,long now){
        ZoneId zone=ZoneId.systemDefault();LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        int on=minute(p,"schedule_on",420),off=minute(p,"schedule_off",1380),days=p.getInt("schedule_days",127)&127;
        List<Edge> result=new ArrayList<>();if(on==off)return result;
        for(int i=-1;i<=8;i++){
            LocalDate day=today.plusDays(i);if((days&(1<<(day.getDayOfWeek().getValue()-1)))==0)continue;
            long start=day.atTime(on/60,on%60).atZone(zone).toInstant().toEpochMilli();
            LocalDate endDay=off<on?day.plusDays(1):day;
            long end=endDay.atTime(off/60,off%60).atZone(zone).toInstant().toEpochMilli();
            // A DST gap can collapse a very short window; it must not become an inverted window.
            if(end<=start)continue;
            result.add(new Edge(start,true));result.add(new Edge(end,false));
        }
        result.sort((a,b)->Long.compare(a.time,b.time));return result;
    }
    static boolean inside(SharedPreferences p,long now){
        if(!valid(p))return false;Edge latest=null;
        for(Edge edge:edges(p,now)){if(edge.time>now)break;latest=edge;}
        return latest!=null&&latest.on;
    }
    static Edge next(SharedPreferences p,long now,Boolean onlyOn){
        for(Edge e:edges(p,now))if(e.time>now&&(onlyOn==null||e.on==onlyOn.booleanValue()))return e;
        return null;
    }
    static boolean manualOff(SharedPreferences p,long now){return p.getBoolean("manual_off",false)&&now<p.getLong("manual_off_until",Long.MAX_VALUE);}
    static boolean allowed(SharedPreferences p,long now){return !enabled(p)||inside(p,now)||now<p.getLong("schedule_override_until",0);}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    private static PendingIntent pending(Context c){return PendingIntent.getBroadcast(c,40,new Intent(c,ScheduleReceiver.class).setAction(ALARM),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void cancel(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending(c));}
    static void plan(Context c){
        SharedPreferences p=TakeoverControl.prefs(c);AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);cancel(c);
        if(!enabled(p)||!valid(p))return;
        long now=System.currentTimeMillis();Edge edge=next(p,now,null);if(edge==null)return;
        long when=edge.time,until=p.getLong("pause_until",0);
        // A finite pause also gets a reevaluation alarm; indefinite pauses retain the next window edge.
        if(until>now&&until!=Long.MAX_VALUE)when=Math.min(when,until);
        try{if(exact(c))alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c));else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c));}
        catch(SecurityException e){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c));}
    }
    static void configure(Context c,boolean enabled){
        SharedPreferences p=TakeoverControl.prefs(c);
        p.edit().putBoolean("schedule_enabled",enabled).putLong("power_generation",p.getLong("power_generation",0)+1).putLong("schedule_override_until",0).putLong("schedule_applied_start",0).apply();
        if(p.getBoolean("manual_off",false)){Edge edge=enabled?next(p,System.currentTimeMillis(),true):null;p.edit().putLong("manual_off_until",edge==null?Long.MAX_VALUE:edge.time).apply();}
        if(enabled){p.edit().putBoolean("enabled",true).apply();reconcile(c);}else{cancel(c);TakeoverControl.sync(c);}
    }
    static void timeChanged(Context c){
        SharedPreferences p=TakeoverControl.prefs(c);long now=System.currentTimeMillis();SharedPreferences.Editor edit=p.edit().putLong("schedule_applied_start",0);
        if(enabled(p)&&p.getBoolean("manual_off",false)){Edge edge=next(p,now,true);edit.putLong("manual_off_until",edge==null?Long.MAX_VALUE:edge.time);}
        if(p.getLong("schedule_override_until",0)>0){Edge edge=next(p,now,false);edit.putLong("schedule_override_until",edge==null?0:edge.time);}
        edit.apply();reconcile(c);
    }
    static void reconcile(Context c){
        SharedPreferences p=TakeoverControl.prefs(c);plan(c);
        if(!enabled(p)||!valid(p)||!p.getBoolean("enabled",false))return;
        long now=System.currentTimeMillis();
        if(TakeoverControl.paused(p)||p.getBoolean("external_active",false))return;
        if(manualOff(p,now))return;
        if(p.getBoolean("manual_off",false))p.edit().putBoolean("manual_off",false).apply();
        if(!allowed(p,now)){DisplayPower.off(c,false);return;}
        long start=0;for(Edge edge:edges(p,now)){if(edge.time>now)break;if(edge.on)start=edge.time;}
        boolean newWindow=start>p.getLong("schedule_applied_start",0);
        if(newWindow){p.edit().putLong("schedule_applied_start",start).putLong("power_generation",p.getLong("power_generation",0)+1).putBoolean("yielded",false).apply();TakeoverControl.start(c,TakeoverControl.RESTORE);}
        else if(!TakeoverControl.mode(p).equals("manual"))TakeoverControl.sync(c);
    }
}
