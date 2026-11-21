package com.example.namaz.asistani;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
public class PrayerAudioService extends Service implements android.hardware.SensorEventListener {
    private static final String CHANNEL_ID = "prayer_audio_playback";
    private static final int NOTIFICATION_ID = 8421;
    private android.hardware.SensorManager sensorManager;
    private android.hardware.Sensor accelerometer;
    private long faceDownSince = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static PrayerAudioService activeService;
    private MediaPlayer player;
    private int activeStartId;
    private boolean previewPlayback;
    private final Runnable stopRunnable = new Runnable() { @Override public void run() { finishAndStop(activeStartId); } };
    @Override public void onCreate() {
        super.onCreate(); activeService = this;
        try {
            sensorManager = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        } catch (Exception ignored) {}
    }
    // Telefon yuz ustu (ekran yere bakacak sekilde) cevrilince sesi durdur.
    @Override public void onSensorChanged(android.hardware.SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 3) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        float norm = (float) Math.sqrt(x * x + y * y + z * z);
        if (norm < 1f) return;
        float zn = z / norm;
        if (zn < -0.75f) {
            if (faceDownSince == 0) faceDownSince = System.currentTimeMillis();
            else if (System.currentTimeMillis() - faceDownSince > 450) {
                faceDownSince = 0;
                handler.post(() -> finishAndStop(activeStartId));
            }
        } else { faceDownSince = 0; }
    }
    @Override public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) { }
    static void stopPreview() { PrayerAudioService service = activeService; if (service != null && service.previewPlayback) service.handler.post(() -> service.finishAndStop(service.activeStartId)); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        activeStartId = startId;
        previewPlayback = intent != null && intent.getBooleanExtra("play_prayer_sound_preview", false);
        stopCurrentPlayer();
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        ensureChannel();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Namaz Asistanı").setContentText("Bildirim sesi çalıyor").setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true);
        startForeground(NOTIFICATION_ID, builder.build());
        String uri = intent.getStringExtra("play_prayer_sound_uri");
        String channel = intent.getStringExtra("play_prayer_sound_channel");
        // Uzun ezanlar kesilmesin: ust sinir 180 saniye
        int seconds = Math.max(1, Math.min(180, intent.getIntExtra("play_prayer_sound_seconds", 5)));
        if (uri == null || uri.isEmpty()) { finishAndStop(startId); return START_NOT_STICKY; }
        try {
            // Dosya saglayici adresleri icin okuma izni (Android 7+)
            try { getApplicationContext().grantUriPermission(getPackageName(), Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            MediaPlayer next = new MediaPlayer(); player = next;
            int stream = "alarm".equals(channel) ? AudioManager.STREAM_ALARM : ("ringtone".equals(channel) ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC);
            AudioAttributes audioAttributes = new AudioAttributes.Builder().setLegacyStreamType(stream).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            next.setAudioAttributes(audioAttributes);
            next.setDataSource(this, Uri.parse(uri));
            next.setLooping(true);
            next.setOnPreparedListener(mp -> { if (player != mp) return; try { mp.start(); registerFaceDown(); handler.postDelayed(stopRunnable, seconds * 1000L); } catch (Exception e) { finishAndStop(startId); } });
            final int[] retry = { 0 };
            next.setOnErrorListener((mp, what, extra) -> {
                // Bazi cihazlar ilk denemede dosyayi acamaz; 2 kez daha dene.
                if (retry[0] < 2) { retry[0]++; try { mp.reset(); mp.setDataSource(PrayerAudioService.this, Uri.parse(uri)); mp.prepareAsync(); return true; } catch (Exception ignored) {} }
                finishAndStop(startId); return true; });
            next.prepareAsync();
        } catch (Exception e) { finishAndStop(startId); }
        return START_NOT_STICKY;
    }
    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) { NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Bildirim sesi", NotificationManager.IMPORTANCE_LOW); channel.setSound(null, null); channel.setShowBadge(false); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel); }
    }
    private void registerFaceDown() {
        try { if (sensorManager != null && accelerometer != null) sensorManager.registerListener(this, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI); } catch (Exception ignored) {}
    }
    private void unregisterFaceDown() {
        try { if (sensorManager != null) sensorManager.unregisterListener(this); } catch (Exception ignored) {}
        faceDownSince = 0;
    }
    private void stopCurrentPlayer() { unregisterFaceDown(); handler.removeCallbacks(stopRunnable); if (player != null) { try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {} try { player.release(); } catch (Exception ignored) {} player = null; } }
    private void finishAndStop(int startId) { stopCurrentPlayer(); if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true); stopSelf(startId); }
    @Override public void onDestroy() { unregisterFaceDown(); stopCurrentPlayer(); handler.removeCallbacksAndMessages(null); if (activeService == this) activeService = null; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}