package com.miniscreen.takeover;

import android.content.*;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.pm.*;
import android.os.*;
import android.widget.Toast;
import java.util.*;

/** Only normal, exported launcher activities; uses the same tested manufacturer gateway. */
final class ExternalAppLauncher {
    static final class Entry {
        final ComponentName component;final String label;
        Entry(ComponentName component,String label){this.component=component;this.label=label;}
        @Override public String toString(){return label+" · "+component.getPackageName();}
    }
    static List<Entry> apps(Context c){
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=c.getPackageManager();List<Entry> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ResolveInfo info:pm.queryIntentActivities(intent,0)){
            ActivityInfo a=info.activityInfo;if(a==null||!a.exported||!a.enabled||!a.applicationInfo.enabled||a.packageName.equals(c.getPackageName()))continue;
            ComponentName component=new ComponentName(a.packageName,a.name);if(!seen.add(component.flattenToString()))continue;
            result.add(new Entry(component,info.loadLabel(pm).toString()));
        }
        result.sort((a,b)->a.label.compareToIgnoreCase(b.label));return result;
    }
    static void returnToTakeover(Activity activity){
        Context c=activity.getApplicationContext();SharedPreferences p=TakeoverControl.prefs(c);
        ComponentName selected=ComponentName.unflattenFromString(p.getString("external_activity",""));
        if(!p.getBoolean("return_external_primary",false)||!p.getBoolean("external_active",false)||selected==null){TakeoverControl.show(c);return;}
        long generation=p.getLong("power_generation",0)+1;p.edit().putLong("power_generation",generation).apply();
        try{
            // NEW_TASK can reuse the app's task, but the target app may create a separate instance.
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(selected).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(launch,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
            Toast.makeText(c,I18n.get(R.string.return_requested),Toast.LENGTH_LONG).show();
        }catch(RuntimeException e){Toast.makeText(c,I18n.get(R.string.return_failed)+e.getMessage(),Toast.LENGTH_LONG).show();}
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            if(p.getLong("power_generation",0)!=generation)return;
            // Bring our controls back to display 0. The foreign app remains alive in the background.
            try{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());}
            catch(RuntimeException e){Toast.makeText(c,I18n.get(R.string.return_failed)+e.getMessage(),Toast.LENGTH_LONG).show();}
            TakeoverControl.show(c);
        },700);
    }
    static void launch(Context context,Entry entry){
        Context c=context.getApplicationContext();
        try{ActivityInfo info=c.getPackageManager().getActivityInfo(entry.component,0);if(!info.exported||!info.enabled||!info.applicationInfo.enabled)throw new IllegalArgumentException(I18n.get(R.string.external_unavailable));
            // Retain the paused state until the user explicitly resumes or starts our clock again.
            SharedPreferences p=TakeoverControl.prefs(c);long generation=p.getLong("power_generation",0)+1;
            p.edit().putBoolean("external_active",true).putBoolean("manual_off",false).putBoolean("yielded",true).putLong("power_generation",generation).apply();
            TakeoverControl.pause(c,0);WatchService.suspendNow();ScreenActivity.closeScreen();
            c.sendBroadcast(new Intent("com.yft.miniscreen.action.GC_SCREEN_ON").setPackage("com.yft.miniscreendisplay").putExtra("HALL_CLOSE",false));
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if(!p.getBoolean("external_active",false)||p.getLong("power_generation",0)!=generation)return;
                try{c.sendBroadcast(new Intent("com.yft.miniscreen.action.START_ACTIVITY").setPackage("com.yft.miniscreendisplay").putExtra("activity",entry.component.flattenToString()));Toast.makeText(c,I18n.get(R.string.external_requested),Toast.LENGTH_LONG).show();}
                catch(RuntimeException e){Toast.makeText(c,I18n.get(R.string.external_failed)+e.getMessage(),Toast.LENGTH_LONG).show();}
            },700);
        }catch(PackageManager.NameNotFoundException|RuntimeException e){Toast.makeText(c,I18n.get(R.string.external_failed)+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
