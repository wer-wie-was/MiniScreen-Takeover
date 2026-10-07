package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.util.Arrays;

/** Manual OEM preparation; confirmations are user statements, never automatic verification. */
public final class OnboardingActivity extends Activity {
    private SharedPreferences prefs;
    private int step;
    private TextView access;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String t(int id){return I18n.get(id);}
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(I18n.wrap(base));}
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=TakeoverControl.prefs(this);step=Config.clamp(prefs.getInt("onboarding_step_v2",0),0,2);build();}
    private TextView text(LinearLayout box,String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(0xFFE5EDF7);v.setPadding(0,dp(8),0,dp(8));box.addView(v);return v;}
    private Button button(LinearLayout box,String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(primary?0xFF102329:0xFFE5EDF7);
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));bg.setColor(primary?0xFF63E6DC:0xFF263747);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3363E6DC),bg,null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);b.setPadding(dp(12),dp(12),dp(12),dp(12));box.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    private void page(int next){step=next;prefs.edit().putInt("onboarding_step_v2",step).apply();build();}
    private void build(){
        access=null;LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(22),dp(18),dp(22),dp(18));
        text(layout,"MiniScreen Takeover",24).setTypeface(null,Typeface.BOLD);
        text(layout,t(R.string.onboarding_progress)+" "+(step+1)+" / 3",13).setTextColor(0xFF63E6DC);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.VERTICAL);layout.addView(navigation);setContentView(layout);
        if(step==0){
            text(body,t(R.string.onboarding_language_title),23).setTypeface(null,Typeface.BOLD);
            text(body,t(R.string.msg_001),16);
            String[] codes={"","en","de","zh","fr","tr","es","it","ru"};
            String[] names={t(R.string.language_system),"English","Deutsch","中文（简体）","Français","Türkçe","Español","Italiano","Русский"};
            Spinner language=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);language.setAdapter(adapter);
            int selected=Math.max(0,Arrays.asList(codes).indexOf(prefs.getString("language","")));language.setSelection(selected);body.addView(language);int[] previous={selected};
            language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> v){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int index,long id){if(index!=previous[0]){previous[0]=index;I18n.select(OnboardingActivity.this,codes[index]);recreate();}}});
            button(navigation,t(R.string.onboarding_next),true,()->page(1));
        }else if(step==1){
            text(body,t(R.string.onboarding_background_title),23).setTypeface(null,Typeface.BOLD);
            text(body,t(R.string.onboarding_background_body),16);
            button(body,t(R.string.onboarding_open_settings),false,this::openGeneralSettings);
            text(body,t(R.string.onboarding_manual),13).setTextColor(0xFFAAB9CB);
            confirmation(body,navigation,"onboarding_background_confirmed",R.string.onboarding_background_confirm);
        }else{
            text(body,t(R.string.onboarding_miniscreen_title),23).setTypeface(null,Typeface.BOLD);
            text(body,t(R.string.onboarding_miniscreen_body),16);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(10),dp(16),dp(10));
            GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF1D2A37);bg.setCornerRadius(dp(14));card.setBackground(bg);body.addView(card);
            text(card,"Screen timeout → Never",17).setTypeface(null,Typeface.BOLD);
            text(card,"Go home time / Back to Home → Never",17).setTypeface(null,Typeface.BOLD);
            text(card,"Open personalized signature → "+t(R.string.onboarding_off),17).setTypeface(null,Typeface.BOLD);
            text(card,t(R.string.onboarding_other_switches),16);
            text(card,t(R.string.onboarding_music),16);
            text(body,t(R.string.onboarding_scroll),15);
            button(body,t(R.string.onboarding_open_miniscreen),false,()->{
                Intent i=new Intent().setComponent(new ComponentName("com.yft.miniscreendisplay","com.yft.miniscreendisplay.SettingActivity"));
                try{startActivity(i,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());}
                catch(RuntimeException e){Toast.makeText(this,t(R.string.onboarding_settings_fallback),Toast.LENGTH_LONG).show();openGeneralSettings();}
            });
            text(body,t(R.string.onboarding_manual),13).setTextColor(0xFFAAB9CB);
            text(body,t(R.string.onboarding_restricted_title),20).setTypeface(null,Typeface.BOLD);
            text(body,t(R.string.onboarding_restricted_body),16);
            button(body,t(R.string.onboarding_app_details),false,()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:"+getPackageName()))));
            text(body,t(R.string.onboarding_notifications),16);access=text(body,"",13);refreshAccess();
            button(body,t(R.string.ticker_access),false,()->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            confirmation(body,navigation,"onboarding_miniscreen_confirmed",R.string.onboarding_miniscreen_confirm);
        }
        if(step>0)button(navigation,t(R.string.onboarding_back),false,()->page(step-1));
    }
    private void confirmation(LinearLayout body,LinearLayout navigation,String key,int label){
        CheckBox check=new CheckBox(this);check.setText(t(label));check.setTextColor(0xFFE5EDF7);check.setChecked(prefs.getBoolean(key,false));body.addView(check);
        Button next=button(navigation,t(step==2?R.string.onboarding_finish:R.string.onboarding_next),true,()->{if(!prefs.getBoolean(key,false))return;if(step==2)complete();else page(step+1);});
        next.setEnabled(check.isChecked());next.setAlpha(check.isChecked()?1:.45f);
        check.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();next.setEnabled(checked);next.setAlpha(checked?1:.45f);});
    }
    private void complete(){
        if(!prefs.getBoolean("onboarding_background_confirmed",false)){page(1);return;}
        if(!prefs.getBoolean("onboarding_miniscreen_confirmed",false))return;
        prefs.edit().putBoolean("onboarding_complete",true).putInt("onboarding_step_v2",0).apply();
        startActivity(new Intent(this,PebbleHealthActivity.class).putExtra("onboarding",true));finish();
    }
    private void openGeneralSettings(){
        // Start the Settings root in a fresh Settings task, rather than resume its last subpage.
        open(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void open(Intent i){try{startActivity(i,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());}catch(RuntimeException e){Toast.makeText(this,t(R.string.onboarding_settings_fallback),Toast.LENGTH_LONG).show();}}
    private void refreshAccess(){if(access==null)return;String flat=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");boolean granted=false;ComponentName own=new ComponentName(this,TickerListener.class);if(flat!=null)for(String item:flat.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))granted=true;access.setText(t(granted?R.string.ticker_access_yes:R.string.ticker_access_no));}
    @Override protected void onResume(){super.onResume();refreshAccess();}
    @Override public void onBackPressed(){if(step>0)page(step-1);else finish();}
}
