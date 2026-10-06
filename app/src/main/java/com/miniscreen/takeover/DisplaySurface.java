package com.miniscreen.takeover;

import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared renderer for the live preview and the rear activity. No JavaScript-to-Java bridge. */
final class DisplaySurface extends FrameLayout {
    private static final String ORIGIN="https://miniscreen.local";
    private final ProfileStore store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ClockView clock;
    private WebView web;
    private ChargeView charge;
    private TickerView notificationView;
    private NotificationTicker.Message notification;
    private boolean tickerDemo;
    void simulateTicker(boolean value){tickerDemo=preview&&value;tick();}
    private boolean plugged,simulateCharge;
    private int batteryLevel;
    private Config config;
    private String profile="",loadedDesign="",loadedMode="",loadedImage="",loadedFont="";
    private File designRoot;
    private boolean ready,running,disposed;
    private final boolean preview;
    private final Runnable ticker=()->tick();
    private final Runnable motionFrame=new Runnable(){@Override public void run(){
        if(!running||disposed||config==null)return;
        boolean moving=clock!=null&&!config.image.isEmpty()&&config.imageFit.equals("window")&&config.windowMotion;
        if(moving)clock.invalidate();if(charge!=null&&charge.animated())charge.invalidate();
        if(moving||(charge!=null&&charge.animated()))handler.postDelayed(this,33);
    }};
    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){readBattery(i);tick();}};
    private void readBattery(Intent i){if(i==null)return;int max=i.getIntExtra(BatteryManager.EXTRA_SCALE,0),level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);plugged=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0&&max>0&&level>=0;batteryLevel=max>0?Config.clamp(Math.round(level*100f/max),0,100):0;updateBattery();}
    private void updateBattery(){if(charge!=null)charge.battery(preview&&simulateCharge||plugged,preview&&simulateCharge?65:batteryLevel);}
    void simulateCharge(boolean enabled){simulateCharge=preview&&enabled;updateBattery();tick();}
    private final BroadcastReceiver timeReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){tick();}};
    DisplaySurface(Context c,boolean preview) {super(c);this.preview=preview;store=new ProfileStore(c);setClipChildren(true);setClipToPadding(true);readBattery(c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));}
    void apply(Config c) {
        if(disposed)return;String id=store.active();
        boolean rebuild=!id.equals(profile)||!c.mode.equals(loadedMode)||!c.design.equals(loadedDesign)
            ||(c.mode.equals("html")&&web==null)
            ||(c.mode.equals("html")&&(!c.image.equals(loadedImage)||!c.fontFile.equals(loadedFont)));
        boolean reloadAssets=rebuild||!c.image.equals(loadedImage)||!c.fontFile.equals(loadedFont);
        config=c;profile=id;loadedDesign=c.design;loadedMode=c.mode;loadedImage=c.image;loadedFont=c.fontFile;
        setBackgroundColor(Color.parseColor(c.backgroundColor));
        if(rebuild) {
            releaseWeb();removeAllViews();clock=null;ready=false;
            if(c.mode.equals("html")&&!c.design.isEmpty()) createWeb();
            else {clock=new ClockView(getContext());addView(clock,new LayoutParams(-1,-1));}
            charge=new ChargeView(getContext());addView(charge,new LayoutParams(-1,-1));
            notificationView=new TickerView(getContext());addView(notificationView,new LayoutParams(-1,-1));
        }
        if(charge!=null){charge.configure(c,store.dir(profile));updateBattery();charge.bringToFront();}
        if(clock!=null) {
            // Colors, sizing and typefaces are applied with the current profile. Image decoding is cached separately below.
            clock.configure(c,store.dir(profile));
        }
        if(web!=null&&reloadAssets&&!rebuild)web.reload();
        if(notificationView!=null)notificationView.bringToFront();updateWebLayout();tick();
    }
    private void createWeb() {
        try {
            File entry=store.resolve(profile,config.design);
            String top=config.design.substring(0,config.design.indexOf('/'));
            designRoot=store.resolve(profile,top).getCanonicalFile();
            final File root=designRoot;final File profileFolder=store.dir(profile);final String imagePath=config.image,fontPath=config.fontFile;
            web=new WebView(getContext());web.setBackgroundColor(Color.TRANSPARENT);
            WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
            s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setBlockNetworkLoads(true);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setDomStorageEnabled(false);s.setDatabaseEnabled(false);
            s.setMediaPlaybackRequiresUserGesture(true);s.setSupportZoom(false);s.setTextZoom(100);
            web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            web.setVerticalScrollBarEnabled(false);web.setHorizontalScrollBarEnabled(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request) {
                    Uri uri=request.getUrl();
                    try {
                        if(!"https".equals(uri.getScheme())||!"miniscreen.local".equals(uri.getHost()))return error(403,"Forbidden");
                        String path=uri.getPath();File file;
                        if(path!=null&&path.startsWith("/ticker/icon/")){
                            String app=path.substring(13);TickerSettings ts=new TickerSettings(getContext());
                            if(!ts.html()||(!preview&&(!ts.enabled()||!ts.apps().contains(app)))||preview&&!app.equals("android"))return error(403,"Forbidden");
                            android.graphics.drawable.Drawable icon=getContext().getPackageManager().getApplicationIcon(app);
                            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
                            icon.setBounds(0,0,64,64);icon.draw(new android.graphics.Canvas(bitmap));
                            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
                            return new WebResourceResponse("image/png",null,new ByteArrayInputStream(bytes.toByteArray()));
                        }
                        if("/shared/background".equals(path)&&!imagePath.isEmpty())file=new File(profileFolder,imagePath);
                        else if("/shared/font".equals(path)&&!fontPath.isEmpty())file=new File(profileFolder,fontPath);
                        else {
                            if(path==null||!path.startsWith("/design/"))return error(404,"Not Found");
                            file=new File(root,path.substring(8)).getCanonicalFile();
                            if(!file.getPath().startsWith(root.getPath()+File.separator))return error(403,"Forbidden");
                        }
                        if(!file.isFile())return error(404,"Not Found");String mime=mime(file);
                        InputStream in;
                        if(mime.equals("text/html")) {
                            String html=ProfileStore.readText(file,1024*1024);
                            // Enforce CSP before any user-supplied resource, including on older WebViews.
                            html="<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\""+csp()+"\">"+html;
                            in=new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
                        }else in=new FileInputStream(file);
                        Map<String,String> headers=new HashMap<>();headers.put("Content-Security-Policy",csp());headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");
                        return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,in);
                    }catch(Exception e){return error(404,"Not Found");}
                }
                @Override public void onPageFinished(WebView v,String url){ready=true;tick();}
                @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())report(I18n.get(R.string.web_load_failed)+e.getDescription());}
                @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {report(I18n.get(R.string.msg_158));ready=false;if(web==v){removeView(v);web=null;}v.destroy();return true;}
            });
            addView(web,new LayoutParams(-1,-1));
            String rel=root.toPath().relativize(entry.toPath()).toString().replace(File.separatorChar,'/');
            web.loadUrl(ORIGIN+"/design/"+Uri.encode(rel,"/"));
        }catch(Exception e){report(I18n.get(R.string.msg_159)+e.getMessage());clock=new ClockView(getContext());addView(clock,new LayoutParams(-1,-1));}
    }
    private void report(String message){if(!preview)store.prefs.edit().putString("status",message).apply();}
    private static String csp(){return "default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; connect-src 'self'; frame-src 'none'; object-src 'none'; worker-src 'none'; base-uri 'self'; form-action 'none'";}
    private static WebResourceResponse error(int code,String reason){return new WebResourceResponse("text/plain","UTF-8",code,reason,Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private static String mime(File f) {
        String n=f.getName().toLowerCase(Locale.ROOT);String ext=n.substring(n.lastIndexOf('.')+1);
        switch(ext){case "html":case "htm":return "text/html";case "css":return "text/css";case "js":case "mjs":return "application/javascript";case "json":return "application/json";case "svg":return "image/svg+xml";case "ttf":return "font/ttf";case "otf":return "font/otf";case "woff":return "font/woff";case "woff2":return "font/woff2";}
        // Imported images use a generated .img suffix; sniff supported bitmap headers.
        if(ext.equals("img")){android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getPath(),o);if(o.outMimeType!=null)return o.outMimeType;}
        String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return mime==null?"application/octet-stream":mime;
    }
    private float scale(){return Math.min(getWidth(),getHeight())/340f;}
    private void updateWebLayout(){if(web==null||config==null)return;int margin=config.shifting?Math.round(config.shiftRange*scale()):0;LayoutParams lp=new LayoutParams(Math.max(1,getWidth()-2*margin),Math.max(1,getHeight()-2*margin));lp.leftMargin=margin;lp.topMargin=margin;web.setLayoutParams(lp);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(notificationView!=null)notificationView.bringToFront();updateWebLayout();tick();}
    static float[] offset(Config c,long now) {
        if(!c.shifting)return new float[]{0,0};
        long step=now/(c.shiftInterval*1000L);int n=c.shiftRange*2+1;
        // Consecutive steps move in both axes and cover the entire configured span.
        return new float[]{(int)Math.floorMod(step,n)-c.shiftRange,(int)Math.floorMod(step*2+3,n)-c.shiftRange};
    }
    void tick() {
        handler.removeCallbacks(ticker);if(config==null||disposed)return;
        long now=System.currentTimeMillis();float[] offset=offset(config,now);float dx=offset[0]*scale(),dy=offset[1]*scale();
        if(clock!=null)clock.shift(dx,dy);if(charge!=null)charge.shift(dx,dy);
        TickerSettings ts=new TickerSettings(getContext());
        notification=preview?(tickerDemo?NotificationTicker.demo():null):(running?NotificationTicker.present(getContext()):null);
        boolean ownHtml=web!=null&&ts.html();
        if(notificationView!=null)notificationView.update(ownHtml?null:notification,config,store.dir(profile),dx,dy,running);
        float alpha=notification==null?1:ts.number("clock",0,0,2)==1?.25f:ts.number("clock",0,0,2)==2?0:1;
        if(clock!=null)clock.notificationAlpha(alpha);if(web!=null)web.setAlpha(ownHtml?1:alpha);
        if(web!=null){web.setTranslationX(dx);web.setTranslationY(dy);if(ready)pushData(now,offset);}
        handler.removeCallbacks(motionFrame);if(running)handler.post(motionFrame);
        if(running){long refresh=(ts.enabled()||tickerDemo)?250:config.seconds?1000:60000;long next=refresh-now%refresh;
            if(config.shifting)next=Math.min(next,config.shiftInterval*1000L-now%(config.shiftInterval*1000L));handler.postDelayed(ticker,Math.max(30,next));}
    }
    private void pushData(long now,float[] offset) {
        try {
            JSONObject d=new JSONObject();Date date=new Date(now);
            d.put("epochMs",now).put("time",config.time(date)).put("date",config.showDate?config.date(date):"")
             .put("showDate",config.showDate).put("width",getWidth()).put("height",getHeight())
             .put("cssWidth",web.getWidth()/getResources().getDisplayMetrics().density).put("cssHeight",web.getHeight()/getResources().getDisplayMetrics().density)
             .put("timeZone",TimeZone.getDefault().getID()).put("locale",I18n.locale().toLanguageTag())
             .put("preview",preview).put("running",running).put("batteryPlugged",preview&&simulateCharge||plugged).put("batteryLevel",preview&&simulateCharge?65:batteryLevel).put("shiftX",offset[0]).put("shiftY",offset[1]).put("settings",config.json())
             .put("backgroundUrl",config.image.isEmpty()?"":ORIGIN+"/shared/background").put("fontUrl",config.fontFile.isEmpty()?"":ORIGIN+"/shared/font");
            TickerSettings ts=new TickerSettings(getContext());
            boolean deliver=ts.html()&&running;
            d.put("notification",deliver&&notification!=null?notification.json():JSONObject.NULL)
             .put("tickerSettings",ts.json())
             .put("notifications",deliver?(preview?(notification==null?new org.json.JSONArray():new org.json.JSONArray().put(notification.json())):NotificationTicker.filteredQueue(getContext())):new org.json.JSONArray());
            String js="(function(d){window.miniScreen=d;var s=document.documentElement.style;s.setProperty('--time-color',d.settings.timeColor);s.setProperty('--date-color',d.settings.dateColor);s.setProperty('--background-color',d.settings.backgroundColor);window.dispatchEvent(new CustomEvent('miniscreen:update',{detail:d}));})("+d.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029")+");";
            web.evaluateJavascript(js,null);
        }catch(Exception e){report(I18n.get(R.string.msg_160)+e.getMessage());}
    }
    void start() {if(running||disposed)return;running=true;IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);f.addAction(Intent.ACTION_DATE_CHANGED);if(Build.VERSION.SDK_INT>=33){getContext().registerReceiver(timeReceiver,f,Context.RECEIVER_NOT_EXPORTED);getContext().registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED),Context.RECEIVER_NOT_EXPORTED);}else{getContext().registerReceiver(timeReceiver,f);getContext().registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}if(web!=null)web.onResume();tick();}
    void stop() {handler.removeCallbacks(ticker);handler.removeCallbacks(motionFrame);if(running){running=false;getContext().unregisterReceiver(timeReceiver);getContext().unregisterReceiver(batteryReceiver);}notification=null;if(notificationView!=null)notificationView.stop();if(clock!=null)clock.notificationAlpha(1);if(web!=null){web.setAlpha(1);if(ready&&config!=null)pushData(System.currentTimeMillis(),offset(config,System.currentTimeMillis()));web.onPause();}}
    void dispose(){stop();disposed=true;releaseWeb();removeAllViews();}
    private void releaseWeb(){if(web!=null){removeView(web);web.stopLoading();web.destroy();web=null;}}
    @Override public boolean dispatchTouchEvent(MotionEvent e){return true;}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return true;}
}
