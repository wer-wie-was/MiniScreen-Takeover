package com.miniscreen.takeover;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.net.Uri;
import java.util.*;

/** Watchface settings run on display 0 without a Java bridge or file access. */
public final class PebbleConfigActivity extends Activity {
    private WebView web;private boolean returned;private String configId;
    private final PebbleSession.Observer observer=(frame,status)->{};
    @Override protected void onCreate(Bundle state){super.onCreate(state);PebbleSession.get(this).subscribe(observer,false);configId=getIntent().getStringExtra("configId");String url;try{url=PebbleConfigStore.read(this,configId);}catch(java.io.IOException e){finish();return;}
        if(!url.startsWith("data:")&&!TakeoverControl.prefs(this).getBoolean("pebble_network",false)){finish();return;}
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setBlockNetworkLoads(!TakeoverControl.prefs(this).getBoolean("pebble_network",false));web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);setContentView(web);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return navigation(request.getUrl());}});
        if(url.startsWith("data:text/html")){try{int comma=url.indexOf(',');String html=url.substring(0,comma).contains(";base64")?new String(android.util.Base64.decode(url.substring(comma+1),0),java.nio.charset.StandardCharsets.UTF_8):Uri.decode(url.substring(comma+1));html=html.replace("pebblejs://close","https://miniscreen-config.invalid/close");web.loadDataWithBaseURL("https://miniscreen-config.invalid/",html,"text/html","UTF-8",null);}catch(RuntimeException e){finish();}}
        else if(url.startsWith("https://")||url.startsWith("http://"))web.loadUrl(url+(url.contains("?")?"&":"?")+"return_to="+Uri.encode("pebblejs://close#"));else finish();
    }
    private boolean navigation(Uri uri){if("pebblejs".equals(uri.getScheme())||"miniscreen-config.invalid".equals(uri.getHost())&&"/close".equals(uri.getPath())){returned=true;PebbleSession.get(this).closeConfig(uri.getEncodedFragment()==null?"":uri.getEncodedFragment());finish();return true;}return !Arrays.asList("http","https").contains(uri.getScheme());}
    @Override protected void onDestroy(){if(!isChangingConfigurations()){if(!returned)PebbleSession.get(this).closeConfig("");PebbleConfigStore.remove(this,configId);}if(web!=null)web.destroy();PebbleSession.get(this).unsubscribe(observer);super.onDestroy();}
}
