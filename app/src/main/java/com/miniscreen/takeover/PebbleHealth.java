package com.miniscreen.takeover;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.health.connect.*;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.HeartRateRecord;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Read-only platform Health Connect adapter. No records or values are persisted to preferences. */
final class PebbleHealth {
    static final String STEPS="android.permission.health.READ_STEPS",HEART="android.permission.health.READ_HEART_RATE",BACKGROUND="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND";
    static boolean available(Context c){return Build.VERSION.SDK_INT>=34&&c.getSystemService("healthconnect")!=null;}
    static boolean granted(Context c,String permission){return c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    static boolean backgroundAvailable(Context c){try{c.getPackageManager().getPermissionInfo(BACKGROUND,0);return available(c);}catch(PackageManager.NameNotFoundException e){return false;}}
    static void request(Activity a,boolean background){List<String> permissions=new ArrayList<>();SharedPreferences p=TakeoverControl.prefs(a);if(p.getBoolean("pebble_health_steps",false))permissions.add(STEPS);if(p.getBoolean("pebble_health_heart",false))permissions.add(HEART);if(p.getBoolean("wfz_health_distance",false))permissions.add(WfzHealth.DISTANCE);if(p.getBoolean("wfz_health_calories",false))permissions.add(WfzHealth.CALORIES);if(background&&backgroundAvailable(a)&&!permissions.isEmpty())permissions.add(BACKGROUND);if(!permissions.isEmpty()&&available(a))a.requestPermissions(permissions.toArray(new String[0]),91);}
    static void read(Context c,boolean foreground,Executor executor,Consumer<JSONObject> complete){
        if(!available(c)||!foreground&&!granted(c,BACKGROUND)){complete.accept(new JSONObject());return;}
        Api.read(c,executor,complete);
    }
    @android.annotation.TargetApi(34)
    private static final class Api {
        static void read(Context c,Executor executor,Consumer<JSONObject> complete){
            HealthConnectManager manager=(HealthConnectManager)c.getSystemService("healthconnect");SharedPreferences p=TakeoverControl.prefs(c);
            Instant now=Instant.now(),start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();JSONObject result=new JSONObject();
            Runnable heart=()->readHeart(c,manager,executor,start,now,result,complete);
            if(!p.getBoolean("pebble_health_steps",false)||!granted(c,STEPS)){heart.run();return;}
            AggregateRecordsRequest<Long> req=new AggregateRecordsRequest.Builder<Long>(new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(now).build()).addAggregationType(StepsRecord.STEPS_COUNT_TOTAL).build();
            try{manager.aggregate(req,executor,new OutcomeReceiver<AggregateRecordsResponse<Long>,HealthConnectException>(){
                public void onResult(AggregateRecordsResponse<Long> data){try{Long n=data.get(StepsRecord.STEPS_COUNT_TOTAL);if(n!=null)result.put("steps",n).put("dayStart",start.getEpochSecond()).put("stepsTime",now.getEpochSecond());}catch(JSONException ignored){}heart.run();}
                public void onError(HealthConnectException error){heart.run();}
            });}catch(RuntimeException e){heart.run();}
        }
        static void readHeart(Context c,HealthConnectManager manager,Executor executor,Instant start,Instant now,JSONObject result,Consumer<JSONObject> complete){
            if(!TakeoverControl.prefs(c).getBoolean("pebble_health_heart",false)||!granted(c,HEART)){complete.accept(result);return;}
            int minutes=Config.clamp(TakeoverControl.prefs(c).getInt("pebble_health_max_age",15),1,120);Instant cutoff=now.minusSeconds(minutes*60L);
            readPage(manager,executor,cutoff,now,-1,0,0,0,result,complete);
        }
        static void readPage(HealthConnectManager manager,Executor executor,Instant start,Instant now,long token,int pages,long latest,long bpm,JSONObject result,Consumer<JSONObject> complete){
            ReadRecordsRequestUsingFilters.Builder<HeartRateRecord> b=new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class).setTimeRangeFilter(new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(now).build()).setPageSize(1000).setAscending(false);if(token!=-1)b.setPageToken(token);
            try{manager.readRecords(b.build(),executor,new OutcomeReceiver<ReadRecordsResponse<HeartRateRecord>,HealthConnectException>(){
                public void onResult(ReadRecordsResponse<HeartRateRecord> records){long time=latest,value=bpm;for(HeartRateRecord r:records.getRecords())for(HeartRateRecord.HeartRateSample s:r.getSamples()){long epoch=s.getTime().getEpochSecond();if(epoch>=start.getEpochSecond()&&epoch<=now.getEpochSecond()&&epoch>time){time=epoch;value=s.getBeatsPerMinute();}}
                    if(records.getNextPageToken()!=-1&&pages<20){readPage(manager,executor,start,now,records.getNextPageToken(),pages+1,time,value,result,complete);return;}
                    try{if(time>0&&value>0)result.put("heartRate",value).put("heartTime",time);}catch(JSONException ignored){}complete.accept(result);
                }
                public void onError(HealthConnectException e){complete.accept(result);}
            });}catch(RuntimeException e){complete.accept(result);}
        }
    }
}
