package com.miniscreen.takeover;
import android.content.Context;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.health.connect.datatypes.units.*;
import android.os.OutcomeReceiver;
import java.time.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Daily distance and active energy, read only and only after explicit permission. */
final class WfzHealth {
    static final String DISTANCE="android.permission.health.READ_DISTANCE",CALORIES="android.permission.health.READ_ACTIVE_CALORIES_BURNED";
    static void read(Context c,boolean foreground,Executor executor,Consumer<JSONObject> complete){
        if(!PebbleHealth.available(c)||!foreground&&(!TakeoverControl.prefs(c).getBoolean("pebble_health_background",false)||!PebbleHealth.granted(c,PebbleHealth.BACKGROUND))){complete.accept(new JSONObject());return;}
        PebbleHealth.read(c,foreground,executor,result->Api.distance(c,executor,result,complete));
    }
    @android.annotation.TargetApi(34)
    private static final class Api {
        static TimeInstantRangeFilter range(){return new TimeInstantRangeFilter.Builder().setStartTime(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()).setEndTime(Instant.now()).build();}
        static void distance(Context c,Executor executor,JSONObject result,Consumer<JSONObject> complete){
            if(!TakeoverControl.prefs(c).getBoolean("wfz_health_distance",false)||!PebbleHealth.granted(c,DISTANCE)){calories(c,executor,result,complete);return;}
            HealthConnectManager m=(HealthConnectManager)c.getSystemService("healthconnect");
            final long start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond(),time=Instant.now().getEpochSecond();
            AggregateRecordsRequest<Length> req=new AggregateRecordsRequest.Builder<Length>(range()).addAggregationType(DistanceRecord.DISTANCE_TOTAL).build();
            try{m.aggregate(req,executor,new OutcomeReceiver<AggregateRecordsResponse<Length>,HealthConnectException>(){
                public void onResult(AggregateRecordsResponse<Length> data){try{Length v=data.get(DistanceRecord.DISTANCE_TOTAL);if(v!=null)result.put("distanceKm",v.getInMeters()/1000).put("distanceTime",time).put("distanceDay",start);}catch(Exception ignored){}calories(c,executor,result,complete);}
                public void onError(HealthConnectException error){calories(c,executor,result,complete);}
            });}catch(RuntimeException e){calories(c,executor,result,complete);}
        }
        static void calories(Context c,Executor executor,JSONObject result,Consumer<JSONObject> complete){
            if(!TakeoverControl.prefs(c).getBoolean("wfz_health_calories",false)||!PebbleHealth.granted(c,CALORIES)){complete.accept(result);return;}
            HealthConnectManager m=(HealthConnectManager)c.getSystemService("healthconnect");
            final long start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond(),time=Instant.now().getEpochSecond();
            AggregateRecordsRequest<Energy> req=new AggregateRecordsRequest.Builder<Energy>(range()).addAggregationType(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL).build();
            try{m.aggregate(req,executor,new OutcomeReceiver<AggregateRecordsResponse<Energy>,HealthConnectException>(){
                public void onResult(AggregateRecordsResponse<Energy> data){try{Energy v=data.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);if(v!=null)result.put("activeCalories",Math.round(v.getInCalories()/1000.0)).put("caloriesTime",time).put("caloriesDay",start);}catch(Exception ignored){}complete.accept(result);}
                public void onError(HealthConnectException error){complete.accept(result);}
            });}catch(RuntimeException e){complete.accept(result);}
        }
    }
}
