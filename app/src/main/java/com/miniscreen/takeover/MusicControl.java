package com.miniscreen.takeover;

import android.content.*;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** Device-local media pause. The notification listener grants access to media sessions. */
final class MusicControl {
    static final String ENABLED="music_pause_enabled",ACTIVE="music_pause_active";
    private static final String OWNED="music_pause_owned",GENERATION="music_pause_generation";
    private static final String BOOT="music_pause_boot";
    private static final long RESUME_DELAY=3000;
    private static MusicControl instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final Map<MediaSession.Token,Session> sessions=new HashMap<>();
    private boolean listening,playing,resumePending;
    private final Runnable refreshTask=()->refreshSessions();
    private final Runnable evaluateTask=()->evaluate();
    private final Runnable resumeTask=()->{resumePending=false;if(!playing)finish();};
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionListener=controllers->replaceSessions(controllers);
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(ENABLED.equals(key)){main.post(this::configure);return;}
        if(key.equals("enabled")||key.equals("pause_until")||key.equals("only_locked")||key.equals("external_active")
            ||key.equals("yielded")||key.equals("manual_off")||key.equals("manual_off_until")||key.equals("power_generation")
            ||key.startsWith("schedule_")||key.startsWith("automation_")){
            main.removeCallbacks(evaluateTask);main.post(evaluateTask);
        }
    };
    private final ContentObserver accessChanges=new ContentObserver(main){@Override public void onChange(boolean self){configure();}};
    static void init(Context c){if(instance==null){instance=new MusicControl(c.getApplicationContext());instance.configure();}}
    static boolean blocked(SharedPreferences p){return p.getBoolean(ENABLED,false)&&p.getBoolean(ACTIVE,false);}
    static void refresh(Context c){init(c);instance.main.removeCallbacks(instance.refreshTask);instance.main.post(instance::configure);}
    static void setEnabled(Context c,boolean enabled){init(c);instance.prefs.edit().putBoolean(ENABLED,enabled).apply();instance.configure();TakeoverControl.sync(c);}
    static boolean hasAccess(Context c){
        String flat=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        ComponentName own=new ComponentName(c,TickerListener.class);
        if(flat!=null)for(String item:flat.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))return true;
        return false;
    }
    private MusicControl(Context c){context=c;prefs=TakeoverControl.prefs(c);manager=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);listener=new ComponentName(c,TickerListener.class);
        // A process restart may resume an owned pause, but a reboot must follow
        // the existing restore-on-boot setting instead of an old music snapshot.
        int boot=bootCount();if(boot<0||prefs.getInt(BOOT,-1)!=boot)prefs.edit().putBoolean(OWNED,false).apply();
        prefs.registerOnSharedPreferenceChangeListener(changes);
        c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("enabled_notification_listeners"),false,accessChanges);
    }
    private int bootCount(){try{return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}catch(RuntimeException e){return -1;}}
    private void configure(){
        if(!prefs.getBoolean(ENABLED,false)||!hasAccess(context)||manager==null){detach();playing=false;finish();return;}
        try{if(!listening){manager.addOnActiveSessionsChangedListener(sessionListener,listener,main);listening=true;}refreshSessions();}
        catch(RuntimeException e){unavailable();}
    }
    private void refreshSessions(){
        if(!listening)return;
        try{replaceSessions(manager.getActiveSessions(listener));}catch(RuntimeException e){unavailable();}
    }
    private void unavailable(){detach();playing=false;finish();}
    private void detach(){
        if(listening){try{manager.removeOnActiveSessionsChangedListener(sessionListener);}catch(RuntimeException ignored){}listening=false;}
        for(Session s:sessions.values())s.close();sessions.clear();main.removeCallbacks(refreshTask);
    }
    private void replaceSessions(List<MediaController> controllers){
        if(!listening)return;
        Set<MediaSession.Token> keep=new HashSet<>();
        if(controllers!=null)for(MediaController controller:controllers){
            MediaSession.Token token=controller.getSessionToken();keep.add(token);
            if(!sessions.containsKey(token)){
                Session session=new Session(controller);sessions.put(token,session);
                try{controller.registerCallback(session,main);}catch(RuntimeException e){session.dead=true;}
            }
        }
        Iterator<Map.Entry<MediaSession.Token,Session>> it=sessions.entrySet().iterator();
        while(it.hasNext()){Session session=it.next().getValue();if(!keep.contains(session.controller.getSessionToken())){session.close();it.remove();}}
        evaluate();
    }
    private final class Session extends MediaController.Callback {
        final MediaController controller;boolean dead;
        Session(MediaController c){controller=c;}
        void close(){try{controller.unregisterCallback(this);}catch(RuntimeException ignored){}}
        @Override public void onPlaybackStateChanged(PlaybackState state){evaluate();}
        @Override public void onAudioInfoChanged(MediaController.PlaybackInfo info){evaluate();}
        @Override public void onSessionDestroyed(){dead=true;evaluate();main.removeCallbacks(refreshTask);main.post(refreshTask);}
        boolean playing(){
            if(dead)return false;
            try{
                MediaController.PlaybackInfo info=controller.getPlaybackInfo();
                int usage=info==null||info.getAudioAttributes()==null?AudioAttributes.USAGE_UNKNOWN:info.getAudioAttributes().getUsage();
                if(usage!=AudioAttributes.USAGE_MEDIA&&usage!=AudioAttributes.USAGE_UNKNOWN)return false;
                PlaybackState state=controller.getPlaybackState();return state!=null&&MusicPlayback.active(state.getState());
            }catch(RuntimeException e){return false;}
        }
    }
    private boolean eligible(){return TakeoverControl.wantedWithoutMusic(context)&&!prefs.getBoolean("yielded",false);}
    private void evaluate(){
        if(!prefs.getBoolean(ENABLED,false)||!listening)return;
        playing=false;for(Session session:sessions.values())if(session.playing()){playing=true;break;}
        if(playing){
            main.removeCallbacks(resumeTask);resumePending=false;
            if(!prefs.getBoolean(ACTIVE,false))prefs.edit().putBoolean(ACTIVE,true).apply();
            long generation=prefs.getLong("power_generation",0);
            boolean owned=prefs.getBoolean(OWNED,false)&&prefs.getLong(GENERATION,-1)==generation;
            if(!owned&&eligible()&&(WatchService.musicEligible()||ScreenActivity.visibleOn(1))){
                // Music OFF preserves manual/scheduled intent and keeps the foreground monitor.
                DisplayPower.offForMusic(context);
                prefs.edit().putBoolean(OWNED,true).putInt(BOOT,bootCount()).putLong(GENERATION,prefs.getLong("power_generation",0)).apply();
                WatchService.musicChanged();
            }
        }else if(prefs.getBoolean(ACTIVE,false)&&!resumePending){resumePending=true;main.postDelayed(resumeTask,RESUME_DELAY);}
    }
    private void finish(){
        main.removeCallbacks(resumeTask);resumePending=false;
        boolean active=prefs.getBoolean(ACTIVE,false),owned=prefs.getBoolean(OWNED,false);
        long generation=prefs.getLong("power_generation",0);
        boolean restore=MusicPlayback.restore(owned,prefs.getLong(GENERATION,-1),generation,eligible());
        SharedPreferences.Editor edit=prefs.edit().putBoolean(ACTIVE,false).putBoolean(OWNED,false).remove(GENERATION);
        // Do not cancel a newer manual/scheduled OFF command.
        if(restore)edit.putLong("power_generation",generation+1);
        edit.apply();
        if(restore){prefs.edit().putBoolean("yielded",false).apply();DisplaySchedule.plan(context);
            if(!WatchService.restoreAfterMusic())TakeoverControl.start(context,TakeoverControl.SHOW);
        }else if(active){WatchService.musicChanged();DisplaySchedule.reconcile(context);}
    }
}
