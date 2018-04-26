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
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.DeluxeSpeedView;
import com.github.anastr.speedviewlib.TubeSpeedometer;
import java.util.HashMap;
import java.util.Iterator;


public class CanAdapter {
    private static final String ACTION_USB_PERMISSION = "org.quark.dr.canapp.USB_PERMISSION";
    private static final String TAG = "org.quark.dr.canapp";
    private PendingIntent   mPermissionIntent;
    private UsbManager      mUsbManager;
    private UsbDevice       mUsbDevice;
    private MainActivity    mMainActivity;
    private Handler         handler = new Handler();
    private UsbDeviceConnection    mUsbConnection;
    private TubeSpeedometer mWaterTempView, mFuelLevelView, mOilView;
    private TextView        mExternalTempView, mOdometerView, mFuelConsumptionView;
    private DeluxeSpeedView mRpmView, mSpeedView;

    // control request direction
    final int CTRL_OUT = 0x00;
    final int CTRL_IN  = 0x80;

    // control request recipient
    final int CTRL_RECIPIENT_DEVICE = 0;
    final int CTRL_RECIPIENT_INTERFACE = 1;
    final int CTRL_RECIPIENT_ENDPOINT = 2;
    final int CTRL_RECIPIENT_OTHER = 3;

    // control request type
    final int CTRL_TYPE_STANDARD = (0 << 5);
    final int CTRL_TYPE_CLASS = (1 << 5);
    final int CTRL_TYPE_VENDOR = (2 << 5);
    final int CTRL_TYPE_RESERVED = (3 << 5);

    // hid get/set
    final int USBRQ_HID_GET_REPORT  =  0x01;
    final int USBRQ_HID_GET_IDLE    =  0x02;
    final int USBRQ_HID_GET_PROTOCOL=  0x03;
    final int USBRQ_HID_SET_REPORT  =  0x09;
    final int USBRQ_HID_SET_IDLE    =  0x0a;
    final int USBRQ_HID_SET_PROTOCOL=  0x0b;

    // descriptor type
    final int DESC_TYPE_DEVICE = 0x01;
    final int DESC_TYPE_CONFIG = 0x02;
    final int DESC_TYPE_STRING = 0x03;
    final int DESC_TYPE_INTERFACE = 0x04;
    final int DESC_TYPE_ENDPOINT = 0x05;

    // endpoint direction
    final int ENDPOINT_IN = 0x80;
    final int ENDPOINT_OUT = 0x00;

    CanAdapter(MainActivity activity){
        try {
            CanSocket canSockAdapter = new CanSocket(CanSocket.Mode.RAW);
            CanSocket.CanInterface caninterface = new CanSocket.CanInterface(canSockAdapter, "can0");
        } catch (Exception e){
            Log.i("CanApp", "interface error " + e.getMessage());
        }

        mMainActivity = activity;

        mWaterTempView = activity.findViewById(R.id.waterTempView);
        mFuelLevelView = activity.findViewById(R.id.fuelLevelView);
        mOilView = activity.findViewById(R.id.oilLevelView);
        mExternalTempView = activity.findViewById(R.id.externalTempView);
        mOdometerView = activity.findViewById(R.id.odometerView);
        mFuelConsumptionView = activity.findViewById(R.id.fuelView);
        mRpmView =  activity.findViewById(R.id.rpmView);
        mSpeedView =  activity.findViewById(R.id.speedView);

        mUsbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(ACTION_USB_PERMISSION), 0);

        activity.registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        activity.registerReceiver(mUsbReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        activity.registerReceiver(mUsbReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
        scanUsbDevices();

        // Launch timer
        handler.postDelayed(canAckHandler, 1000);
    }

    private void scanUsbDevices(){
        // Check if USB is already connected
        if (mUsbDevice == null) {
            try {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    if (device == null)
                        continue;

                    if (!mUsbManager.hasPermission(device))
                        mUsbManager.requestPermission(device, mPermissionIntent);

                    if (mUsbManager.hasPermission(device))
                        openDevice(device);
                }
            } catch (Exception e) {
                Toast toast = Toast.makeText(mMainActivity.getApplicationContext(),
                        "Cannot init USB sybsystem : " + e.getMessage(),
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    public void shutdown(){
        mMainActivity.unregisterReceiver(mUsbReceiver);
        handler.removeCallbacks(canAckHandler);
        closeDevice(mUsbDevice);
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

    private boolean openDevice(UsbDevice device){
        if (device == null)
            return false;

        if (device.getVendorId() == 5824 && device.getProductId() == 1503){
            mUsbDevice = device;
            UsbInterface usbinterface = device.getInterface(0);
            mUsbConnection = mUsbManager.openDevice(device);
            mUsbConnection.claimInterface(usbinterface, true);
            Toast toast = Toast.makeText(mMainActivity.getApplicationContext(),
                    "Successfully connected to " + device.getDeviceName(), Toast.LENGTH_SHORT);
            toast.show();
            return true;
        }
        mUsbDevice = null;
        mUsbConnection = null;
        return false;
    }

    private boolean closeDevice(UsbDevice device){
        if (device == null || mUsbDevice != device)
            return false;

        if (device.getVendorId() == 5824 && device.getProductId() == 1503){
            mUsbConnection.close();
            mUsbConnection = null;
            Toast toast = Toast.makeText(mMainActivity.getApplicationContext(),
                    "Successfully disconnected from " + device.getDeviceName(),
                    Toast.LENGTH_SHORT);
            toast.show();
            return true;
        }
        return false;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            openDevice(device);
                        } else {
                            mUsbConnection = null;
                            CharSequence text = "USB permission refused for " + device;
                            Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                openDevice(device);
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    closeDevice(device);
                }
            }
        }
    };

    private class CanReceiveTask extends AsyncTask<String, Integer, Long> {
        byte[] buffer = new byte[48];
        String error;
        long time;

        // Do it in background, maybe overkilling, but cannot block the UI thread
        protected Long doInBackground(String... str) {
            long num_receive = 0l;
            if (mUsbConnection == null)
                return num_receive;
            try {
                time = System.nanoTime();
                int requestType = CTRL_IN | CTRL_TYPE_CLASS | CTRL_RECIPIENT_DEVICE;
                num_receive = mUsbConnection.controlTransfer(
                        requestType,
                        USBRQ_HID_GET_REPORT,
                        0, 0, buffer,
                        48, 500);
                time = System.nanoTime() - time;
            } catch (Exception e) {
                error = e.getMessage();
                return -1l;
            }
            return num_receive;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Long result) {
            if (result == -1l) {
                CharSequence text = "USB error " + error;
                Toast toast = Toast.makeText(mMainActivity, text, Toast.LENGTH_SHORT);
                toast.show();
                // Attempt to reconnect...
                closeDevice(mUsbDevice);
                scanUsbDevices();
            } else if (result > 0) {
                handleBuffer(buffer, time);
            }

            // Repost can handler
            handler.postDelayed(canAckHandler, 200);
        }
    }

    private Runnable canAckHandler = new Runnable() {
        @Override
        public void run() {
            if (mUsbConnection != null)
                new CanReceiveTask().execute();
        }
    };
}
