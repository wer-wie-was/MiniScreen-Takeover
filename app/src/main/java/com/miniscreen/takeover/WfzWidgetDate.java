package com.miniscreen.takeover;

import java.util.Calendar;
import java.util.Locale;
import android.icu.util.ChineseCalendar;

/** Model-specific language/format is part of the watchface, not the settings language. */
final class WfzWidgetDate {
    private static final String[] EN={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
    private static final String[] CN={"日","一","二","三","四","五","六"};
    private static final String[] MONTH={"正","二","三","四","五","六","七","八","九","十","冬","腊"};
    private static final String[] DIGIT={"零","一","二","三","四","五","六","七","八","九","十"};
    static String weekday(Calendar t){return EN[t.get(Calendar.DAY_OF_WEEK)-1];}
    static String chineseWeekday(Calendar t){return "星期"+CN[t.get(Calendar.DAY_OF_WEEK)-1];}
    static String value(int model,Calendar t){
        String md=two(t.get(Calendar.MONTH)+1)+"."+two(t.get(Calendar.DAY_OF_MONTH));
        String full=String.format(Locale.ROOT,"%04d",t.get(Calendar.YEAR))+"."+md;
        switch(model){
            case 1:return md.replace('.','-');
            case 2:return weekday(t);
            case 3:return lunar(t,false);
            case 4:return full;
            case 5:return two(t.get(Calendar.DAY_OF_MONTH));
            case 6:return md+"  "+weekday(t).toUpperCase(Locale.ROOT);
            case 7:return md+"  "+chineseWeekday(t);
            case 8:return full+"  "+chineseWeekday(t);
            case 9:return full;
            case 10:return lunar(t,true)+"  "+chineseWeekday(t);
            case 11:return full+"  "+weekday(t);
            case 12:return md.replace('.','-')+"  "+weekday(t);
            default:return "--";
        }
    }
    private static String lunar(Calendar t,boolean compact){
        ChineseCalendar lunar=new ChineseCalendar(android.icu.util.TimeZone.getTimeZone(t.getTimeZone().getID()));
        lunar.setTimeInMillis(t.getTimeInMillis());
        int month=lunar.get(android.icu.util.Calendar.MONTH),day=lunar.get(android.icu.util.Calendar.DAY_OF_MONTH);
        String prefix=lunar.get(android.icu.util.Calendar.IS_LEAP_MONTH)==1?"闰":"";
        return prefix+MONTH[month]+(compact?"":"月")+lunarDay(day);
    }
    static String lunarDay(int day){
        if(day<=10)return "初"+DIGIT[day];
        if(day<20)return "十"+DIGIT[day-10];
        if(day==20)return "二十";
        if(day<30)return "廿"+DIGIT[day-20];
        return "三十";
    }
    private static String two(int n){return String.format(Locale.ROOT,"%02d",n);}
    private WfzWidgetDate(){}
}
