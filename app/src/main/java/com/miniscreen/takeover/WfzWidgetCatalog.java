package com.miniscreen.takeover;

/** Geometry reconstructed from WFZMaker 2.0.1 WidgetsStorage.Init (320 px family).
 * Model numbers are the XML IDs, NOT the suffix of the preview PNG filename.
 * No editor/firmware images are bundled or used at runtime. */
final class WfzWidgetCatalog {
    private static final int[] DATE_W={100,100,100,122,134,32,159,159,162,162,150,156,106};
    private static final int[] DATE_H={24,24,24,18,20,32,33,33,18,18,18,18,21};
    static boolean metric(int data){return data>=1&&data<=5||data==8||data==10||data==12;}
    static boolean known(int data,int model){return data==6?model>=1&&model<=12:data==0?model==0:metric(data)&&model>=0&&model<=4;}
    static int width(int data,int model){
        if(data==6&&model>=1&&model<=12)return DATE_W[model];
        if(data==0&&model==0)return 320;
        if(metric(data)&&model>=0&&model<=4)return model==0||model==2?84:100;
        return 100;
    }
    static int height(int data,int model){
        if(data==6&&model>=1&&model<=12)return DATE_H[model];
        if(data==0&&model==0)return 20;
        if(metric(data)&&model>=0&&model<=4)return model==0||model==2?84:model==3?100:24;
        return 24;
    }
    static boolean black(int data,int model){return data==6?model>=8&&model<=11:model==4;}
    static boolean progressRing(int data,int model){return model==0?data==10:(model==2||model==3)&&(data==1||data==2||data==10);}
    private WfzWidgetCatalog(){}
}
