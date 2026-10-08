package com.miniscreen.takeover;
import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
public final class UpdateInstallReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent result){if(!(c.getPackageName()+".UPDATE_RESULT").equals(result.getAction()))return;int status=result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirmation=result.getParcelableExtra(Intent.EXTRA_INTENT);if(confirmation==null){AppUpdates.state(c,"failed");return;}if(!UpdateActivity.confirmIfForeground(confirmation)){AppUpdates.state(c,"ready");try{PendingIntent open=PendingIntent.getActivity(c,50502,confirmation,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AppUpdates.notifications(c).notify(AppUpdates.NOTICE,new Notification.Builder(c,AppUpdates.CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(I18n.get(R.string.update_confirm)).setContentIntent(open).setAutoCancel(true).build());}catch(SecurityException e){AppUpdates.state(c,"ready");}}}else if(status==PackageInstaller.STATUS_SUCCESS){AppUpdates.apk(c).delete();AppUpdates.prefs(c).edit().remove("update_ready").remove("update_release").apply();AppUpdates.state(c,"current");}else AppUpdates.state(c,"failed");}
}
