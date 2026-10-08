package com.miniscreen.takeover;
import android.content.*;
import android.widget.*;
/** Compact pinned banner; notes and controls live on their own page. */
final class UpdatePanel extends LinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
 private final TextView label;UpdatePanel(Context c){super(c);setOrientation(HORIZONTAL);setGravity(android.view.Gravity.CENTER_VERTICAL);int pad=Math.round(10*getResources().getDisplayMetrics().density);setPadding(pad,pad,pad,pad);setBackgroundColor(0xFF293B50);label=new TextView(c);label.setTextColor(0xFF63E6DC);addView(label,new LinearLayout.LayoutParams(0,-2,1));Button button=new Button(c);button.setText(I18n.get(R.string.update_title));button.setAllCaps(false);button.setOnClickListener(v->c.startActivity(new Intent(c,UpdateActivity.class)));addView(button);refresh();}
 private void refresh(){SharedPreferences p=AppUpdates.prefs(getContext());String tag=p.getString("update_release","");setVisibility(!tag.isEmpty()&&!tag.equals(p.getString("update_skip",""))?VISIBLE:GONE);label.setText(I18n.get(R.string.update_available)+" "+tag);}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();AppUpdates.prefs(getContext()).registerOnSharedPreferenceChangeListener(this);refresh();}
 @Override protected void onDetachedFromWindow(){AppUpdates.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);super.onDetachedFromWindow();}
 @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if(key.startsWith("update_"))refresh();}
}
