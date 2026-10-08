package com.miniscreen.takeover;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

final class BrightnessCurveDialog {
 static void show(Context c,Config config,Consumer<JSONArray> save){
  LinearLayout root=new LinearLayout(c);root.setOrientation(1);int pad=Math.round(16*c.getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);TextView help=new TextView(c);help.setText(I18n.get(R.string.brightness_curve_help));root.addView(help);
  Graph graph=new Graph(c,BrightnessCurve.normalize(config.brightnessPoints,config.brightness));root.addView(graph,new LinearLayout.LayoutParams(-1,pad*19));Button reset=new Button(c);reset.setText(I18n.get(R.string.brightness_curve_reset));reset.setOnClickListener(v->graph.load(BrightnessCurve.normalize(null,config.brightness)));root.addView(reset);
  new AlertDialog.Builder(c).setTitle(I18n.get(R.string.brightness_curve_edit)).setView(root).setNegativeButton(android.R.string.cancel,null).setPositiveButton(I18n.get(R.string.brightness_curve_apply),(d,w)->save.accept(graph.json())).show();
 }
 static final class Graph extends View {
  final ArrayList<int[]> points=new ArrayList<>();final Paint paint=new Paint(3);final float density;int selected=-1;float downX,downY;boolean moved;
  Graph(Context c,JSONArray initial){super(c);density=getResources().getDisplayMetrics().density;setLayerType(View.LAYER_TYPE_SOFTWARE,null);load(initial);}
  void load(JSONArray a){points.clear();for(int i=0;i<a.length();i++){JSONArray p=a.optJSONArray(i);points.add(new int[]{p.optInt(0),p.optInt(1)});}selected=-1;invalidate();}
  JSONArray json(){JSONArray a=new JSONArray();for(int[] p:points)a.put(new JSONArray().put(p[0]).put(p[1]));return BrightnessCurve.normalize(a,25);}
  float left(){return 42*density;}float right(){return getWidth()-18*density;}float top(){return 24*density;}float bottom(){return getHeight()-36*density;}
  float x(int minute){return left()+(right()-left())*minute/1440f;}float y(int value){return bottom()-(bottom()-top())*value/100f;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setTextSize(11*density);paint.setStrokeWidth(density);for(int v=0;v<=100;v+=25){paint.setColor(0xFF405060);c.drawLine(left(),y(v),right(),y(v),paint);paint.setColor(0xFFCBD5E1);c.drawText(v+"%",0,y(v)+4*density,paint);}for(int h=0;h<=24;h+=6){paint.setColor(0xFF405060);c.drawLine(x(h*60),top(),x(h*60),bottom(),paint);paint.setColor(0xFFCBD5E1);c.drawText(String.format(Locale.ROOT,"%02d:00",h),x(h*60)-14*density,bottom()+20*density,paint);}paint.setColor(0xFF63E6DC);paint.setStrokeWidth(2*density);for(int i=1;i<points.size();i++){int[] a=points.get(i-1),b=points.get(i);c.drawLine(x(a[0]),y(a[1]),x(b[0]),y(b[1]),paint);}for(int[] p:points)c.drawCircle(x(p[0]),y(p[1]),6*density,paint);}
  @Override public boolean onTouchEvent(MotionEvent e){if(right()<=left()||bottom()<=top())return false;switch(e.getActionMasked()){
   case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();moved=false;selected=-1;float best=22*density;for(int i=0;i<points.size();i++){int[] p=points.get(i);float distance=(float)Math.hypot(x(p[0])-downX,y(p[1])-downY);if(distance<best){best=distance;selected=i;}}if(selected<0){if(points.size()>=100)return true;int minute=Config.clamp(Math.round((downX-left())*1440/(right()-left())),1,1439);int value=Config.clamp(Math.round((bottom()-downY)*100/(bottom()-top())),0,100);for(int[] p:points)if(p[0]==minute)return true;points.add(new int[]{minute,value});points.sort((a,b)->Integer.compare(a[0],b[0]));for(int i=0;i<points.size();i++)if(points.get(i)[0]==minute)selected=i;}getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
   case MotionEvent.ACTION_MOVE:if(selected<0)return true;if(Math.hypot(e.getX()-downX,e.getY()-downY)>6*density)moved=true;if(moved){int[] p=points.get(selected);p[1]=Config.clamp(Math.round((bottom()-e.getY())*100/(bottom()-top())),0,100);if(selected==0||selected==points.size()-1){points.get(0)[1]=p[1];points.get(points.size()-1)[1]=p[1];}else p[0]=Config.clamp(Math.round((e.getX()-left())*1440/(right()-left())),points.get(selected-1)[0]+1,points.get(selected+1)[0]-1);invalidate();}return true;
   case MotionEvent.ACTION_UP:if(selected>=0&&!moved)edit(selected);getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
   case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;
  }return true;}
  @Override public boolean performClick(){super.performClick();return true;}
  void edit(int index){int[] point=points.get(index);boolean endpoint=index==0||index==points.size()-1;LinearLayout root=new LinearLayout(getContext());root.setOrientation(1);EditText time=new EditText(getContext()),level=new EditText(getContext());time.setSingleLine(true);time.setText(String.format(Locale.ROOT,"%02d:%02d",point[0]/60,point[0]%60));time.setHint(I18n.get(R.string.brightness_curve_time));time.setEnabled(!endpoint);level.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);level.setText(Integer.toString(point[1]));level.setHint(I18n.get(R.string.brightness_curve_level));root.addView(time);root.addView(level);
   AlertDialog.Builder builder=new AlertDialog.Builder(getContext()).setTitle(I18n.get(R.string.brightness_curve_point)).setView(root).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null);if(!endpoint)builder.setNeutralButton(I18n.get(R.string.brightness_curve_delete),(d,w)->{points.remove(index);invalidate();});AlertDialog dialog=builder.create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int n=Integer.parseInt(level.getText().toString());if(n<0||n>100)throw new IllegalArgumentException();int minute=point[0];if(!endpoint){String[] parts=time.getText().toString().split(":");if(parts.length!=2)throw new IllegalArgumentException();int hour=Integer.parseInt(parts[0]),m=Integer.parseInt(parts[1]);if(hour<0||hour>23||m<0||m>59)throw new IllegalArgumentException();minute=hour*60+m;if(minute<=0||minute>=1440)throw new IllegalArgumentException();for(int[] p:points)if(p!=point&&p[0]==minute)throw new IllegalArgumentException();}point[0]=minute;point[1]=n;if(endpoint){points.get(0)[1]=n;points.get(points.size()-1)[1]=n;}points.sort((a,b)->Integer.compare(a[0],b[0]));invalidate();dialog.dismiss();}catch(RuntimeException e){level.setError(I18n.get(R.string.brightness_curve_invalid));time.setError(I18n.get(R.string.brightness_curve_invalid));}}));dialog.show();}
 }
}
