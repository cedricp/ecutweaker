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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class CanAdapter {
    private static final String ACTION_USB_PERMISSION = "org.quark.dr.canapp.USB_PERMISSION";
    private static final String TAG = "org.quark.dr.canapp";
    private PendingIntent   mPermissionIntent;
    private UsbManager      mUsbManager;
    private MainActivity    mMainActivity;
    private Handler         handler = new Handler();
    private UsbDeviceConnection    mUsbConnection;

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
        mMainActivity = activity;
        mUsbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(ACTION_USB_PERMISSION), 0);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        IntentFilter filterAttach  = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        IntentFilter filterDetach = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);

        activity.registerReceiver(mUsbReceiver, filter);
        activity.registerReceiver(mUsbReceiver, filterAttach);
        activity.registerReceiver(mUsbReceiver, filterDetach);

        UsbManager usbManager = (UsbManager) mMainActivity.getSystemService(Context.USB_SERVICE);
        try {
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                if (!usbManager.hasPermission(device)) {
                    usbManager.requestPermission(device, mPermissionIntent);
                }
                if (usbManager.hasPermission(device)) {
                    if (checkDevice(device))
                        break;
                }
            }
        } catch (Exception e){
            Toast toast = Toast.makeText(mMainActivity.getApplicationContext(),
                    "Cannot init USB sybsystem ",
                    Toast.LENGTH_SHORT);
            toast.show();
        }
        handler.postDelayed(canAckHandler, 1000);
    }

    public void shutdown(){
        mMainActivity.unregisterReceiver(mUsbReceiver);
        handler.removeCallbacks(canAckHandler);
        mUsbConnection.close();
        mUsbConnection= null;
    }

    private void handleBuffer(byte[] bytes){
        int waterTemp = bytes[24] - 40;
        long odometer = bytes[18] | bytes[17] << 8 | bytes[16] << 16;
        int oilLevel  = bytes[19] >> 2 & 0b1111;
        int fuelLevel = bytes[20] >> 1 & 0b1111111;

        TextView waterTempView = mMainActivity.findViewById(R.id.waterTempView);
        waterTempView.setText(String.valueOf(waterTemp) + " C");

        TextView odometerView = mMainActivity.findViewById(R.id.odometerView);
        odometerView.setText(String.valueOf(odometer) + " KM");

        TextView fuelLevelView = mMainActivity.findViewById(R.id.fuelLevelView);
        fuelLevelView.setText(String.valueOf(fuelLevel) + " L");

        ProgressBar oilView = mMainActivity.findViewById(R.id.oilLevelView);
        if (oilLevel > 8 )
            oilView.setProgress(0);
        else
            oilView.setProgress(oilLevel);
    }

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 3);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private boolean checkDevice(UsbDevice device){
        if (device == null)
            return false;
        if (device.getVendorId() == 5824 && device.getProductId() == 1503){
            UsbInterface usbinterface = device.getInterface(0);
            mUsbConnection = mUsbManager.openDevice(device);
            mUsbConnection.claimInterface(usbinterface, true);
            Toast toast = Toast.makeText(mMainActivity.getApplicationContext(),
                    "Successfully connected to " + device.getDeviceName(), Toast.LENGTH_SHORT);
            toast.show();
            return true;
        }
        Toast toast = Toast.makeText(mMainActivity.getApplicationContext(),
                "VID/PID ERROR " + String.valueOf(device.getVendorId()) + "/" + String.valueOf(device.getProductId()),
                Toast.LENGTH_SHORT);
        toast.show();
        mUsbConnection = null;
        return false;
    }

    private boolean closeDevice(UsbDevice device){
        if (device == null)
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
                            checkDevice(device);
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
                if (device != null){
                    checkDevice(device);
                }
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
        byte[] buffer = new byte[32];
        String error;

        // Do it in background, maybe overkilling, but cannot block the UI thread
        protected Long doInBackground(String... str) {
            long num_receive = 0;
            if (mUsbConnection == null)
                return 0l;
            try {
                int requestType = CTRL_IN | CTRL_TYPE_CLASS | CTRL_RECIPIENT_DEVICE;
                num_receive = mUsbConnection.controlTransfer(
                        requestType,
                        USBRQ_HID_GET_REPORT,
                        0, 0, buffer,
                        32, 500);
            } catch (Exception e) {
                error = e.getMessage();
                return -1l;
            }
            return num_receive;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Long result) {
            if (result == -1l){
                CharSequence text = "USB error " + error;
                Toast toast = Toast.makeText(mMainActivity, text, Toast.LENGTH_SHORT);
                toast.show();
            }

            if (result > 0) {
                handleBuffer(buffer);
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
