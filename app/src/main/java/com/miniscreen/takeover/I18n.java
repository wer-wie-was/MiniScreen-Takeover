package com.miniscreen.takeover;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.*;
import java.util.regex.*;

/** Per-app language without changing Android's global locale or portable profiles. */
final class I18n {
    private static Context app,cached;
    private static String cacheKey="";
    static void init(Context context){app=context.getApplicationContext();}
    static Context wrap(Context base){return withLanguage(base,base.getSharedPreferences("takeover",0).getString("language",""));}
    private static Context withLanguage(Context base,String code){
        if(code.isEmpty())return base;
        Configuration config=new Configuration(base.getResources().getConfiguration());
        Locale locale=Locale.forLanguageTag(code.equals("zh")?"zh-Hans":code);
        config.setLocales(new LocaleList(locale));config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }
    private static synchronized Context context(){
        String code=app.getSharedPreferences("takeover",0).getString("language","");
        String key=code+":"+app.getResources().getConfiguration().getLocales().toLanguageTags();
        if(cached==null||!key.equals(cacheKey)){cached=wrap(app);cacheKey=key;}
        return cached;
    }
    static String get(int id){return context().getString(id);}
    static Locale locale(){return context().getResources().getConfiguration().getLocales().get(0);}
    static void select(Context base,String code){
        Context old=context(),next=withLanguage(app,code);
        SharedPreferences prefs=base.getSharedPreferences("takeover",0);
        // Persisted status text can contain display numbers and system error details.
        Map<String,String> replacements=new HashMap<>();
        for(int id:TranslationIds.ALL){String before=old.getString(id),after=next.getString(id);if(!before.equals(after))replacements.put(before,after);}
        List<String> keys=new ArrayList<>(replacements.keySet());keys.sort((a,b)->Integer.compare(b.length(),a.length()));
        StringBuilder pattern=new StringBuilder();for(String key:keys){if(pattern.length()>0)pattern.append('|');pattern.append(Pattern.quote(key));}
        SharedPreferences.Editor edit=prefs.edit();
        if(pattern.length()>0){Pattern compiled=Pattern.compile(pattern.toString());for(String name:new String[]{"status","monitor_status"}){
            String value=prefs.getString(name,"");Matcher matcher=compiled.matcher(value);StringBuffer result=new StringBuffer();
            while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(replacements.get(matcher.group())));
            matcher.appendTail(result);edit.putString(name,result.toString());
        }}
        edit.putString("language",code).apply();
        synchronized(I18n.class){cached=null;}
    }
}
