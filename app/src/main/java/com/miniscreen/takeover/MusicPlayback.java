package com.miniscreen.takeover;

/** Pure playback/ownership policy, kept independent of Android callbacks. */
final class MusicPlayback {
    // Android PlaybackState values: playing, seeking, buffering, connecting,
    // and skipping. Paused/stopped/none/error are intentionally excluded.
    static boolean active(int state){return state==3||state==4||state==5||state==6||state==8||state==9||state==10||state==11;}
    static boolean restore(boolean owned,long savedGeneration,long currentGeneration,boolean eligible){
        return owned&&savedGeneration==currentGeneration&&eligible;
    }
    private MusicPlayback(){}
}
