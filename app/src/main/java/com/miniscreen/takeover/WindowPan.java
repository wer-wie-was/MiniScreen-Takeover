package com.miniscreen.takeover;

/** Smooth reproducible random targets; identical algorithm in the included HTML design. */
final class WindowPan {
    private static double target(long segment,int axis){
        double n=Math.sin(segment*12.9898+axis*78.233)*43758.5453;
        return n-Math.floor(n);
    }
    static float position(long now,int seconds,int axis,boolean moving){
        if(!moving)return .5f;
        double phase=now/(seconds*1000.0);long segment=(long)Math.floor(phase);
        double t=phase-segment;t=t*t*(3-2*t);
        return (float)(target(segment,axis)*(1-t)+target(segment+1,axis)*t);
    }
}
