package com.miniscreen.takeover;

import android.app.Application;

public final class MiniScreenApp extends Application {
    @Override public void onCreate(){super.onCreate();I18n.init(this);}
}
