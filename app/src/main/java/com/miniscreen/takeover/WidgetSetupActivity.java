package com.miniscreen.takeover;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import java.util.*;

/** The system grants binding and providers configure widgets on the main display. */
public final class WidgetSetupActivity extends Activity {
    private static final int BIND=1,CONFIGURE=2;
    private int pending=-1;private boolean committed,awaiting;
    @Override protected void attachBaseContext(Context c){super.attachBaseContext(I18n.wrap(c));}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        if(state!=null){pending=state.getInt("pending",-1);awaiting=state.getBoolean("awaiting",false);if(pending>0){if(!awaiting)failed();return;}}
        SharedPreferences p=TakeoverControl.prefs(this);WidgetHostManager.delete(this,p.getInt("widget_pending_id",-1));p.edit().remove("widget_pending_id").apply();
        List<AppWidgetProviderInfo> providers=new ArrayList<>(AppWidgetManager.getInstance(this).getInstalledProviders());
        providers.sort((a,b)->a.loadLabel(getPackageManager()).compareToIgnoreCase(b.loadLabel(getPackageManager())));
        String[] labels=new String[providers.size()];for(int i=0;i<labels.length;i++)labels[i]=providers.get(i).loadLabel(getPackageManager())+" · "+providers.get(i).provider.getPackageName();
        if(providers.isEmpty()){new AlertDialog.Builder(this).setMessage(I18n.get(R.string.widget_empty)).setPositiveButton(android.R.string.ok,(d,w)->finish()).setOnCancelListener(d->finish()).show();return;}
        new AlertDialog.Builder(this).setTitle(I18n.get(R.string.widget_choose)).setItems(labels,(d,index)->bind(providers.get(index))).setNegativeButton(I18n.get(R.string.msg_056),(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
    private void bind(AppWidgetProviderInfo info){try{
        pending=WidgetHostManager.host(this).allocateAppWidgetId();TakeoverControl.prefs(this).edit().putInt("widget_pending_id",pending).apply();
        if(AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(pending,info.provider))configure();
        else {awaiting=true;startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pending).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider),BIND);}
    }catch(RuntimeException e){failed();}}
    private void configure(){AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(pending);if(info==null){failed();return;}
        if(info.configure==null){commit();return;}
        try{awaiting=true;WidgetHostManager.host(this).startAppWidgetConfigureActivityForResult(this,pending,0,CONFIGURE,null);}catch(RuntimeException e){failed();}
    }
    private void commit(){AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(pending);if(info==null){failed();return;}
        SharedPreferences p=TakeoverControl.prefs(this);int old=p.getInt("widget_id",-1);
        p.edit().putInt("widget_id",pending).putString("widget_label",info.loadLabel(getPackageManager())).putBoolean("widget_enabled",true).remove("widget_pending_id").apply();committed=true;WidgetHostManager.delete(this,old);setResult(RESULT_OK);finish();
    }
    private void failed(){awaiting=false;new AlertDialog.Builder(this).setMessage(I18n.get(R.string.widget_failed)).setPositiveButton(android.R.string.ok,(d,w)->finish()).setOnCancelListener(d->finish()).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);awaiting=false;if(result!=RESULT_OK){finish();return;}if(request==BIND)configure();else if(request==CONFIGURE)commit();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("pending",pending);state.putBoolean("awaiting",awaiting);}
    @Override protected void onDestroy(){if(isFinishing()&&!committed){WidgetHostManager.delete(this,pending);TakeoverControl.prefs(this).edit().remove("widget_pending_id").apply();}super.onDestroy();}
}
