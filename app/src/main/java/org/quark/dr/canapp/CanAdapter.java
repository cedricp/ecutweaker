package org.quark.dr.canapp;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.github.anastr.speedviewlib.DeluxeSpeedView;
import com.github.anastr.speedviewlib.TubeSpeedometer;

import org.quark.dr.socketcan.CanSocket;

import java.lang.ref.WeakReference;

public class CanAdapter {
    private static final String TAG = "org.quark.dr.canapp";
    private MainActivity    mMainActivity;
    private Handler         handler;
    private TubeSpeedometer mWaterTempView, mFuelLevelView, mOilView;
    private TextView        mExternalTempView, mOdometerView, mFuelConsumptionView, mTimeView;
    private DeluxeSpeedView mRpmView, mSpeedView;
    private Thread          socketThread;
    private long            mSpeedMemory, mRpmMemory, mOdometerMemory, mOilLevelMemory;
    private long            mWaterTempMemory, mFuelLevelMemory, mExternalTempMemory;
    private long            mLockMemory, mFuelAcc;
    private long[]          mTimeStamps;
    byte                    mLastFuelConsumptionMemory;
    volatile private boolean socketThreadRunning = true;

    private static class SafeHandler extends Handler {
        private final WeakReference<CanAdapter> mAdapter;
        public SafeHandler(CanAdapter adapter) {
            mAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void handleMessage(Message msg) {
            CanAdapter adapter = mAdapter.get();
            if (adapter == null){
                Log.i(TAG, "Weakref issue...");
                return;
            }

            CanSocket.CanFrame frame = (CanSocket.CanFrame)msg.obj;
            int canaddr = frame.getCanId().getAddress();
            long cants = frame.getTimeStamp();
            byte[] data = frame.getData();
            if (canaddr == 0x0354) {
                long time = cants - adapter.mTimeStamps[0];
                adapter.mTimeStamps[0] = cants;
                int vehicleSpeed = data[1] & 0xFF;
                vehicleSpeed |= (data[0] & 0xFF) << 8;

                if (vehicleSpeed != adapter.mSpeedMemory) {
                    adapter.mSpeedView.speedTo((float)vehicleSpeed * 0.01f, time);
                    adapter.mSpeedMemory = vehicleSpeed;
                }
            } else if (canaddr == 0x0715){
                long odometer = (data[0] & 0xFF) << 16;
                odometer |= (data[1] & 0xFF) << 8;
                odometer |= data[2] & 0xFF;
                int oillevel = (data[3] & 0xFF) & 0b00111100;
                int fuellevel = (data[4] & 0b11111110) >> 1;

                if (odometer != adapter.mOdometerMemory){
                    adapter.mOdometerView.setText(String.valueOf(odometer) + " KM");
                    adapter.mOdometerMemory = odometer;
                }
                if (oillevel != adapter.mOilLevelMemory){
                    adapter.mOilView.speedTo(oillevel >> 2, 2000);
                    adapter.mOilLevelMemory = oillevel;
                }
                if (fuellevel != adapter.mFuelLevelMemory){
                    adapter.mFuelLevelView.speedTo(fuellevel, 5000);
                    adapter.mFuelLevelMemory = fuellevel;
                }
            } else if (canaddr == 0x0551){
                long time = cants - adapter.mTimeStamps[1];
                int waterTemp = data[0] & 0xFF;
                byte fuelConsumption = data[1];

                byte  diff = (byte)(fuelConsumption - adapter.mLastFuelConsumptionMemory);
                adapter.mLastFuelConsumptionMemory = fuelConsumption;
                adapter.mFuelAcc += diff & 0xFF;

                if (waterTemp != adapter.mWaterTempMemory){
                    adapter.mWaterTempView.speedTo(waterTemp - 40, 3000);
                    adapter.mWaterTempMemory = waterTemp;
                }

                if (time > 400) {
                    float seconds = time * 0.001f;
                    float mm3 = (float)adapter.mFuelAcc * 80.f;
                    float mm3perheour = (mm3 / seconds) * 3600.f;
                    float dm3perhour = mm3perheour * 0.000001f;
                    adapter.mTimeStamps[1] = cants;
                    adapter.mFuelAcc = 0;

                    if (adapter.mSpeedMemory > 2000) {
                        float dm3per100kmh = (dm3perhour * 100.f) / (float) adapter.mSpeedMemory;
                        String fuelstring = String.format("%.2f", dm3per100kmh) + " L/100";
                        adapter.mFuelConsumptionView.setText(fuelstring);
                    } else {
                        String fuelstring = String.format("%.2f", dm3perhour) + " L/h";
                        adapter.mFuelConsumptionView.setText(fuelstring);
                    }
                }
            } else if (canaddr == 0x0181) {
                long time = cants - adapter.mTimeStamps[2];
                adapter.mTimeStamps[2] = cants;
                int rpm = (data[0] & 0xFF) << 8;
                rpm |= data[1] & 0xFF;

                if (rpm != adapter.mRpmMemory){
                    adapter.mRpmView.speedTo((rpm * 0.00125f), time);
                    adapter.mRpmMemory = rpm;
                }
            } else if (canaddr == 0x060D) {
                int globallock = data[2] & 0b00011000;
                int externaltemp = data[4] & 0xFF;

                if (externaltemp != adapter.mExternalTempMemory){
                    adapter.mExternalTempView.setText(String.valueOf(externaltemp - 40) + " °C");
                    adapter.mExternalTempMemory = externaltemp;
                }

                if (globallock != adapter.mLockMemory){
                    boolean bootlock = (globallock & 0b00001000) == 0b00001000;
                    boolean doorlock = (globallock & 0b00010000) == 0b00010000;
                    adapter.mLockMemory = globallock;
                }
            }
        }
    }

    CanAdapter(MainActivity activity){
        mMainActivity = activity;
        mTimeStamps         = new long[10];
        mWaterTempView      = activity.findViewById(R.id.waterTempView);
        mFuelLevelView      = activity.findViewById(R.id.fuelLevelView);
        mOilView            = activity.findViewById(R.id.oilLevelView);
        mExternalTempView   = activity.findViewById(R.id.externalTempView);
        mOdometerView       = activity.findViewById(R.id.odometerView);
        mRpmView            =  activity.findViewById(R.id.rpmView);
        mSpeedView          =  activity.findViewById(R.id.speedView);
        mFuelConsumptionView = activity.findViewById(R.id.fuelView);
        mTimeView           = activity.findViewById(R.id.timeView);

        handler = new SafeHandler(this);

        socketThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Log.i(TAG, "interface binding...");
                        CanSocket canSockAdapter = new CanSocket(CanSocket.Mode.RAW);
                        CanSocket.CanInterface caninterface = new CanSocket.CanInterface(canSockAdapter, "can0");
                        canSockAdapter.bind(caninterface, 0x00, 0x00);
                        Log.i(TAG, "interface bound : " + caninterface.toString());
                        while (true) {
                            CanSocket.CanFrame frame = canSockAdapter.recv();
                            Message message = handler.obtainMessage();
                            message.obj = frame;
                            handler.sendMessage(message);

                            if (Thread.currentThread().isInterrupted() || !socketThreadRunning) {
                                Log.i(TAG, "Thread stop");
                                return;
                            }
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "interface error " + e.getMessage());
                    }

                    if (Thread.currentThread().isInterrupted() || !socketThreadRunning) {
                        Log.i(TAG, "Thread stop");
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
        socketThreadRunning = false;
        try {
            socketThread.join();
        } catch (Exception e) {

        }
        Log.i(TAG, "App stop");
    }
}
