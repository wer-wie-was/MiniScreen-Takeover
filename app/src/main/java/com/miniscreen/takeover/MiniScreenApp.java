package com.miniscreen.takeover;

import android.app.Application;

public final class MiniScreenApp extends Application {
    @Override public void onCreate(){super.onCreate();if(android.os.Build.VERSION.SDK_INT>=28&&Application.getProcessName().endsWith(":pebble"))android.webkit.WebView.setDataDirectorySuffix("pebble");I18n.init(this);if(mainProcess()){MusicControl.init(this);AppUpdates.schedule(this);}}
    private boolean mainProcess(){
        if(android.os.Build.VERSION.SDK_INT>=28)return getPackageName().equals(Application.getProcessName());
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
        if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo process:processes)
            if(process.pid==android.os.Process.myPid())return getPackageName().equals(process.processName);
        return false;
    }
}
