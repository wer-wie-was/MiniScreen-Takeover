package com.miniscreen.takeover;
import android.app.job.*;
import android.os.*;
public final class UpdateJob extends JobService {
 private boolean stopped;
 @Override public boolean onStartJob(JobParameters p){stopped=false;if(!AppUpdates.prefs(this).getBoolean("update_auto",true))return false;AppUpdates.check(this,false,()->new Handler(Looper.getMainLooper()).post(()->{if(!stopped)jobFinished(p,false);}));return true;}
 @Override public boolean onStopJob(JobParameters p){stopped=true;return true;}
}
