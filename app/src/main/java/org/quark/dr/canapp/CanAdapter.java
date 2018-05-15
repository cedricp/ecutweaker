package org.quark.dr.canapp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.DeluxeSpeedView;
import com.github.anastr.speedviewlib.TubeSpeedometer;
import java.util.HashMap;
import java.util.Iterator;


public class CanAdapter {
    private static final String TAG = "org.quark.dr.canapp";
    private MainActivity    mMainActivity;
    private Handler         handler;
    private TubeSpeedometer mWaterTempView, mFuelLevelView, mOilView;
    private TextView        mExternalTempView, mOdometerView, mFuelConsumptionView;
    private DeluxeSpeedView mRpmView, mSpeedView;
    private Thread socketThread;
    volatile private boolean running = true;

    CanAdapter(MainActivity activity){
        mMainActivity = activity;

        mWaterTempView = activity.findViewById(R.id.waterTempView);
        mFuelLevelView = activity.findViewById(R.id.fuelLevelView);
        mOilView = activity.findViewById(R.id.oilLevelView);
        mExternalTempView = activity.findViewById(R.id.externalTempView);
        mOdometerView = activity.findViewById(R.id.odometerView);
        mFuelConsumptionView = activity.findViewById(R.id.fuelView);
        mRpmView =  activity.findViewById(R.id.rpmView);
        mSpeedView =  activity.findViewById(R.id.speedView);

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.i("CanApp", ">>>>>>>>>>> Receive " + msg.obj);
            }

        };

        socketThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Log.i("CanApp", "interface binding...");
                        CanSocket canSockAdapter = new CanSocket(CanSocket.Mode.RAW);
                        CanSocket.CanInterface caninterface = new CanSocket.CanInterface(canSockAdapter, "can0");
                        canSockAdapter.bind(caninterface, 0x00, 0x00);
                        Log.i("CanApp", "interface bound : " + caninterface.toString());
                        while (true) {
                            CanSocket.CanFrame frame = canSockAdapter.recv();
                            switch (frame.getCanId().getAddress()) {
                                case 0x0551:

                                    break;
                                default:
                                    break;
                            }
                            if (Thread.currentThread().isInterrupted() || !running) {
                                Log.i("CanApp", "Thread stop");
                                return;
                            }
                        }
                    } catch (Exception e) {
                        Log.i("CanApp", "interface error " + e.getMessage());
                    }

                    if (Thread.currentThread().isInterrupted() || !running) {
                        Log.i("CanApp", "Thread stop");
                        return;
                    }

                    try {
                        Thread.sleep(500);
                    } catch (Exception e){

                    }
                }
            }
        });

        socketThread.start();
        socketThread.setPriority(Thread.MIN_PRIORITY);
    }


    public void shutdown(){
        socketThread.interrupt();
        running = false;
        try {
            socketThread.join();
        } catch (Exception e) {

        }
        Log.i("CanApp", "App stop");
    }

    private void handleBuffer(byte[] bytes, long time){
        int externalTemp = (bytes[4] & 0xFF) - 40;
        int fuelConsumption = (bytes[25] & 0xFF) * 80;
        int oilLevel  = (bytes[19] >> 2) & 0b00001111;
        int fuelLevel = (bytes[20] >> 1) & 0b01111111;
        int waterTemp = (bytes[24] & 0xFF) - 40;
        long odometer = bytes[18] & 0xFF;
        odometer |= (bytes[17] & 0xFF) << 8;
        odometer |= (bytes[16] &0xFF) << 16;

        int vehicleSpeed = bytes[33] & 0xFF;
        vehicleSpeed |= (bytes[32] & 0xFF) << 8;
        float vSpeedFloat = (float)vehicleSpeed / 100.0f;

        int engineRpm = bytes[41] & 0xFF;
        engineRpm |= (bytes[40] & 0xFF) << 8;
        float engineRpmFloat = (float)engineRpm / 800.0f;

        mWaterTempView.speedTo(waterTemp, 2000);
        mFuelLevelView.speedTo(fuelLevel, 4000);
        mExternalTempView.setText(String.valueOf(externalTemp) + " C");
        mOdometerView.setText(String.valueOf(odometer) + " KM");
        mFuelConsumptionView.setText(String.valueOf(fuelConsumption));
        mOilView.speedTo(oilLevel);
        mSpeedView.speedTo(vSpeedFloat, 350);
        mRpmView.speedTo(engineRpmFloat, 350);

        TextView timeView = mMainActivity.findViewById(R.id.timeView);
        timeView.setText(String.valueOf(time/1000000) + " ms");
    }
}
