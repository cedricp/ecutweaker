package org.quark.dr.canapp;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.github.anastr.speedviewlib.DeluxeSpeedView;
import com.github.anastr.speedviewlib.TubeSpeedometer;
import com.github.anastr.speedviewlib.components.note.Note;
import com.github.anastr.speedviewlib.components.note.TextNote;

import org.quark.dr.ecu.Ecu;
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
    private long            mLockMemory, mFuelAcc, mSpeedLimiterMemory, mFuelAcc2, mFuelTime2;
    private long[]          mTimeStamps;
    byte                    mLastFuelConsumptionMemory, mLimiterEnableMemory;
    volatile private boolean socketThreadRunning = true;

    private static class canDataHandler extends Handler {
        private final WeakReference<CanAdapter> mAdapter;
        public canDataHandler(CanAdapter adapter) {
            super(adapter.mMainActivity.getMainLooper());
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
            int can_id = frame.getCanId().getAddress();
            long frame_timestamp = frame.getTimeStamp();

            byte[] frame_data = frame.getData();
            if (can_id == 0x0354) {
                long time = frame_timestamp - adapter.mTimeStamps[0];
                adapter.mTimeStamps[0] = frame_timestamp;
                int vehicleSpeed = frame_data[1] & 0xFF;
                vehicleSpeed |= (frame_data[0] & 0xFF) << 8;

                if (vehicleSpeed != adapter.mSpeedMemory) {
                    adapter.mSpeedView.speedTo((float)vehicleSpeed * 0.01f, time);
                    adapter.mSpeedMemory = vehicleSpeed;
                }
            } else if (can_id == 0x0715){
                long odometer = (frame_data[0] & 0xFF) << 16;
                odometer     |= (frame_data[1] & 0xFF) << 8;
                odometer     |= frame_data[2] & 0xFF;
                int oil_level  = frame_data[3] & 0b00111100;
                int fuel_level = (frame_data[4] & 0b11111110) >> 1;

                if (odometer != adapter.mOdometerMemory){
                    adapter.mOdometerView.setText(String.valueOf(odometer) + " KM");
                    adapter.mOdometerMemory = odometer;
                }
                if (oil_level != adapter.mOilLevelMemory){
                    adapter.mOilView.speedTo(oil_level >> 2, 4000);
                    adapter.mOilLevelMemory = oil_level;
                }
                if (fuel_level != adapter.mFuelLevelMemory){
                    adapter.mFuelLevelView.speedTo(fuel_level, 15000);
                    adapter.mFuelLevelMemory = fuel_level;
                }
            } else if (can_id == 0x0551){
                long time = frame_timestamp - adapter.mTimeStamps[1];
                int water_temp = frame_data[0] & 0xFF;
                byte fuel_consumption = frame_data[1];
                byte limiter_byte = (byte)((frame_data[5] & 0b01110000) >> 4);
                long speed_limit = frame_data[4] & 0xFF;
                boolean draw_limiter_note = (limiter_byte > 0) && (speed_limit != 254);
                boolean limiter_switched_on = false;

                byte  diff = (byte)(fuel_consumption - adapter.mLastFuelConsumptionMemory);
                adapter.mLastFuelConsumptionMemory = fuel_consumption;
                adapter.mFuelAcc += diff & 0xFF;

                if (limiter_byte != adapter.mLimiterEnableMemory){
                    if (limiter_byte == 0){
                        adapter.mSpeedView.removeAllNotes();
                    } else {
                        limiter_switched_on = true;
                    }
                    adapter.mLimiterEnableMemory = limiter_byte;
                }

                if ( limiter_switched_on || (speed_limit != adapter.mSpeedLimiterMemory) ){
                    adapter.mSpeedView.removeAllNotes();
                    if (draw_limiter_note) {
                        TextNote mSpeedNote = new TextNote(adapter.mMainActivity.getApplicationContext(), String.valueOf(speed_limit))
                                .setPosition(Note.Position.CenterIndicator)
                                .setAlign(Note.Align.Top)
                                .setTextTypeFace(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                                .setBackgroundColor(Color.parseColor("#41FF41"))
                                .setCornersRound(20f)
                                .setTextSize(adapter.mSpeedView.dpTOpx(15f));
                        adapter.mSpeedView.addNote(mSpeedNote, TextNote.INFINITE);
                    }
                    adapter.mSpeedLimiterMemory = speed_limit;
                }

                if (water_temp != adapter.mWaterTempMemory){
                    adapter.mWaterTempView.speedTo(water_temp - 40, 3000);
                    adapter.mWaterTempMemory = water_temp;
                }

                if (time > 400) {
                    float seconds = ((float)time + (float)adapter.mFuelTime2) * 0.001f;
                    float mm3 = ((float)adapter.mFuelAcc + (float)adapter.mFuelAcc2) * 80.f;
                    float mm3perheour = (mm3 / seconds) * 3600.f;
                    float dm3perhour = mm3perheour * 0.000001f;
                    adapter.mFuelTime2 = time;
                    adapter.mFuelAcc2 = adapter.mFuelAcc;
                    adapter.mTimeStamps[1] = frame_timestamp;
                    adapter.mFuelAcc = 0;

                    if (adapter.mSpeedMemory > 3000) {
                        // mSpeedMemory is in km/h * 100
                        float dm3per100kmh = (dm3perhour * 10000.f) / ((float) adapter.mSpeedMemory);
                        String fuelstring = String.format("%.2f", dm3per100kmh) + " L/100";
                        adapter.mFuelConsumptionView.setText(fuelstring);
                    } else {
                        String fuelstring = String.format("%.2f", dm3perhour) + " L/h";
                        adapter.mFuelConsumptionView.setText(fuelstring);
                    }
                }
            } else if (can_id == 0x0181) {
                long time = frame_timestamp - adapter.mTimeStamps[2];
                adapter.mTimeStamps[2] = frame_timestamp;
                int rpm = (frame_data[0] & 0xFF) << 8;
                rpm |= frame_data[1] & 0xFF;

                if (rpm != adapter.mRpmMemory){
                    adapter.mRpmView.speedTo((rpm * 0.00125f), time);
                    adapter.mRpmMemory = rpm;
                }
            } else if (can_id == 0x060D) {
                int globallock = frame_data[2] & 0b00011000;
                int externaltemp = frame_data[4] & 0xFF;

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
        mTimeStamps         = new long[5];
        mWaterTempView      = activity.findViewById(R.id.waterTempView);
        mFuelLevelView      = activity.findViewById(R.id.fuelLevelView);
        mOilView            = activity.findViewById(R.id.oilLevelView);
        mExternalTempView   = activity.findViewById(R.id.externalTempView);
        mOdometerView       = activity.findViewById(R.id.odometerView);
        mRpmView            =  activity.findViewById(R.id.rpmView);
        mSpeedView          =  activity.findViewById(R.id.speedView);
        mFuelConsumptionView = activity.findViewById(R.id.fuelView);
        mTimeView           = activity.findViewById(R.id.timeView);

        handler = new canDataHandler(this);

        socketThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Log.i(TAG, "interface binding...");
                        CanSocket canSockAdapter = new CanSocket(CanSocket.Mode.RAW);
                        CanSocket.CanInterface caninterface = new CanSocket.CanInterface(canSockAdapter, "can0");
                        canSockAdapter.bind(caninterface, 0x00, 0x00);
                        // We are interested in only some frames
                        int[] filters = new int[]{0x060D, 0x0181, 0x0551, 0x0715, 0x0354};
                        int[] masks = new int[]{0x07FF, 0x07FF, 0x07FF, 0x07FF, 0x07FF};
                        canSockAdapter.setFilterMask(filters, masks);

                        Log.i(TAG, "interface bound : " + caninterface.toString());

                        while (true) {
                            CanSocket.CanFrame frame = canSockAdapter.recv();
                            Message message = handler.obtainMessage();
                            message.obj = frame;
                            handler.sendMessage(message);

                            if (Thread.currentThread().isInterrupted() || !socketThreadRunning) {
                                canSockAdapter.close();
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
