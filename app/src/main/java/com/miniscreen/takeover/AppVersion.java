package com.miniscreen.takeover;
import android.content.Context;
final class AppVersion {static String name(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "?";}}}
