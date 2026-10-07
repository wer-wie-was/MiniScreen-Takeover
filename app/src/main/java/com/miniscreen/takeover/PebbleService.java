package com.miniscreen.takeover;

import android.app.Service;
import android.content.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** One QEMU instance in a separate Android process; clients share frames over bounded IPC. */
public final class PebbleService extends Service implements PebbleServer.Events {
    static final int CONNECT=1,DISCONNECT=2,COMMAND=3,SETTINGS=4,FRAME=5,STATUS=6,CONFIG=7,RESTART=8;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<IBinder,Messenger> clients=new HashMap<>();private PebbleServer server;private WebView engine;
    private final PebbleWebView isolation=new PebbleWebView();
    private String board="",status="",options="";private long revision=-1;private byte[] lastFrame;private String restartToken;
    private final Runnable restartTimeout=()->finishRestart(restartToken);
    private final Runnable shutdown=()->{if(clients.isEmpty()){closeEngine();stopSelf();}};
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),msg->{
        if(msg.sendingUid!=-1&&msg.sendingUid!=android.os.Process.myUid())return true;
        if(msg.what==CONNECT&&msg.replyTo!=null){main.removeCallbacks(shutdown);clients.put(msg.replyTo.getBinder(),msg.replyTo);Bundle b=new Bundle();b.putString("status",status);send(msg.replyTo,STATUS,b);if(lastFrame!=null){Bundle f=new Bundle();f.putByteArray("png",lastFrame);send(msg.replyTo,FRAME,f);}}
        else if(msg.what==DISCONNECT&&msg.replyTo!=null){clients.remove(msg.replyTo.getBinder());if(clients.isEmpty()){if(server!=null)server.command(command("persist"));main.postDelayed(shutdown,5000);}}
        else if(msg.what==SETTINGS){try{JSONObject settings=new JSONObject(msg.getData().getString("json","{}"));String next=settings.optString("platform","gabbro");long nextRevision=settings.optLong("revision");options=settings.toString();if(server!=null)server.network=settings.optBoolean("network",false);if(!next.equals(board)||nextRevision!=revision||engine==null){board=next;revision=nextRevision;restart(settings);}else if(server!=null)server.command(new JSONObject().put("type","settings").put("value",settings));}catch(Exception e){status("settings_error");}}
        else if(msg.what==RESTART){requestRestart();}
        else if(msg.what==COMMAND&&server!=null)try{JSONObject cmd=new JSONObject(msg.getData().getString("json","{}"));if("configClosedFile".equals(cmd.optString("type"))){String id=cmd.optString("value");try{cmd.put("type","configClosed").put("value",PebbleConfigStore.read(this,id));}finally{PebbleConfigStore.remove(this,id);}}server.command(cmd);}catch(JSONException|IOException ignored){status("configuration_failed");}
        return true;
    }));
    private static JSONObject command(String type){JSONObject o=new JSONObject();try{o.put("type",type);}catch(JSONException ignored){}return o;}
    @Override public IBinder onBind(Intent intent){return receiver.getBinder();}
    private void requestRestart(){
        if(restartToken!=null)return;
        if(server==null||engine==null){try{restart(new JSONObject(options));}catch(JSONException e){status("settings_error");}return;}
        restartToken=UUID.randomUUID().toString();
        try{server.command(new JSONObject().put("type","restart").put("value",restartToken));main.postDelayed(restartTimeout,20000);}catch(JSONException ignored){finishRestart(restartToken);}
    }
    private void finishRestart(String token){
        if(token==null||!token.equals(restartToken))return;
        main.removeCallbacks(restartTimeout);restartToken=null;
        try{restart(new JSONObject(options));}catch(JSONException ignored){status("settings_error");}
    }
    private void restart(JSONObject settings){closeEngine();lastFrame=null;
        if(!PebbleFiles.watchface(this).isFile()){status("watchface_missing");return;}if(!PebbleFiles.ready(this,board)){status("runtime_missing");return;}
        try{server=new PebbleServer(this,this);server.network=settings.optBoolean("network",false);engine=new WebView(this);if(!isolation.enable(engine,server.origin())){closeEngine();status("webview_update_required");return;}WebSettings s=engine.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setSupportMultipleWindows(false);s.setMediaPlaybackRequiresUserGesture(true);
            final String base=server.base(),origin=server.origin();engine.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !request.getUrl().toString().startsWith(base);}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(url.startsWith(base)||url.startsWith("blob:"+origin+"/"))return null;return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
                @Override public void onPageFinished(WebView v,String url){if(server!=null)try{server.command(new JSONObject().put("type","settings").put("value",new JSONObject(options)));}catch(JSONException ignored){}}
                @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){status("engine_crashed");closeEngine();return true;}
            });
            engine.setWebChromeClient(new WebChromeClient());engine.onResume();status("booting");engine.loadUrl(base+"engine.html?board="+Uri.encode(board)+"&steady="+(PebbleFiles.steadyDisplay(this,board)?"1":"0"));
        }catch(Exception e){status("engine_failed");closeEngine();}
    }
    private void send(Messenger client,int what,Bundle data){try{Message message=Message.obtain(null,what);message.setData(data);client.send(message);}catch(DeadObjectException e){clients.remove(client.getBinder());}catch(RemoteException e){/* A failed large parcel does not mean the client disconnected. */}}
    private void broadcast(int what,Bundle data){for(Messenger client:new ArrayList<>(clients.values()))send(client,what,data);if(clients.isEmpty())main.postDelayed(shutdown,5000);}
    @Override public void status(String value){main.post(()->{if(value.startsWith("restart_ready:")){finishRestart(value.substring(14));return;}status=value.length()>500?value.substring(0,500):value;Bundle b=new Bundle();b.putString("status",status);broadcast(STATUS,b);});}
    @Override public void config(String value){try{String id=PebbleConfigStore.write(this,value);main.post(()->{if(clients.isEmpty()){PebbleConfigStore.remove(this,id);return;}Bundle b=new Bundle();b.putString("configId",id);broadcast(CONFIG,b);});}catch(IOException e){status("configuration_failed");}}
    @Override public void frame(byte[] png){if(png.length>512*1024)return;BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(png,0,png.length,dimensions);if(dimensions.outWidth<1||dimensions.outHeight<1||dimensions.outWidth>512||dimensions.outHeight>512)return;main.post(()->{lastFrame=png;Bundle b=new Bundle();b.putByteArray("png",png);broadcast(FRAME,b);});}
    private void closeEngine(){main.removeCallbacks(restartTimeout);restartToken=null;isolation.clear();if(engine!=null){engine.stopLoading();engine.destroy();engine=null;}if(server!=null){server.close();server=null;}}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);closeEngine();clients.clear();super.onDestroy();}
}
