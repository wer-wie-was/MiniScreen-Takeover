package com.miniscreen.takeover;
import java.util.*;

/** Converts internal renderer diagnostics into deduplicated user explanations. */
final class WfzReport {
    final Set<Integer> limitations=new LinkedHashSet<>(), notes=new LinkedHashSet<>();
    void add(String raw){
        String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if(s.isEmpty()||s.equals("support26w"))return;
        if(s.startsWith("health:")){notes.add(R.string.wfz_note_health);return;}
        if(s.contains("watch connectivity")&&!s.contains("phone battery")){limitations.add(R.string.wfz_note_connection);return;}
        if(s.contains("phone battery")){notes.add(R.string.wfz_note_battery);
            if(s.contains("connectivity"))limitations.add(R.string.wfz_note_connection);return;}
        int message;
        if(s.contains("weather source"))message=R.string.wfz_note_weather;
        else if(s.contains("workout distance")||s.contains("floors source"))message=R.string.wfz_note_activity;
        else if(s.contains("reconstructed model")||s.contains("plain text fallback")||s.startsWith("font:")||s.startsWith("system font:"))message=R.string.wfz_note_appearance;
        else if(s.startsWith("weekday images:"))message=R.string.wfz_note_weekday;
        else if(s.startsWith("battery image sequence:"))message=R.string.wfz_note_battery_images;
        else if(s.equals("addtiveimage")||s.startsWith("graduation: unavailable")||s.startsWith("missing widget mask:"))message=R.string.wfz_note_image;
        else if(s.startsWith("day @")||s.startsWith("month @")||s.startsWith("weekday @")||s.startsWith("numeric/localized calendar:"))message=R.string.wfz_note_date;
        else if(s.contains(" @")||s.startsWith("alignment:")||s.startsWith("nested elements:"))message=R.string.wfz_note_layout;
        else message=R.string.wfz_note_missing;
        limitations.add(message);
    }
    int status(){return limitations.isEmpty()?R.string.wfz_supported:R.string.wfz_partial;}
    String details(){
        StringBuilder text=new StringBuilder();
        for(int message:limitations)text.append("\n• ").append(I18n.get(message));
        for(int message:notes)text.append("\n• ").append(I18n.get(message));
        return text.toString();
    }
}
