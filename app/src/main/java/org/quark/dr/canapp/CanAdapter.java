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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class CanAdapter {
    private static final String ACTION_USB_PERMISSION = "org.quark.dr.canapp.USB_PERMISSION";
    private static PendingIntent mPermissionIntent;
    private static UsbManager mUsbManager;
    private static MainActivity mMainActivity;
    private boolean mUsbConnected;
    final int CTRL_OUT = 0x00;
    final int CTRL_IN  = 0x80;

    final int CTRL_RECIPIENT_DEVICE = 0;
    final int CTRL_RECIPIENT_INTERFACE = 1;
    final int CTRL_RECIPIENT_ENDPOINT = 2;
    final int CTRL_RECIPIENT_OTHER = 3;

    final int CTRL_TYPE_STANDARD = (0 << 5);
    final int CTRL_TYPE_CLASS = (1 << 5);
    final int CTRL_TYPE_VENDOR = (2 << 5);
    final int CTRL_TYPE_RESERVED = (3 << 5);

    final int USBRQ_HID_GET_REPORT  =  0x01;
    final int USBRQ_HID_GET_IDLE    =  0x02;
    final int USBRQ_HID_GET_PROTOCOL=  0x03;
    final int USBRQ_HID_SET_REPORT  =  0x09;
    final int USBRQ_HID_SET_IDLE    =  0x0a;
    final int USBRQ_HID_SET_PROTOCOL=  0x0b;

    CanAdapter(MainActivity activity){
        mMainActivity = activity;
        mUsbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        activity.registerReceiver(mUsbReceiver, filter);
        mUsbConnected = false;
    }

    public ArrayList getDevicesList(){
        ArrayList devlist = new ArrayList();
        UsbManager manager = (UsbManager) mMainActivity.getSystemService(Context.USB_SERVICE);

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            devlist.add(device.getDeviceName());
        }
        return devlist;
    }

    private void handleBuffer(byte[] bytes){

    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                mMainActivity.changeText("Broadcast received");
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    mUsbManager.requestPermission(device, mPermissionIntent);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            mUsbConnected = true;
                            CharSequence text = "Connected to " + device.getDeviceName();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(context, text, duration);
                            toast.show();
                            mMainActivity.changeText(device.getDeviceName());
                        }
                    }
                    else {
                        mUsbConnected = false;
                        CharSequence text = "Cannot connect to " + device.getDeviceName();
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    CharSequence text = "Disconnected from " + device.getDeviceName();
                    int duration = Toast.LENGTH_SHORT;

                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }
            }
        }
    };

    private class CanReceiveTask extends AsyncTask<UsbDevice, Integer, Long> {
        byte[] buffer = new byte[512];

        protected Long doInBackground(UsbDevice... usbdev) {
            UsbInterface usbinterface = usbdev[0].getInterface(0);
            UsbDeviceConnection connection = mUsbManager.openDevice(usbdev[0]);
            connection.claimInterface(usbinterface, true);
            long numreceiv = connection.controlTransfer(CTRL_IN | CTRL_TYPE_CLASS,
                    USBRQ_HID_GET_REPORT,
                    0, 0, buffer,
                    512, 1000);
            return numreceiv;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Long result) {
            if (result > 0) {
                handleBuffer(buffer);
            }
        }
    }
}
