package com.miniscreen.takeover;

/** Stateful image-space centre; settings never select another time-based random segment. */
final class WindowPan {
    private double u=.5,v=.5,angle=.63,target=.63;
    private long last=-1,pauseUntil,nextTurn;
    private int zoom=-1,turn;
    private static double clamp(double n,double lo,double hi){return Math.max(lo,Math.min(hi,n));}
    void reset(){u=v=.5;angle=target=.63;last=-1;zoom=-1;pauseUntil=nextTurn=0;turn=0;}
    private double random(){double n=Math.sin(++turn*12.9898+78.233)*43758.5453;return n-Math.floor(n);}
    void update(long now,int newZoom,int speed,boolean moving,long externalPause,
                float iw,float ih,float width,float height,float extra,float scale){
        if(zoom!=-1&&zoom!=newZoom)pauseUntil=now+1000;
        zoom=newZoom;
        double minU=Math.min(.5,(width/2+extra)/iw),minV=Math.min(.5,(height/2+extra)/ih);
        u=clamp(u,minU,1-minU);v=clamp(v,minV,1-minV);
        double dt=last<0?0:clamp((now-last)/1000.0,0,.1);last=now;
        boolean external=externalPause>now&&externalPause-now<=2000;
        if(!moving||external||now<pauseUntil){nextTurn=now+4000;return;}
        if(now>=nextTurn){target=angle+(random()-.5)*Math.PI;nextTurn=now+4000+(long)(random()*6000);}
        double pixels=speed*scale;
        double x=u*iw,y=v*ih,left=minU*iw,right=(1-minU)*iw,top=minV*ih,bottom=(1-minV)*ih;
        // Turn toward the interior before reaching an edge; retain full speed along the curve.
        double look=Math.max(20*scale,pixels*1.5),sx=Math.cos(target),sy=Math.sin(target);
        if(right-left>1){sx+=Math.max(0,1-(x-left)/look)*2; sx-=Math.max(0,1-(right-x)/look)*2;}
        if(bottom-top>1){sy+=Math.max(0,1-(y-top)/look)*2; sy-=Math.max(0,1-(bottom-y)/look)*2;}
        double wanted=Math.atan2(sy,sx),delta=Math.atan2(Math.sin(wanted-angle),Math.cos(wanted-angle));
        angle+=clamp(delta,-1.2*dt,1.2*dt);
        double vx=Math.cos(angle),vy=Math.sin(angle);
        if(right-left<=1){vx=0;vy=vy<0?-1:1;}
        if(bottom-top<=1){vy=0;vx=vx<0?-1:1;}
        double nx=x+vx*pixels*dt,ny=y+vy*pixels*dt;
        if(nx<left||nx>right){angle=Math.PI-angle;target=angle;}
        if(ny<top||ny>bottom){angle=-angle;target=angle;}
        u=clamp(nx/iw,minU,1-minU);v=clamp(ny/ih,minV,1-minV);
    }
    float left(float iw,float width){return (float)(width/2-iw*u);}
    float top(float ih,float height){return (float)(height/2-ih*v);}
}
