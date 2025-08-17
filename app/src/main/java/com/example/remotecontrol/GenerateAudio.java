package com.example.remotecontrol;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.IBinder;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.graphics.drawable.IconCompat;

public class GenerateAudio extends Service {
    private int duration, amplitude;
    private int sampleRate;
    private int numSamples;
    private byte[] generatedTone;
    private double[] sample;
    private NotificationManagerCompat notificationManagerCompat;

    private AudioTrack audioTrack;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate(){
        bindService();
    }

    private void bindService(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        NotificationChannel notificationChannel = new NotificationChannel("11", "11", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(notificationChannel);

        Notification.Builder notification = new Notification.Builder(getBaseContext(), "11")
                                                            .setContentText("Sending signal in background")
                                                            .setContentTitle("Connected")
                                                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                            .setOngoing(true)
                                                            .setAutoCancel(true)
                                                            .setPriority(Notification.PRIORITY_DEFAULT);

        ServiceCompat.startForeground(this,11,notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id){
        duration = intent.getIntExtra("duration", 10);
        amplitude = intent.getIntExtra("amplitude", 32768);

        init();
        generateTone();
        playAudio();
        if(audioTrack.getState()==AudioTrack.PLAYSTATE_STOPPED) audioTrack.play();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy(){
        audioTrack.flush();
        audioTrack.stop();
    }

    private void init(){
        sampleRate = 8000;
        numSamples = sampleRate*duration;
        generatedTone = new byte[numSamples*2];
        sample = new double[numSamples];
    }

    private void generateTone(){
        for(int i=0;i<numSamples;++i){
            double angle = Math.PI/2;
            sample[i] = Math.sin(angle);
        }

        for(int i = 0;i<numSamples;){
            short val = (short) (sample[i] *amplitude);

            generatedTone[i++] = (byte) (val&0x00ff);
            generatedTone[i++] = (byte) ((val >> 8)&0x00ff);
        }
    }
    private void playAudio(){
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedTone.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedTone,0,generatedTone.length);
        audioTrack.setLoopPoints(0, numSamples-5*sampleRate, 1000);
    }

    public void play() throws InterruptedException {
        generateTone();
        playAudio();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    if(audioTrack.getState()==AudioTrack.STATE_UNINITIALIZED) playAudio();
                    if(audioTrack.getState()==AudioTrack.PLAYSTATE_STOPPED) audioTrack.play();
                }
            }
        });

        thread.start();
    }
}
