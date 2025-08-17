package com.example.remotecontrol;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private BluetoothDevice bluetoothDevice;
    private Method m;

    private float BaseLevel;

    private boolean isOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        View view = findViewById(R.id.main);
        view.post(new Runnable() {
            @Override
            public void run() {
                start();
            }
        });
        //Thread to check for battery level and ground the drone when battery is less than 10%
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (checkBattery("41:42:CC:F6:DE:F9")) ;
                land();
            }
        });
        t.start();
        android.os.SystemClock.sleep(1000);
        connect();
    }

    public void start() {
        isOn=false;

        //For land button
        Button button = findViewById(R.id.land);
        button.setOnClickListener(v -> land());

        //For y-axis acceleration
        setJoystick(R.id.accelerate_vert_outer, R.id.accelerate_vert_inner);


        //for x-axis acceleration
        setJoystick(R.id.accelerate_horizontal_outer, R.id.accelerate_horizontal_inner);

    }

    @SuppressLint("ClickableViewAccessibility")
    private void setJoystick(int Id_outer,int Id_inner){
        TextView throttle_outer = findViewById(Id_outer);
        ImageView throttle_inner = findViewById(Id_inner);
        float x_center = throttle_inner.getX();
        float y_center = throttle_inner.getY();

        throttle_outer.setOnTouchListener((view, event) -> {
            float y_drag = event.getRawY();
            float x_drag = event.getRawX();

            float ratio_x, ratio_y;


            float radius = dptopixel(85.0f);
            float distance = distance(x_drag, y_drag, x_center, y_center);

            float dy = y_drag-y_center;
            float dx = x_drag-x_center;

            if(distance<=radius) {
                throttle_inner.setY(event.getRawY());
                throttle_inner.setX(event.getRawX());
                ratio_y = -dy/radius;
                ratio_x = -dx/radius;
            }else{
                float dist = distance(dx, dy,0,0);
                float x_curr = x_center + 85.0f* (dx/dist);
                float y_curr = y_center + 85.0f* (dy/dist);
                throttle_inner.setX(x_curr);
                throttle_inner.setY(y_curr);
                ratio_y = -dy/dist;
                ratio_x = -dx/dist;
            }

            if(event.getAction()==MotionEvent.ACTION_UP){
                throttle_inner.setY(y_center+0.5f);
                throttle_inner.setX(x_center-0.8f);
                ratio_x=ratio_y=0.0f;
            }

                if (Id_inner == R.id.accelerate_horizontal_inner) {
                    //accelerateHorizontal();
                } else {
                    Log.i("ratio_y : ", "" + ratio_y);
                    accelerateVertical(ratio_y);
                }
            return true;
        });

    }

    public void accelerateVertical(float ratio){
        BaseLevel = 0.5f;
        setAudioLevel(ratio/2.0f);
    }

    private void setAudioLevel(float audioLevel){
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) ((audioLevel + BaseLevel)*maxVolume), 0);
    }
    private float dptopixel(float dp){
        DisplayMetrics display = this.getResources().getDisplayMetrics();
        float density = display.density;
        float radius = density*dp + 0.5f;
        return radius;
    }//i can send u the app as src code niye tui ki korbi??.
    //download android studio first.oi

    public float distance(float x_1, float y_1, float x_2, float y_2){
        double dx = x_1 - x_2;
        double dy = y_1 - y_2;
        return (float) Math.sqrt(dy*dy + dx*dx);
    }

    private boolean checkBattery(String device_addr) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(device_addr); // replace with the device's MAC address
        int batteryLevel = 0;
        try {
            // Use reflection to access the hidden getBatteryLevel method
            Method method = bluetoothDevice.getClass().getMethod("getBatteryLevel");
            batteryLevel = (Integer) method.invoke(bluetoothDevice);
            Log.d("BatteryLevel", "Battery Level: " + batteryLevel + "%");
        } catch (Exception e) {
            Log.d("BatteryLevel", "Battery level not available.");
        }

        if (batteryLevel <= 10) {
            land();
            return false;
        }

        return true;
    }
    public void showToast(String msg, int duration) {
        Toast toast = Toast.makeText(com.example.remotecontrol.MainActivity.this, msg, duration);
        toast.show();
    }

    public void land() {
        setAudioLevel(-0.02f);
    }

    public void connect(){
        Button button = findViewById(R.id.connect);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), GenerateAudio.class);
                intent.putExtra("duration",100);
                intent.putExtra("amplitude", 32768);

                if(isOn){
                    stopService(intent);
                    isOn = false;
                }else {
                    startForegroundService(intent);
                    isOn = true;
                }

            }
        });
    }
}