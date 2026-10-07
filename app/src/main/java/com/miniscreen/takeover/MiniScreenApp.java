package com.miniscreen.takeover;

import android.app.Application;

public final class MiniScreenApp extends Application {
    @Override public void onCreate(){super.onCreate();if(android.os.Build.VERSION.SDK_INT>=28&&Application.getProcessName().endsWith(":pebble"))android.webkit.WebView.setDataDirectorySuffix("pebble");I18n.init(this);}
}
