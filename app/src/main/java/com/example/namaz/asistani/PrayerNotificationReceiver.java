package com.example.namaz.asistani;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;
public class PrayerNotificationReceiver extends BroadcastReceiver {
    private static int stableId(String title, long at, int remaining) { return (title + "|" + at + "|" + remaining).hashCode() & 0x7fffffff; }
    @Override public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        String title = intent.getStringExtra("title"), message = intent.getStringExtra("message"), soundMode = intent.getStringExtra("soundMode"), channelType = intent.getStringExtra("channelType");
        if (title == null) title = context.getString(R.string.app_name);
        if (message == null) message = "Namaz vakti";
        Uri sound = null;
        String custom = intent.getStringExtra("soundUri");
        if ("file".equals(soundMode) && custom != null && !custom.isEmpty()) sound = Uri.parse(custom);
        else if ("alarm".equals(soundMode)) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        else if ("ringtone".equals(soundMode)) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        else if (!"off".equals(soundMode)) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        String channelId = "prayer_notice_" + (soundMode == null ? "notification" : soundMode) + "_" + (channelType == null ? "media" : channelType) + "_" + (custom == null ? 0 : custom.hashCode());
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId, "Namaz vakti bildirimleri", NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (sound == null) channel.setSound(null, null);
            else channel.setSound(sound, new AudioAttributes.Builder().setUsage("alarm".equals(channelType) ? AudioAttributes.USAGE_ALARM : ("ringtone".equals(channelType) ? AudioAttributes.USAGE_NOTIFICATION_RINGTONE : AudioAttributes.USAGE_MEDIA)).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, channelId) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(message).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER);
        if (Build.VERSION.SDK_INT < 26 && sound != null) builder.setSound(sound);
        int notificationId = intent.getIntExtra("notificationId", stableId(title, System.currentTimeMillis(), 1));
        manager.notify(notificationId, builder.build());
        int remaining = intent.getIntExtra("repeatRemaining", 1), intervalMinutes = intent.getIntExtra("repeatIntervalMinutes", 1);
        if (remaining > 1 && intervalMinutes >= 0) {
            long nextAt = System.currentTimeMillis() + Math.max(250L, intervalMinutes * 60000L);
            Intent next = new Intent(context, PrayerNotificationReceiver.class); next.putExtras(intent); next.putExtra("repeatRemaining", remaining - 1);
            int requestId = stableId(title, nextAt, remaining - 1); next.putExtra("notificationId", notificationId);
            PendingIntent pending = PendingIntent.getBroadcast(context, requestId, next, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending);
            else if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending); else alarms.set(AlarmManager.RTC_WAKEUP, nextAt, pending);
            Set<String> ids = new HashSet<>(context.getSharedPreferences("configured_prayer_alarms", 0).getStringSet("ids", java.util.Collections.emptySet())); ids.add(String.valueOf(requestId));
            context.getSharedPreferences("configured_prayer_alarms", 0).edit().putStringSet("ids", ids).apply();
        }
    }
}