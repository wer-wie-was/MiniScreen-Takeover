package com.miniscreen.takeover;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** Shared one-step controls, with hold-to-repeat and accessible click actions. */
final class StepSlider {
 static void add(LinearLayout parent,SeekBar seek,Runnable changed){
  Context c=parent.getContext();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);Button minus=new Button(c),plus=new Button(c);minus.setText("−");plus.setText("+");minus.setContentDescription(I18n.get(R.string.slider_decrease));plus.setContentDescription(I18n.get(R.string.slider_increase));int size=Math.round(48*c.getResources().getDisplayMetrics().density);row.addView(minus,new LinearLayout.LayoutParams(size,size));row.addView(seek,new LinearLayout.LayoutParams(0,-2,1));row.addView(plus,new LinearLayout.LayoutParams(size,size));parent.addView(row);
  Runnable enabled=()->{minus.setEnabled(seek.getProgress()>0);plus.setEnabled(seek.getProgress()<seek.getMax());};
  for(Button button:new Button[]{minus,plus}){int step=button==minus?-1:1;Runnable move=()->{int next=Math.max(0,Math.min(seek.getMax(),seek.getProgress()+step));if(next!=seek.getProgress()){seek.setProgress(next);changed.run();}enabled.run();};button.setOnClickListener(v->move.run());button.setOnTouchListener(new View.OnTouchListener(){boolean held;int repeats;final Runnable repeat=new Runnable(){public void run(){if(!held||!button.isAttachedToWindow())return;int steps=Math.min(Math.max(1,seek.getMax()/100),1+(repeats++/10));for(int n=0;n<steps&&button.isEnabled();n++)move.run();if(button.isEnabled())button.postDelayed(this,90);}};public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){held=true;repeats=0;button.postDelayed(repeat,450);}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){held=false;button.removeCallbacks(repeat);}return false;}});}
  seek.setOnTouchListener((v,e)->{seek.post(enabled);return false;});seek.addOnLayoutChangeListener((v,a,b,d,e,f,g,h,i)->enabled.run());enabled.run();
 }
}
