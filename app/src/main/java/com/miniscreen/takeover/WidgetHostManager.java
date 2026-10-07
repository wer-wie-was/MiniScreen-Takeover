package com.miniscreen.takeover;

import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.lang.ref.WeakReference;
import java.util.*;

/** One persistent host, with RemoteViews fan-out to the rear screen and its pinned preview. */
final class WidgetHostManager {
    static final int HOST_ID=340;
    private static Host host;private static int listeners;
    static Host host(Context c){if(host==null)host=new Host(c.getApplicationContext());return host;}
    static boolean start(Context c){if(listeners==0)try{host(c).startListening();}catch(RuntimeException e){return false;}listeners++;return true;}
    static void stop(){if(listeners>0&&--listeners==0&&host!=null)try{host.stopListening();}catch(RuntimeException ignored){}}
    static void delete(Context c,int id){if(id>0)try{host(c).deleteAppWidgetId(id);}catch(RuntimeException ignored){}}
    static final class Host extends AppWidgetHost {
        private final List<WeakReference<MirrorView>> views=new ArrayList<>();
        Host(Context c){super(c,HOST_ID);}
        @Override protected AppWidgetHostView onCreateView(Context c,int id,AppWidgetProviderInfo info){MirrorView v=new MirrorView(c,this,id);views.add(new WeakReference<>(v));return v;}
        void distribute(MirrorView sender,RemoteViews data){
            Iterator<WeakReference<MirrorView>> it=views.iterator();while(it.hasNext()){MirrorView v=it.next().get();if(v==null){it.remove();continue;}if(v.id==sender.id)v.apply(data);}
        }
    }
    private static final class MirrorView extends AppWidgetHostView {
        final Host owner;final int id;
        MirrorView(Context c,Host owner,int id){super(c);this.owner=owner;this.id=id;}
        @Override public void updateAppWidget(RemoteViews views){owner.distribute(this,views);}
        void apply(RemoteViews views){super.updateAppWidget(views);}
    }
}
