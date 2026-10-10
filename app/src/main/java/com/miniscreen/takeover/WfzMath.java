package com.miniscreen.takeover;
/** Bitmap 00 is the first positive battery bucket; 100% is the last bitmap. */
final class WfzMath {
    static int batteryFrame(int level,int count){
        if(count<1)throw new IllegalArgumentException("Empty battery sequence");
        int clamped=Math.max(0,Math.min(100,level));
        return Math.max(0,Math.min(count-1,(clamped*count+99)/100-1));
    }
}
