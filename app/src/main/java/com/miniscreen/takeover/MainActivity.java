package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener,DisplayManager.DisplayListener {
    private static final int IMAGE=11,FONT=12,DESIGN=13,IMPORT_PROFILE=14,EXPORT_PROFILE=15,CHARGE_FONT=16,WIDGET=80;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> opened=new HashSet<>(Arrays.asList(I18n.get(R.string.msg_003),I18n.get(R.string.msg_004)));
    private ProfileStore store;
    private Config config;
    private DisplayManager displays;
    private LinearLayout root;
    private ScrollView scroll;
    private TextView status,workStatus,monitorStatus,scheduleStatus,tickerAccessStatus,dotAccessStatus;
    private PreviewFrame preview;
    private boolean busy,resumed,guides=true,chargePreview,tickerPreview,dotPreview;
    private String pendingProfile="",uiLanguage="";
    private int pendingRequest;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(getWindowManager().getDefaultDisplay().getDisplayId()!=Display.DEFAULT_DISPLAY){startActivity(new Intent(this,ScreenActivity.class));finish();return;}
        if(!TakeoverControl.prefs(this).getBoolean("onboarding_complete",false)){startActivity(new Intent(this,OnboardingActivity.class));finish();return;}
        store=new ProfileStore(this);uiLanguage=store.prefs.getString("language","");displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);config=store.load();
        if(saved!=null){pendingProfile=saved.getString("pendingProfile","");pendingRequest=saved.getInt("pendingRequest");guides=saved.getBoolean("guides",true);chargePreview=saved.getBoolean("chargePreview",false);tickerPreview=saved.getBoolean("tickerPreview",false);dotPreview=saved.getBoolean("dotPreview",false);String[] sections=saved.getStringArray("opened");if(sections!=null&&saved.getString("language","").equals(store.prefs.getString("language",""))){opened.clear();Collections.addAll(opened,sections);}}
        store.prefs.registerOnSharedPreferenceChangeListener(this);build();
    }
    private void build() {
        int y=scroll==null?0:scroll.getScrollY();if(preview!=null)preview.surface.dispose();
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pinned=new LinearLayout(this);pinned.setOrientation(LinearLayout.HORIZONTAL);pinned.setGravity(Gravity.CENTER_VERTICAL);pinned.setPadding(dp(18),dp(12),dp(18),dp(12));pinned.setBackgroundColor(0xFF15212D);
        layout.addView(pinned,new LinearLayout.LayoutParams(-1,-2));
        int target=1;Display d=displays.getDisplay(target);int w=340,h=340;Context dc=this;
        if(d!=null){w=d.getMode().getPhysicalWidth();h=d.getMode().getPhysicalHeight();dc=createDisplayContext(d);}
        int size=dp(Math.max(88,Math.min(160,getResources().getConfiguration().screenHeightDp/4)));
        preview=new PreviewFrame(this,dc,w,h);int pw=Math.round(size*Math.min(1f,w/(float)h));pinned.addView(preview,new LinearLayout.LayoutParams(pw,-2));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(16);pinned.addView(info,ip);
        TextView title=text(info,"MiniScreen Takeover",20);title.setTypeface(null,Typeface.BOLD);
        text(info,I18n.get(R.string.msg_005),11).setTextColor(0xFF63E6DC);
        text(info,w+" × "+h+" · Display "+target,12).setTextColor(0xFF9FACBE);
        CheckBox guide=new CheckBox(this);guide.setText(I18n.get(R.string.msg_006));guide.setTextSize(12);guide.setTextColor(0xFFCBD5E1);guide.setChecked(guides);info.addView(guide);guide.setOnCheckedChangeListener((v,checked)->{guides=checked;preview.guides(checked,config.shifting?config.shiftRange:0);});
        preview.surface.apply(config);preview.surface.simulateCharge(chargePreview);preview.surface.simulateTicker(tickerPreview);preview.surface.simulateDot(dotPreview);preview.guides(guides,config.shifting?config.shiftRange:0);if(resumed)preview.surface.start();
        workStatus=text(info,I18n.get(R.string.msg_007),12);workStatus.setTextColor(0xFF63E6DC);workStatus.setVisibility(busy?View.VISIBLE:View.GONE);
        scroll=new ScrollView(this);scroll.setFillViewport(true);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),0,dp(18),dp(32));scroll.addView(root);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
        languageSettings();controls();scheduleSettings();externalApps();profiles();clockSettings();background();charging();pebbleSettings();widgetSettings();tickerSettings();dotSettings();shifting();html();
        text(root,I18n.get(R.string.msg_008),12).setTextColor(0xFF9FACBE);
        scroll.post(()->scroll.scrollTo(0,y));
    }
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(I18n.wrap(base));}
    private void languageSettings(){
        LinearLayout p=section(I18n.get(R.string.msg_000));
        button(p,I18n.get(R.string.onboarding_reopen),()->{store.prefs.edit().putInt("onboarding_step_v2",0).apply();startActivity(new Intent(this,OnboardingActivity.class));});
        String[] codes={"","en","de","zh","fr","tr","es","it","ru"};
        String[] names={I18n.get(R.string.language_system),"English","Deutsch","中文（简体）","Français","Türkçe","Español","Italiano","Русский"};
        String selected=store.prefs.getString("language","");int index=Arrays.asList(codes).indexOf(selected);
        choose(p,I18n.get(R.string.msg_001),names,Math.max(0,index),i->{I18n.select(this,codes[i]);recreate();});
    }
    private TextView text(LinearLayout p,String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(0xFFE5EDF7);v.setPadding(0,dp(6),0,dp(6));p.addView(v);return v;}
    private android.graphics.drawable.Drawable controlBackground(int color,int border,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));if(border!=0)bg.setStroke(dp(1),border);return new RippleDrawable(ColorStateList.valueOf(0x3363E6DC),bg,null);}
    private Button button(LinearLayout p,String value,Runnable action){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(0xFFE5EDF7);b.setBackgroundTintList(null);b.setBackground(controlBackground(0xFF293B50,0xFF48627D,10));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setMinHeight(dp(48));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(8);bp.bottomMargin=dp(4);p.addView(b,bp);b.setOnClickListener(v->{if(!busy)action.run();else toast(I18n.get(R.string.msg_007));});return b;}
    private LinearLayout section(String title) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(6),dp(12),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF1B2431);bg.setCornerRadius(dp(16));card.setBackground(bg);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(14);root.addView(card,cp);
        TextView head=text(card,(opened.contains(title)?"▾ ":"▸ ")+title,18);head.setTypeface(null,Typeface.BOLD);head.setTextColor(0xFF63E6DC);head.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);head.setPadding(dp(8),dp(12),dp(8),dp(12));head.setMinHeight(dp(52));head.setBackground(controlBackground(0xFF172E35,0,10));head.setFocusable(true);head.setContentDescription(title+I18n.get(R.string.msg_009));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setVisibility(opened.contains(title)?View.VISIBLE:View.GONE);card.addView(body);
        head.setOnClickListener(v->{if(body.getVisibility()==View.VISIBLE){opened.remove(title);body.setVisibility(View.GONE);head.setText("▸ "+title);}else{opened.add(title);body.setVisibility(View.VISIBLE);head.setText("▾ "+title);}});return body;
    }
    private void check(LinearLayout p,String title,boolean value,Consumer<Boolean> change){Switch s=new Switch(this);s.setText(title);s.setTextColor(0xFFE5EDF7);s.setPadding(0,dp(8),0,dp(8));s.setChecked(value);p.addView(s);s.setOnCheckedChangeListener((b,v)->{if(!busy)change.accept(v);});}
    private void slider(LinearLayout p,String label,int min,int max,int value,IntConsumer change){TextView t=text(p,label+": "+value,14);SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(value-min);p.addView(s);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int n,boolean user){t.setText(label+": "+(n+min));if(user&&!busy)change.accept(n+min);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}
    private void choose(LinearLayout p,String label,String[] options,int selected,IntConsumer change){text(p,label,14);Spinner spinner=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(a);spinner.setSelection(selected);p.addView(spinner);int[] last={selected};spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> v){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position!=last[0]){last[0]=position;if(!busy)change.accept(position);}}});}
    private void color(LinearLayout p,String title,Supplier<String> get,Consumer<String> set){Button b=button(p,title+" · "+get.get(),()->ColorPicker.show(this,title,get.get(),v->{set.accept(v);changed();build();}));b.setTextColor(Color.parseColor(get.get()));if(get.get().equals("#000000"))b.setTextColor(0xFFCBD5E1);}
    private void changed(){if(busy)return;try{store.save(config);preview.surface.apply(config);preview.surface.simulateCharge(chargePreview);preview.surface.simulateTicker(tickerPreview);preview.surface.simulateDot(dotPreview);preview.guides(guides,config.shifting?config.shiftRange:0);}catch(RuntimeException e){error(e);}}
    private void controls() {
        LinearLayout p=section(I18n.get(R.string.msg_003));status=text(p,store.prefs.getString("status",I18n.get(R.string.msg_010)),14);
        Button start=button(p,I18n.get(R.string.msg_011),this::manufacturerStart);start.setBackground(controlBackground(0xFF63E6DC,0,10));start.setTextColor(0xFF102329);
        button(p,I18n.get(R.string.msg_012),()->{TakeoverControl.stop(this);build();});
        button(p,I18n.get(R.string.power_button),()->DisplayPower.off(this,true));
        monitorStatus=text(p,store.prefs.getString("monitor_status",I18n.get(R.string.msg_013)),13);
        String watchMode=TakeoverControl.mode(store.prefs);
        choose(p,I18n.get(R.string.msg_014),new String[]{I18n.get(R.string.msg_015),I18n.get(R.string.msg_016),I18n.get(R.string.msg_017)},watchMode.equals("manual")?0:watchMode.equals("force")?2:1,i->{store.prefs.edit().putString("monitor_mode",i==0?"manual":i==2?"force":"keep").apply();notificationPermission();TakeoverControl.sync(this);});
        check(p,I18n.get(R.string.msg_018),store.prefs.getBoolean("only_locked",false),v->{store.prefs.edit().putBoolean("only_locked",v).apply();TakeoverControl.sync(this);});
        check(p,I18n.get(R.string.msg_019),store.prefs.getBoolean("restore_boot",false),v->store.prefs.edit().putBoolean("restore_boot",v).apply());
        slider(p,I18n.get(R.string.msg_020),15,300,store.prefs.getInt("check_interval",60),n->{store.prefs.edit().putInt("check_interval",n).apply();TakeoverControl.sync(this);});
        button(p,I18n.get(R.string.msg_021),()->new AlertDialog.Builder(this).setTitle(I18n.get(R.string.msg_022)).setItems(new String[]{I18n.get(R.string.msg_023),I18n.get(R.string.msg_024),I18n.get(R.string.msg_025)},(dialog,i)->TakeoverControl.pause(this,i==0?0:i==1?15*60000L:60*60000L)).show());
        button(p,I18n.get(R.string.msg_026),()->{notificationPermission();TakeoverControl.resume(this);});
        button(p,I18n.get(R.string.msg_027),()->{try{startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));}catch(ActivityNotFoundException e){error(e);}});
        text(p,I18n.get(R.string.msg_028),13);
        slider(p,I18n.get(R.string.msg_029),1,100,config.brightness,n->{config.brightness=n;changed();});
        text(p,I18n.get(R.string.msg_030),13);
        button(p,I18n.get(R.string.msg_031),()->{StringBuilder b=new StringBuilder("MiniScreen Takeover 0.3.3\n"+Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n");for(Display d:displays.getDisplays())b.append("Display ").append(d.getDisplayId()).append(" · ").append(d.getName()).append(" · ").append(d.getMode().getPhysicalWidth()).append('×').append(d.getMode().getPhysicalHeight()).append(I18n.get(R.string.msg_032)).append(d.getState()).append(" · Flags 0x").append(Integer.toHexString(d.getFlags())).append('\n');b.append(store.prefs.getString("status","")).append(I18n.get(R.string.msg_033)).append(store.prefs.getString("monitor_status","")).append(I18n.get(R.string.msg_034)).append(TakeoverControl.mode(store.prefs)).append(I18n.get(R.string.msg_035)).append(store.prefs.getBoolean("enabled",false)).append(I18n.get(R.string.msg_036)).append(store.prefs.getBoolean("only_locked",false)).append(" · Boot: ").append(store.prefs.getBoolean("restore_boot",false)).append(I18n.get(R.string.msg_037)).append(TakeoverControl.paused(store.prefs));b.append("\nschedule_enabled=").append(DisplaySchedule.enabled(store.prefs)).append(" · manual_off=").append(DisplaySchedule.manualOff(store.prefs,System.currentTimeMillis())).append(" · external_active=").append(store.prefs.getBoolean("external_active",false));((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(I18n.get(R.string.msg_038),b));toast(I18n.get(R.string.msg_039));});
    }
    private void profiles() {
        LinearLayout p=section(I18n.get(R.string.msg_004));List<String> ids=store.ids();String[] names=new String[ids.size()];for(int i=0;i<ids.size();i++)names[i]=store.load(ids.get(i)).name;
        choose(p,I18n.get(R.string.msg_040),names,Math.max(0,ids.indexOf(store.active())),i->{store.select(ids.get(i));config=store.load();build();});
        choose(p,I18n.get(R.string.msg_041),new String[]{I18n.get(R.string.msg_042),I18n.get(R.string.msg_043),I18n.get(R.string.pebble_section)},config.mode.equals("pebble")?2:config.mode.equals("html")?1:0,i->{if(i==2){config.mode="pebble";changed();build();startActivity(new Intent(this,PebbleSettingsActivity.class));return;}if(i==1&&config.design.isEmpty()){String id=store.active();backgroundWork(I18n.get(R.string.msg_044),()->{Config c=store.load(id);c.design=store.builtIn(id);c.mode="html";store.save(id,c);return id;},v->{config=store.load();build();});}else{config.mode=i==1?"html":"native";changed();build();}});
        button(p,I18n.get(R.string.msg_045),()->nameDialog(I18n.get(R.string.msg_045),I18n.get(R.string.msg_046),name->{try{store.create(name,false);config=store.load();build();}catch(Exception e){error(e);}}));
        button(p,I18n.get(R.string.msg_047),()->nameDialog(I18n.get(R.string.msg_047),config.name+I18n.get(R.string.msg_048),name->backgroundWork(I18n.get(R.string.msg_049),()->store.create(name,true),id->{config=store.load();build();})));
        button(p,I18n.get(R.string.msg_050),()->nameDialog(I18n.get(R.string.msg_051),config.name,name->{config.name=name;changed();build();}));
        button(p,I18n.get(R.string.msg_052),()->pick(EXPORT_PROFILE));button(p,I18n.get(R.string.msg_053),()->pick(IMPORT_PROFILE));
        button(p,I18n.get(R.string.msg_054),()->new AlertDialog.Builder(this).setTitle(I18n.get(R.string.msg_055)).setMessage(config.name).setNegativeButton(I18n.get(R.string.msg_056),null).setPositiveButton(I18n.get(R.string.msg_057),(d,w)->{store.deleteActive();config=store.load();build();}).show());
    }
    private void clockSettings() {
        LinearLayout p=section(I18n.get(R.string.msg_059));text(p,I18n.get(R.string.msg_060),13);
        check(p,tt(R.string.module_clock),config.showClock,v->{config.showClock=v;changed();});
        check(p,I18n.get(R.string.msg_061),config.twentyFour,v->{config.twentyFour=v;changed();});check(p,I18n.get(R.string.msg_062),config.seconds,v->{config.seconds=v;changed();});check(p,I18n.get(R.string.msg_063),config.leadingZero,v->{config.leadingZero=v;changed();});check(p,I18n.get(R.string.msg_064),config.showDate,v->{config.showDate=v;changed();});
        color(p,I18n.get(R.string.msg_065),()->config.timeColor,v->config.timeColor=v);color(p,I18n.get(R.string.msg_066),()->config.dateColor,v->config.dateColor=v);
        slider(p,I18n.get(R.string.msg_067),12,130,config.timeSize,n->{config.timeSize=n;changed();});slider(p,I18n.get(R.string.msg_068),8,50,config.dateSize,n->{config.dateSize=n;changed();});
        String[] fonts={"sans","sans-serif-light","sans-serif-condensed","monospace","serif"};int index=Arrays.asList(fonts).indexOf(config.font);
        choose(p,I18n.get(R.string.msg_069),fonts,Math.max(0,index),i->{config.font=fonts[i];config.fontFile="";changed();});check(p,I18n.get(R.string.msg_070),config.bold,v->{config.bold=v;changed();});
        button(p,I18n.get(R.string.msg_071),()->pick(FONT));button(p,I18n.get(R.string.msg_072),()->{config.fontFile="";changed();toast(I18n.get(R.string.msg_073));});
        slider(p,I18n.get(R.string.msg_074),0,100,config.timeX,n->{config.timeX=n;changed();});slider(p,I18n.get(R.string.msg_075),0,100,config.timeY,n->{config.timeY=n;changed();});
        slider(p,I18n.get(R.string.msg_076),0,100,config.dateX,n->{config.dateX=n;changed();});slider(p,I18n.get(R.string.msg_077),0,100,config.dateY,n->{config.dateY=n;changed();});
        String[] patterns={"dd.MM.yyyy","EEE, dd.MM.yyyy","EEEE, dd. MMMM","yyyy-MM-dd","dd/MM/yyyy","MM/dd/yyyy"};
        button(p,I18n.get(R.string.msg_078),()->new AlertDialog.Builder(this).setTitle(I18n.get(R.string.msg_079)).setItems(patterns,(dialog,i)->{config.datePattern=patterns[i];changed();build();}).show());
        EditText format=new EditText(this);format.setSingleLine(true);format.setText(config.datePattern);format.setHint(I18n.get(R.string.msg_080));p.addView(format);
        button(p,I18n.get(R.string.msg_081),()->{String value=format.getText().toString();try{if(value.isEmpty()||value.length()>120)throw new IllegalArgumentException();new SimpleDateFormat(value,I18n.locale());config.datePattern=value;changed();toast(I18n.get(R.string.msg_082)+config.date(new Date()));}catch(IllegalArgumentException e){format.setError(I18n.get(R.string.msg_083));}});
        text(p,I18n.get(R.string.msg_084),13);
    }
    private void background() {
        LinearLayout p=section(I18n.get(R.string.msg_085));color(p,I18n.get(R.string.msg_086),()->config.backgroundColor,v->config.backgroundColor=v);
        button(p,I18n.get(R.string.msg_087),()->pick(IMAGE));button(p,I18n.get(R.string.msg_088),()->{config.image="";changed();toast(I18n.get(R.string.msg_089));});
        choose(p,I18n.get(R.string.msg_179),new String[]{I18n.get(R.string.msg_090),I18n.get(R.string.msg_091),I18n.get(R.string.window_mode)},config.imageFit.equals("window")?2:config.imageFit.equals("contain")?1:0,i->{config.imageFit=i==2?"window":i==1?"contain":"cover";changed();});
        slider(p,I18n.get(R.string.window_zoom),100,500,config.windowZoom,n->{config.windowZoom=n;changed();});
        slider(p,I18n.get(R.string.window_speed),10,180,config.windowSeconds,n->{config.windowSeconds=n;changed();});
        check(p,I18n.get(R.string.window_moving),config.windowMotion,v->{config.windowMotion=v;changed();});
        text(p,I18n.get(R.string.window_help),13);
        slider(p,I18n.get(R.string.msg_092),0,100,config.dim,n->{config.dim=n;changed();});check(p,I18n.get(R.string.msg_093),config.shiftBackground,v->{config.shiftBackground=v;changed();});
        text(p,I18n.get(R.string.msg_094),13);
    }
    private void scheduleSettings(){
        LinearLayout p=section(I18n.get(R.string.schedule_section));
        check(p,I18n.get(R.string.schedule_enable),DisplaySchedule.enabled(store.prefs),v->{
            if(v&&!DisplaySchedule.valid(store.prefs)){toast(I18n.get(R.string.schedule_invalid));build();return;}
            notificationPermission();DisplaySchedule.configure(this,v);build();
        });
        button(p,I18n.get(R.string.schedule_on)+clockTime(store.prefs.getInt("schedule_on",420)),()->scheduleTime("schedule_on",420));
        button(p,I18n.get(R.string.schedule_off)+clockTime(store.prefs.getInt("schedule_off",1380)),()->scheduleTime("schedule_off",1380));
        button(p,I18n.get(R.string.schedule_days),()->{
            String[] names=new String[]{I18n.get(R.string.day_mon),I18n.get(R.string.day_tue),I18n.get(R.string.day_wed),I18n.get(R.string.day_thu),I18n.get(R.string.day_fri),I18n.get(R.string.day_sat),I18n.get(R.string.day_sun)};
            boolean[] checked=new boolean[7];int old=store.prefs.getInt("schedule_days",127);for(int i=0;i<7;i++)checked[i]=(old&(1<<i))!=0;
            new AlertDialog.Builder(this).setTitle(I18n.get(R.string.schedule_days)).setMultiChoiceItems(names,checked,(d,i,v)->checked[i]=v)
                .setNegativeButton(I18n.get(R.string.msg_056),null).setPositiveButton(I18n.get(R.string.msg_058),(d,w)->{
                    int mask=0;for(int i=0;i<7;i++)if(checked[i])mask|=1<<i;
                    if(mask==0){toast(I18n.get(R.string.schedule_invalid));return;}
                    store.prefs.edit().putInt("schedule_days",mask).apply();DisplaySchedule.configure(this,DisplaySchedule.enabled(store.prefs));build();
                }).show();
        });
        button(p,I18n.get(R.string.schedule_permission),()->{
            if(Build.VERSION.SDK_INT>=31){try{startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){error(e);}}
            else toast(I18n.get(R.string.schedule_exact));
        });
        scheduleStatus=text(p,"",13);refreshSchedule();text(p,I18n.get(R.string.schedule_help),13);
    }
    private String clockTime(int minute){return String.format(I18n.locale(),"%02d:%02d",minute/60,minute%60);}
    private void scheduleTime(String key,int fallback){
        int value=store.prefs.getInt(key,fallback);
        new TimePickerDialog(this,(picker,h,m)->{
            int next=h*60+m,other=store.prefs.getInt(key.equals("schedule_on")?"schedule_off":"schedule_on",key.equals("schedule_on")?1380:420);
            if(next==other){toast(I18n.get(R.string.schedule_invalid));return;}
            store.prefs.edit().putInt(key,next).apply();DisplaySchedule.configure(this,DisplaySchedule.enabled(store.prefs));build();
        },value/60,value%60,true).show();
    }
    private void refreshSchedule(){
        if(scheduleStatus==null)return;
        String text=I18n.get(DisplaySchedule.enabled(store.prefs)?R.string.schedule_active:R.string.schedule_disabled)+"\n"+I18n.get(DisplaySchedule.exact(this)?R.string.schedule_exact:R.string.schedule_inexact);
        if(DisplaySchedule.enabled(store.prefs)){
            DisplaySchedule.Edge next=DisplaySchedule.next(store.prefs,System.currentTimeMillis(),null);
            if(next!=null)text+="\n"+I18n.get(next.on?R.string.schedule_next_on:R.string.schedule_next_off)+new SimpleDateFormat("EEE, dd.MM. HH:mm",I18n.locale()).format(new Date(next.time));
            if(store.prefs.getBoolean("external_active",false))text+="\n"+I18n.get(R.string.schedule_external);
            else if(TakeoverControl.paused(store.prefs))text+="\n"+I18n.get(R.string.msg_127);
            else if(DisplaySchedule.manualOff(store.prefs,System.currentTimeMillis()))text+="\n"+I18n.get(R.string.power_intentional);
        }
        scheduleStatus.setText(text);
    }
    private String tt(int id){return I18n.get(id);}
    private void tickerChanged(){NotificationTicker.settingsChanged(this);if(preview!=null){preview.surface.simulateTicker(tickerPreview);preview.surface.simulateDot(dotPreview);preview.surface.tick();}}
    private void tickerBool(String key,boolean value){store.prefs.edit().putBoolean("ticker_"+key,value).apply();tickerChanged();}
    private void tickerInt(String key,int value){store.prefs.edit().putInt("ticker_"+key,value).apply();tickerChanged();}
    private void tickerColor(LinearLayout p,int label,String key,String fallback){
        TickerSettings s=new TickerSettings(this);button(p,tt(label)+" · "+s.color(key,fallback),()->ColorPicker.show(this,tt(label),s.color(key,fallback),v->{store.prefs.edit().putString("ticker_"+key,v).apply();tickerChanged();build();}));
    }
    private boolean tickerAccess(){String flat=android.provider.Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");if(flat==null)return false;ComponentName own=new ComponentName(this,TickerListener.class);for(String item:flat.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    private void refreshTickerAccess(){String label=tt(tickerAccess()?R.string.ticker_access_yes:R.string.ticker_access_no);if(tickerAccessStatus!=null)tickerAccessStatus.setText(label);if(dotAccessStatus!=null)dotAccessStatus.setText(label);}
    private String[] privacyOptions(){return new String[]{tt(R.string.ticker_private),tt(R.string.ticker_app_name),tt(R.string.ticker_title),tt(R.string.ticker_full)};}
    private void tickerSettings(){
        LinearLayout p=section(tt(R.string.ticker_section));TickerSettings s=new TickerSettings(this);
        check(p,tt(R.string.ticker_enabled),s.enabled(),v->tickerBool("enabled",v));
        tickerAccessStatus=text(p,"",13);refreshTickerAccess();
        button(p,tt(R.string.ticker_access),()->{try{startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(ActivityNotFoundException e){error(e);}});
        button(p,tt(R.string.ticker_apps)+" · "+s.apps().size(),this::chooseTickerApps);
        choose(p,tt(R.string.ticker_privacy_locked),privacyOptions(),s.number("privacy_locked",1,0,3),i->tickerInt("privacy_locked",i));
        choose(p,tt(R.string.ticker_privacy_unlocked),privacyOptions(),s.number("privacy_unlocked",1,0,3),i->tickerInt("privacy_unlocked",i));
        button(p,tt(R.string.ticker_per_app),this::tickerAppPrivacy);
        text(p,tt(R.string.ticker_privacy_help),13);
        check(p,tt(R.string.ticker_only_locked),s.bool("only_locked",false),v->tickerBool("only_locked",v));
        check(p,tt(R.string.ticker_ongoing),s.bool("ongoing",false),v->tickerBool("ongoing",v));
        check(p,tt(R.string.ticker_groups),s.bool("groups",false),v->tickerBool("groups",v));
        choose(p,tt(R.string.ticker_reminder),new String[]{tt(R.string.ticker_once),tt(R.string.ticker_until_unlock),tt(R.string.ticker_until_removed),tt(R.string.ticker_time_limit)},s.mode(),i->tickerInt("reminder",i));
        slider(p,tt(R.string.ticker_interval),10,600,s.interval(),i->tickerInt("interval",i));
        slider(p,tt(R.string.ticker_limit),1,180,s.number("limit",30,1,180),i->tickerInt("limit",i));
        slider(p,tt(R.string.ticker_duration),3,60,s.duration(),i->tickerInt("duration",i));
        slider(p,tt(R.string.ticker_pause),0,60,s.pause(),i->tickerInt("pause",i));
        check(p,tt(R.string.ticker_scroll),s.bool("scroll",true),v->tickerBool("scroll",v));
        slider(p,tt(R.string.ticker_speed),5,120,s.number("speed",28,5,120),i->tickerInt("speed",i));
        slider(p,tt(R.string.ticker_lines),1,5,s.number("lines",2,1,5),i->tickerInt("lines",i));
        slider(p,tt(R.string.ticker_size),8,50,s.number("size",18,8,50),i->tickerInt("size",i));
        String[] fonts={"sans","sans-serif-light","sans-serif-condensed","monospace","serif","profile"};String[] labels=fonts.clone();labels[5]=tt(R.string.ticker_profile_font);
        choose(p,tt(R.string.msg_069),labels,Math.max(0,Arrays.asList(fonts).indexOf(store.prefs.getString("ticker_font","sans"))),i->{store.prefs.edit().putString("ticker_font",fonts[i]).apply();tickerChanged();});
        check(p,tt(R.string.msg_070),s.bool("bold",false),v->tickerBool("bold",v));
        tickerColor(p,R.string.ticker_color,"color","#FFFFFF");tickerColor(p,R.string.ticker_background,"background","#000000");
        slider(p,tt(R.string.ticker_opacity),0,100,s.number("opacity",75,0,100),i->tickerInt("opacity",i));
        slider(p,tt(R.string.ticker_width),20,100,s.number("width",90,20,100),i->tickerInt("width",i));
        slider(p,tt(R.string.ticker_x),0,100,s.number("x",50,0,100),i->tickerInt("x",i));
        slider(p,tt(R.string.ticker_y),0,100,s.number("y",82,0,100),i->tickerInt("y",i));
        check(p,tt(R.string.ticker_icon),s.bool("icon",true),v->tickerBool("icon",v));
        slider(p,tt(R.string.ticker_icon_size),8,50,s.number("icon_size",20,8,50),i->tickerInt("icon_size",i));
        choose(p,tt(R.string.ticker_clock),new String[]{tt(R.string.ticker_clock_keep),tt(R.string.ticker_clock_dim),tt(R.string.ticker_clock_hide)},s.number("clock",0,0,2),i->tickerInt("clock",i));
        check(p,tt(R.string.ticker_html),s.html(),v->tickerBool("html",v));
        check(p,tt(R.string.ticker_preview),tickerPreview,v->{tickerPreview=v;preview.surface.simulateTicker(v);});
        button(p,tt(R.string.ticker_clear),()->{NotificationTicker.dismissQueue();preview.surface.tick();});
        text(p,tt(R.string.ticker_help),13);
    }
    private List<String> tickerApps(){
        Set<String> names=new HashSet<>(NotificationTicker.observedApps);for(ExternalAppLauncher.Entry e:ExternalAppLauncher.apps(this))names.add(e.component.getPackageName());names.addAll(new TickerSettings(this).apps());names.addAll(NotificationDot.apps(this));names.remove(getPackageName());
        List<String> apps=new ArrayList<>(names);apps.sort((a,b)->tickerAppLabel(a).compareToIgnoreCase(tickerAppLabel(b)));return apps;
    }
    private String tickerAppLabel(String app){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception e){return app;}}
    private void chooseTickerApps(){
        List<String> apps=tickerApps();Set<String> selected=new TickerSettings(this).apps();
        ScrollView list=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);list.addView(rows);
        for(String app:apps){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(4),dp(8),dp(4));
            ImageView icon=new ImageView(this);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(app));}catch(Exception ignored){}row.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));
            CheckBox check=new CheckBox(this);check.setText(tickerAppLabel(app)+"\n"+app);check.setChecked(selected.contains(app));row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            check.setOnCheckedChangeListener((v,value)->{if(value)selected.add(app);else selected.remove(app);});rows.addView(row);
        }
        new AlertDialog.Builder(this).setTitle(tt(R.string.ticker_apps)).setView(list)
            .setNegativeButton(tt(R.string.msg_056),null).setPositiveButton(android.R.string.ok,(d,w)->{store.prefs.edit().putStringSet("ticker_apps",selected).apply();tickerChanged();build();}).show();
    }
    private void tickerAppPrivacy(){
        List<String> apps=new ArrayList<>(new TickerSettings(this).apps());apps.sort((a,b)->tickerAppLabel(a).compareToIgnoreCase(tickerAppLabel(b)));String[] labels=new String[apps.size()];for(int i=0;i<labels.length;i++)labels[i]=tickerAppLabel(apps.get(i));
        new AlertDialog.Builder(this).setTitle(tt(R.string.ticker_per_app)).setItems(labels,(d,i)->{
            String app=apps.get(i);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));
            String[] levels=new String[5];levels[0]=tt(R.string.ticker_inherit);System.arraycopy(privacyOptions(),0,levels,1,4);
            for(String state:new String[]{"locked","unlocked"}){String key="ticker_privacy_"+state+"_"+app;
                choose(box,tt(state.equals("locked")?R.string.ticker_privacy_locked:R.string.ticker_privacy_unlocked),levels,store.prefs.contains(key)?Config.clamp(store.prefs.getInt(key,1),0,3)+1:0,n->{SharedPreferences.Editor edit=store.prefs.edit();if(n==0)edit.remove(key);else edit.putInt(key,n-1);edit.apply();tickerChanged();});}
            new AlertDialog.Builder(this).setTitle(tickerAppLabel(app)).setView(box).setPositiveButton(android.R.string.ok,null).show();
        }).setNegativeButton(tt(R.string.msg_056),null).show();
    }

    private void moduleChanged(){NotificationDot.changed(this);if(preview!=null)preview.surface.tick();}
    private void moduleBool(LinearLayout p,int label,String key,boolean fallback){check(p,tt(label),store.prefs.getBoolean(key,fallback),v->{store.prefs.edit().putBoolean(key,v).apply();moduleChanged();});}
    private void moduleSlider(LinearLayout p,int label,String key,int min,int max,int value){slider(p,tt(label),min,max,Config.clamp(store.prefs.getInt(key,value),min,max),v->{store.prefs.edit().putInt(key,v).apply();moduleChanged();});}
    private void pebbleSettings(){LinearLayout p=section(tt(R.string.pebble_section));check(p,tt(R.string.pebble_use_mode),config.mode.equals("pebble"),enabled->{config.mode=enabled?"pebble":"native";changed();build();});text(p,tt(R.string.pebble_help),13);button(p,tt(R.string.pebble_open),()->startActivity(new Intent(this,PebbleSettingsActivity.class)));}
    private void widgetSettings(){
        LinearLayout p=section(tt(R.string.widget_section));moduleBool(p,R.string.widget_enabled,"widget_enabled",false);
        text(p,store.prefs.getString("widget_label",tt(R.string.widget_none)),14);
        button(p,tt(R.string.widget_choose),()->startActivityForResult(new Intent(this,WidgetSetupActivity.class),WIDGET));
        button(p,tt(R.string.widget_remove),()->{int id=store.prefs.getInt("widget_id",-1);store.prefs.edit().remove("widget_id").remove("widget_label").putBoolean("widget_enabled",false).apply();WidgetHostManager.delete(this,id);build();});
        moduleSlider(p,R.string.module_x,"widget_x",0,100,50);moduleSlider(p,R.string.module_y,"widget_y",0,100,50);
        moduleSlider(p,R.string.widget_width,"widget_width",10,100,65);moduleSlider(p,R.string.widget_height,"widget_height",10,100,40);moduleSlider(p,R.string.widget_scale,"widget_scale",50,200,100);
        text(p,tt(R.string.widget_help),13);
    }
    private void dotSettings(){
        LinearLayout p=section(tt(R.string.dot_section));moduleBool(p,R.string.dot_enabled,"dot_enabled",false);
        dotAccessStatus=text(p,tt(tickerAccess()?R.string.ticker_access_yes:R.string.ticker_access_no),13);
        button(p,tt(R.string.ticker_access),()->{try{startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(ActivityNotFoundException e){error(e);}});
        button(p,tt(R.string.dot_apps)+" · "+NotificationDot.apps(this).size(),this::chooseDotApps);
        for(String app:NotificationDot.apps(this)){String key="dot_color_"+app;String value=Config.color(store.prefs.getString(key,"#63E6DC"),"#63E6DC");Button b=button(p,tickerAppLabel(app)+" · "+value,()->ColorPicker.show(this,tickerAppLabel(app),value,c->{store.prefs.edit().putString(key,c).apply();moduleChanged();build();}));b.setTextColor(Color.parseColor(value));}
        moduleSlider(p,R.string.module_x,"dot_x",0,100,50);moduleSlider(p,R.string.module_y,"dot_y",0,100,15);moduleSlider(p,R.string.dot_size,"dot_size",2,100,12);
        choose(p,tt(R.string.dot_clear),new String[]{tt(R.string.ticker_until_unlock),tt(R.string.ticker_until_removed)},store.prefs.getBoolean("dot_until_unlock",true)?0:1,i->{store.prefs.edit().putBoolean("dot_until_unlock",i==0).apply();moduleChanged();});
        moduleSlider(p,R.string.dot_interval,"dot_interval",1,30,3);moduleBool(p,R.string.ticker_ongoing,"dot_ongoing",false);moduleBool(p,R.string.ticker_groups,"dot_groups",false);
        check(p,tt(R.string.dot_preview),dotPreview,v->{dotPreview=v;preview.surface.simulateDot(v);});text(p,tt(R.string.dot_help),13);
    }
    private void chooseDotApps(){
        List<String> apps=tickerApps();Set<String> selected=NotificationDot.apps(this);String[] labels=new String[apps.size()];boolean[] checked=new boolean[apps.size()];for(int i=0;i<apps.size();i++){labels[i]=tickerAppLabel(apps.get(i))+" · "+apps.get(i);checked[i]=selected.contains(apps.get(i));}
        new AlertDialog.Builder(this).setTitle(tt(R.string.dot_apps)).setMultiChoiceItems(labels,checked,(d,i,value)->{if(value)selected.add(apps.get(i));else selected.remove(apps.get(i));}).setNegativeButton(tt(R.string.msg_056),null).setPositiveButton(android.R.string.ok,(d,w)->{store.prefs.edit().putStringSet("dot_apps",selected).apply();moduleChanged();build();}).show();
    }

    private void externalApps(){
        LinearLayout p=section(I18n.get(R.string.external_section));
        text(p,store.prefs.getString("external_label",I18n.get(R.string.external_none)),14);
        button(p,I18n.get(R.string.external_choose),()->{
            List<ExternalAppLauncher.Entry> apps=ExternalAppLauncher.apps(this);
            if(apps.isEmpty()){toast(I18n.get(R.string.external_empty));return;}
            String[] labels=new String[apps.size()];for(int i=0;i<apps.size();i++)labels[i]=apps.get(i).toString();
            new AlertDialog.Builder(this).setTitle(I18n.get(R.string.external_choose)).setItems(labels,(d,i)->{ExternalAppLauncher.Entry e=apps.get(i);store.prefs.edit().putString("external_activity",e.component.flattenToString()).putString("external_label",e.label).apply();build();}).setNegativeButton(I18n.get(R.string.msg_056),null).show();
        });
        button(p,I18n.get(R.string.external_start),()->{
            String selected=store.prefs.getString("external_activity","");
            for(ExternalAppLauncher.Entry e:ExternalAppLauncher.apps(this))if(e.component.flattenToString().equals(selected)){ExternalAppLauncher.launch(this,e);return;}
            toast(I18n.get(R.string.external_unavailable));
        });
                check(p,I18n.get(R.string.return_option),store.prefs.getBoolean("return_external_primary",false),v->store.prefs.edit().putBoolean("return_external_primary",v).apply());
        button(p,I18n.get(R.string.external_return),()->{changed();notificationPermission();ExternalAppLauncher.returnToTakeover(this);});
        text(p,I18n.get(R.string.return_help),13);
        text(p,I18n.get(R.string.external_help),13);
    }
    private void charging(){
        LinearLayout p=section(I18n.get(R.string.charge_section));
        check(p,I18n.get(R.string.charge_enabled),config.chargeEnabled,v->{config.chargeEnabled=v;changed();});
        choose(p,I18n.get(R.string.charge_display),new String[]{I18n.get(R.string.charge_icon),I18n.get(R.string.charge_icon_circle)},config.chargeCircle?1:0,i->{config.chargeCircle=i==1;changed();});
        check(p,I18n.get(R.string.charge_preview),chargePreview,v->{chargePreview=v;preview.surface.simulateCharge(v);});
        color(p,I18n.get(R.string.charge_text_color),()->config.chargeColor,v->config.chargeColor=v);
        color(p,I18n.get(R.string.charge_symbol_color),()->config.chargeSymbolColor,v->config.chargeSymbolColor=v);
        slider(p,I18n.get(R.string.charge_size),8,50,config.chargeSize,n->{config.chargeSize=n;changed();});
        String[] fonts={"sans","sans-serif-light","sans-serif-condensed","monospace","serif"};int selected=Arrays.asList(fonts).indexOf(config.chargeFont);
        choose(p,I18n.get(R.string.charge_font),fonts,Math.max(0,selected),i->{config.chargeFont=fonts[i];config.chargeFontFile="";changed();});
        check(p,I18n.get(R.string.msg_070),config.chargeBold,v->{config.chargeBold=v;changed();});
        button(p,I18n.get(R.string.msg_071),()->pick(CHARGE_FONT));button(p,I18n.get(R.string.msg_072),()->{config.chargeFontFile="";changed();});
        slider(p,I18n.get(R.string.charge_x),0,100,config.chargeX,n->{config.chargeX=n;changed();});
        slider(p,I18n.get(R.string.charge_y),0,100,config.chargeY,n->{config.chargeY=n;changed();});
        color(p,I18n.get(R.string.charge_ring_color),()->config.chargeRingColor,v->config.chargeRingColor=v);
        slider(p,I18n.get(R.string.charge_ring_width),1,12,config.chargeRingWidth,n->{config.chargeRingWidth=n;changed();});
        choose(p,I18n.get(R.string.charge_ring_style),new String[]{I18n.get(R.string.charge_static),I18n.get(R.string.charge_animated)},config.chargeAnimated?1:0,i->{config.chargeAnimated=i==1;changed();});
        slider(p,I18n.get(R.string.charge_animation_speed),2,60,config.chargeAnimationSeconds,n->{config.chargeAnimationSeconds=n;changed();});
        text(p,I18n.get(R.string.charge_help),13);
    }
    private void shifting() {
        LinearLayout p=section(I18n.get(R.string.msg_095));check(p,I18n.get(R.string.msg_096),config.shifting,v->{config.shifting=v;changed();});
        slider(p,I18n.get(R.string.msg_097),1,30,config.shiftRange,n->{config.shiftRange=n;changed();});slider(p,I18n.get(R.string.msg_098),10,600,config.shiftInterval,n->{config.shiftInterval=n;changed();});
        text(p,I18n.get(R.string.msg_099),13);
    }
    private void html() {
        LinearLayout p=section(I18n.get(R.string.msg_100));text(p,config.design.isEmpty()?I18n.get(R.string.msg_101):I18n.get(R.string.msg_102)+config.design,13);
        button(p,I18n.get(R.string.msg_103),()->pick(DESIGN));
        button(p,I18n.get(R.string.msg_104),()->{String id=store.active();backgroundWork(I18n.get(R.string.msg_105),()->{Config c=store.load(id);c.design=store.builtIn(id);c.mode="html";store.save(id,c);return id;},v->{config=store.load();build();});});
        button(p,I18n.get(R.string.msg_106),()->{config.design="";config.mode="native";changed();build();});
        text(p,I18n.get(R.string.msg_107),13);
        button(p,I18n.get(R.string.msg_108),()->new AlertDialog.Builder(this).setTitle(I18n.get(R.string.msg_109)).setMessage(I18n.get(R.string.msg_110)).setPositiveButton("OK",null).show());
    }
    private void notificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!store.prefs.getBoolean("notification_asked",false)){store.prefs.edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},21);}}
    private void manufacturerStart(){changed();notificationPermission();TakeoverControl.show(this);}
    private void nameDialog(String title,String initial,Consumer<String> accepted){EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setSelectAllOnFocus(true);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton(I18n.get(R.string.msg_056),null).setPositiveButton(I18n.get(R.string.msg_058),null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String name=input.getText().toString().trim();if(name.isEmpty()||name.length()>80){input.setError(I18n.get(R.string.msg_111));return;}accepted.accept(name);dialog.dismiss();}));dialog.show();}
    private void pick(int request) {
        pendingProfile=store.active();pendingRequest=request;Intent i;
        if(request==EXPORT_PROFILE){i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"MiniScreen-"+config.name.replaceAll("[^a-zA-Z0-9_-]","_")+".zip");}
        else{i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(request==IMAGE?"image/*":"*/*");if(request==FONT||request==CHARGE_FONT)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"font/ttf","font/otf","application/x-font-ttf","application/octet-stream"});}
        i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,request);}catch(ActivityNotFoundException e){error(e);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request==WIDGET){if(store!=null)build();return;}if(result!=RESULT_OK||data==null||data.getData()==null||store==null)return;
        final Uri uri=data.getData();final String id=pendingProfile.isEmpty()?store.active():pendingProfile;
        if(request!=pendingRequest)return;
        String name="";try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(Exception ignored){}
        final String fileName=name.toLowerCase(Locale.ROOT);
        if(request==EXPORT_PROFILE){backgroundWork(I18n.get(R.string.msg_112),()->{store.exportProfile(id,uri);return "";},v->toast(I18n.get(R.string.msg_113)));return;}
        backgroundWork(I18n.get(R.string.msg_114),()->{
            if(request==IMPORT_PROFILE){String newId=store.importProfile(uri);store.select(newId);return newId;}
            Config c=store.load(id);
            if(request==IMAGE){String path=store.importAsset(id,uri,"background",".img");Bitmap image=ClockView.decodeImage(store.resolve(id,path));if(image==null){store.resolve(id,path).delete();throw new IOException(I18n.get(R.string.msg_115));}image.recycle();c.image=path;}
            else if(request==FONT||request==CHARGE_FONT){String ext=fileName.endsWith(".otf")?".otf":".ttf";String path=store.importAsset(id,uri,request==CHARGE_FONT?"charge-font":"font",ext);try{Typeface.createFromFile(store.resolve(id,path));}catch(RuntimeException e){store.resolve(id,path).delete();throw new IOException(I18n.get(R.string.msg_116));}if(request==CHARGE_FONT)c.chargeFontFile=path;else c.fontFile=path;}
            else if(request==DESIGN){boolean zip=fileName.endsWith(".zip")||"application/zip".equals(getContentResolver().getType(uri));c.design=store.importDesign(id,uri,zip);c.mode="html";}
            else throw new IOException(I18n.get(R.string.msg_117));store.save(id,c);return id;
        },newId->{config=store.load();build();toast(I18n.get(R.string.msg_118));});
    }
    private interface Job {String run() throws Exception;}
    private void backgroundWork(String message,Job job,Consumer<String> done){busy=true;workStatus.setVisibility(View.VISIBLE);workStatus.setText(message);worker.execute(()->{try{String value=job.run();runOnUiThread(()->{if(isDestroyed())return;busy=false;workStatus.setVisibility(View.GONE);done.accept(value);});}catch(Exception e){failed(e);}});}
    private void failed(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;workStatus.setVisibility(View.GONE);config=store.load();build();error(e);});}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("MiniScreen Takeover").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show();}
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){if((key.startsWith("ticker_")||key.startsWith("dot_")||key.startsWith("widget_"))&&preview!=null)preview.surface.tick();if(key.startsWith("schedule_")||key.equals("external_active")||key.equals("pause_until")||key.equals("manual_off"))refreshSchedule();if(status!=null&&(key.equals("status")||key.equals("actual")))status.setText(prefs.getString("status",""));if(monitorStatus!=null&&key.equals("monitor_status"))monitorStatus.setText(prefs.getString("monitor_status",""));if(!busy&&(key.equals("revision")||key.equals("active"))){Config next=store.load();if(!next.json().toString().equals(config.json().toString())){config=next;build();}}}
    @Override protected void onResume(){super.onResume();resumed=true;if(store!=null){if(!uiLanguage.equals(store.prefs.getString("language",""))){recreate();return;}refreshTickerAccess();DisplaySchedule.reconcile(this);refreshSchedule();config=store.load();if(preview!=null){preview.surface.apply(config);preview.surface.start();}displays.registerDisplayListener(this,handler);}}
    @Override protected void onPause(){resumed=false;if(preview!=null)preview.surface.stop();if(displays!=null)displays.unregisterDisplayListener(this);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("language",uiLanguage);out.putString("pendingProfile",pendingProfile);out.putInt("pendingRequest",pendingRequest);out.putBoolean("guides",guides);out.putBoolean("chargePreview",chargePreview);out.putBoolean("tickerPreview",tickerPreview);out.putBoolean("dotPreview",dotPreview);out.putStringArray("opened",opened.toArray(new String[0]));}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(preview!=null)preview.surface.dispose();if(store!=null)store.prefs.unregisterOnSharedPreferenceChangeListener(this);worker.shutdown();super.onDestroy();}
    @Override public void onDisplayAdded(int id){if(!busy)build();}
    @Override public void onDisplayRemoved(int id){if(!busy)build();}
    @Override public void onDisplayChanged(int id){/* State changes must not interrupt editing or rebuild WebViews. */}
}
