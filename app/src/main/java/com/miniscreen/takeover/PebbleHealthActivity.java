package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** Optional setup can be reopened without modifying the original OEM confirmations. */
public final class PebbleHealthActivity extends Activity {
    private TextView status;
    @Override protected void attachBaseContext(Context c){super.attachBaseContext(I18n.wrap(c));}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);LinearLayout body=new LinearLayout(this);body.setOrientation(1);int pad=Math.round(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText(I18n.get(R.string.pebble_health));title.setTextSize(24);body.addView(title);
        TextView help=new TextView(this);help.setText(I18n.get(R.string.pebble_health_help));body.addView(help);
        option(body,R.string.pebble_steps,"pebble_health_steps");option(body,R.string.pebble_heart,"pebble_health_heart");option(body,R.string.wfz_distance,"wfz_health_distance");option(body,R.string.wfz_calories,"wfz_health_calories");
        option(body,R.string.pebble_background,"pebble_health_background");
        status=new TextView(this);body.addView(status);
        Button allow=new Button(this);allow.setText(I18n.get(R.string.pebble_grant));body.addView(allow);allow.setEnabled(PebbleHealth.available(this));allow.setOnClickListener(v->PebbleHealth.request(this,TakeoverControl.prefs(this).getBoolean("pebble_health_background",false)));
        Button done=new Button(this);done.setText(I18n.get(R.string.onboarding_finish));body.addView(done);done.setOnClickListener(v->{if(getIntent().getBooleanExtra("onboarding",false))startActivity(new Intent(this,MainActivity.class));finish();});refresh();
    }
    private void option(LinearLayout parent,int label,String key){Switch s=new Switch(this);s.setText(I18n.get(label));s.setChecked(TakeoverControl.prefs(this).getBoolean(key,false));parent.addView(s);s.setOnCheckedChangeListener((b,on)->TakeoverControl.prefs(this).edit().putBoolean(key,on).apply());}
    private void refresh(){if(status!=null)status.setText(!PebbleHealth.available(this)?I18n.get(R.string.pebble_health_unavailable):I18n.get(R.string.pebble_steps)+": "+(PebbleHealth.granted(this,PebbleHealth.STEPS)?"✓":"—")+"\n"+I18n.get(R.string.pebble_heart)+": "+(PebbleHealth.granted(this,PebbleHealth.HEART)?"✓":"—")+"\n"+I18n.get(R.string.wfz_distance)+": "+(PebbleHealth.granted(this,WfzHealth.DISTANCE)?"✓":"—")+"\n"+I18n.get(R.string.wfz_calories)+": "+(PebbleHealth.granted(this,WfzHealth.CALORIES)?"✓":"—")+"\n"+I18n.get(R.string.pebble_background)+": "+(PebbleHealth.granted(this,PebbleHealth.BACKGROUND)?"✓":"—"));}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);refresh();}
}
