package com.miniscreen.takeover;

import android.content.pm.PackageInfo;
import android.webkit.WebView;
import androidx.webkit.Profile;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;

/** Grants shared WASM memory only to our loopback engine, in the isolated :pebble process. */
final class PebbleWebView {
    private Profile profile;
    boolean enable(WebView view,String origin){
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.CROSS_ORIGIN_ISOLATED_ALLOWLIST))return false;
        WebViewCompat.setProfile(view,"miniscreen-pebble");
        profile=WebViewCompat.getProfile(view);
        profile.setCrossOriginIsolatedAllowlist(Collections.singleton(origin));
        return true;
    }
    void clear(){
        if(profile!=null){try{profile.setCrossOriginIsolatedAllowlist(Collections.emptySet());}catch(RuntimeException ignored){}profile=null;}
    }
    static String version(){
        PackageInfo info=WebView.getCurrentWebViewPackage();
        return info==null?"?":info.packageName+" "+info.versionName;
    }
}
