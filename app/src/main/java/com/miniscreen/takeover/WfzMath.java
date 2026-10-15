package com.miniscreen.takeover;
/** Bitmap 00 is the first positive battery bucket; 100% is the last bitmap. */
final class WfzMath {
    static int litBatterySegments(float fraction,int count){
        if(count<1||Float.isNaN(fraction)||Float.isInfinite(fraction)||fraction<=0)return 0;
        double clamped=Math.min(1,fraction);return Math.max(1,Math.min(count,(int)Math.ceil(clamped*count-0.000001)));
    }
    static float textX(float x,float anchorWidth,float textWidth,int alignment){
        return x+(alignment==72?anchorWidth-textWidth:alignment==68?(anchorWidth-textWidth)/2:0);
    }
    static int batteryFrame(int level,int count){
        if(count<1)throw new IllegalArgumentException("Empty battery sequence");
        int clamped=Math.max(0,Math.min(100,level));
        return Math.max(0,Math.min(count-1,(clamped*count+99)/100-1));
    }
}
