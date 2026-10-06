package com.miniscreen.takeover;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

final class ColorPicker {
    interface Result {void chosen(String color);}
    static void show(Context c,String title,String initial,Result result) {
        LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*c.getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
        float[] hsv=new float[3];Color.colorToHSV(Color.parseColor(initial),hsv);
        EditText hex=new EditText(c);hex.setSingleLine(true);hex.setHint("#RRGGBB");
        TextView swatch=new TextView(c);swatch.setText(I18n.get(R.string.msg_119));swatch.setGravity(Gravity.CENTER);root.addView(swatch,new LinearLayout.LayoutParams(-1,pad*2));
        SV square=new SV(c,hsv);root.addView(square,new LinearLayout.LayoutParams(-1,pad*9));
        TextView hueLabel=new TextView(c);hueLabel.setText(I18n.get(R.string.msg_120));root.addView(hueLabel);
        SeekBar hue=new SeekBar(c);hue.setMax(359);hue.setProgress((int)hsv[0]);root.addView(hue);root.addView(hex);
        final boolean[] updating={false};
        Runnable sync=()->{updating[0]=true;String value=String.format(Locale.ROOT,"#%06X",Color.HSVToColor(hsv)&0xffffff);hex.setText(value);swatch.setBackgroundColor(Color.HSVToColor(hsv));swatch.setTextColor(hsv[2]>.65&&hsv[1]<.6?Color.BLACK:Color.WHITE);square.invalidate();updating[0]=false;};
        square.change=sync;
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){if(user){hsv[0]=n;sync.run();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(updating[0])return;try{if(s.length()!=7)return;int color=Color.parseColor(s.toString());Color.colorToHSV(color,hsv);hue.setProgress((int)hsv[0]);square.invalidate();swatch.setBackgroundColor(color);}catch(IllegalArgumentException ignored){}}public void afterTextChanged(Editable e){}});
        sync.run();AlertDialog dialog=new AlertDialog.Builder(c).setTitle(title).setView(root).setNegativeButton(I18n.get(R.string.msg_056),null).setPositiveButton(I18n.get(R.string.msg_121),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String value=hex.getText().toString().trim();if(!value.matches("#[0-9a-fA-F]{6}")){hex.setError(I18n.get(R.string.msg_122));return;}result.chosen(value.toUpperCase(Locale.ROOT));dialog.dismiss();}));dialog.show();
    }
    private static final class SV extends View {
        private final float[] hsv;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Runnable change;
        SV(Context c,float[] hsv){super(c);this.hsv=hsv;setContentDescription(I18n.get(R.string.msg_123));}
        @Override protected void onDraw(Canvas c){paint.setShader(new LinearGradient(0,0,getWidth(),0,Color.WHITE,Color.HSVToColor(new float[]{hsv[0],1,1}),Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(new LinearGradient(0,0,0,getHeight(),Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(Color.WHITE);c.drawCircle(hsv[1]*getWidth(),(1-hsv[2])*getHeight(),9,paint);paint.setStyle(Paint.Style.FILL);}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(true);hsv[1]=Math.max(0,Math.min(1,e.getX()/getWidth()));hsv[2]=1-Math.max(0,Math.min(1,e.getY()/getHeight()));if(change!=null)change.run();if(e.getAction()==MotionEvent.ACTION_UP)performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
