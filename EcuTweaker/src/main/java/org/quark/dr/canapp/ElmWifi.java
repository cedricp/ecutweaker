package org.quark.dr.canapp;

import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

public class ElmWifi extends ElmBase{
    private static final String TAG = "ElmWifi";
    private final Context mContext;
    private final HandlerThread mOBDThread;
    private final Handler mWIFIHandler;
    private Socket mSocket;
    private boolean mConnecting = false;
    private WifiManager.WifiLock wifiLock;
    private int mState;

    OutputStream outStream;
    InputStream inStream;

    String serverIpAddress = "192.168.0.10";
    public static final int SERVERPORT = 35000;
    String deviceName = "Elm327";
    private ElmWifi.ConnectedThread mConnectedThread;
    private ElmWifi.ConnectThread mConnectThread;

    private class ConnectThread extends Thread {
        String mServerIp;
        int mServerPort;
        Socket mlocalSocket;

        public ConnectThread(String serverIp, int serverPort) {
            mServerIp = serverIp;
            mServerPort = serverPort;
        }

        public void run() {
            try {
                mlocalSocket = new Socket();
                mlocalSocket.connect(new InetSocketAddress(mServerIp, mServerPort), 4000);
                mlocalSocket.setKeepAlive(true);
                mlocalSocket.setSoTimeout(2000);
                setState(STATE_CONNECTED);
                mConnecting = false;

                // Send the name of the connected device back to the UI Activity
                Message msg = mWIFIHandler.obtainMessage(ScreenActivity.MESSAGE_DEVICE_NAME);
                Bundle bundle = new Bundle();
                bundle.putString(ScreenActivity.DEVICE_NAME, deviceName);
                msg.setData(bundle);
                mWIFIHandler.sendMessage(msg);

                if (mConnectedThread != null) {
                    mConnectedThread.cancel();
                    mConnectedThread = null;
                }

                synchronized (this){
                    mmessages.clear();
                }

                // Start the thread to manage the connection and perform transmissions
                connected(mlocalSocket);
            } catch (IOException e) {

            }
        }

        public void cancel() {
            if (!isAlive())
                return;

            interrupt();

            try {
                mlocalSocket.close();
            } catch (IOException e) {
            }

            try {
                join();
            } catch (InterruptedException e) {
            }
        }
    }

    private void logInfo(String info){
        Message msg = mWIFIHandler.obtainMessage(ScreenActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(ScreenActivity.TOAST, info);
        msg.setData(bundle);
        mWIFIHandler.sendMessage(msg);
    }

    private void connectionLost() {
        // Send a failure message back to the Activity;
        logInfo("Wifi device connection was lost");
        setState(STATE_NONE);
    }

    public ElmWifi(Context context, Handler handler, String logDir) {
        super(handler, logDir);
        this.mContext = context;
        mOBDThread = new HandlerThread("OBDII", Thread.NORM_PRIORITY);
        mOBDThread.start();
        mWIFIHandler = handler;
    }

    @Override
    public synchronized int getState() {
            return mState;
    }

    private synchronized void setState(int state) {
        mState = state;
        // Give the new state to the Handler so the UI Activity can update
        mWIFIHandler.obtainMessage(ScreenActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public boolean connect(String address) {
        if (!address.isEmpty()) {
            serverIpAddress = address;
        }

        if (mConnecting || isConnected()) {
            return false;
        }

        if (mConnectThread != null)
            mConnectThread.cancel();

        if (mConnectedThread != null){
            mConnectedThread.cancel();
        }

        setState(STATE_CONNECTING);

        WifiManager wifi = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiLock == null) {
            this.wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HighPerf wifi lock");
        }

        wifiLock.acquire();
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        String name = wifiInfo.getSSID();

        if (wifi.isWifiEnabled() && (name.toUpperCase().contains("OBD") ||
                name.toUpperCase().contains("ELM") ||
                name.toUpperCase().contains("ECU") ||
                name.toUpperCase().contains("LINK") ) ) {
            mConnecting = true;
            deviceName = name.replace("\"","");

            mWIFIHandler.removeCallbacksAndMessages(null);

            if(!isConnected() && mConnecting)
            {
                mConnectThread = new ElmWifi.ConnectThread(serverIpAddress, SERVERPORT);
                mConnectThread.start();
            }

            return true;
        }

        Message msg = mWIFIHandler.obtainMessage(ScreenActivity.MESSAGE_TOAST);
        logInfo("Unable to connect wifi device");

        setState(STATE_NONE);

        mConnecting = false;
        return false;
    }

    @Override
    public void disconnect() {
        if (mConnectThread != null)
            mConnectThread.cancel();

        if (mConnectedThread == null)
            return;

        mConnectedThread.cancel();

        if (wifiLock != null && wifiLock.isHeld())
            wifiLock.release();

        mWIFIHandler.removeCallbacksAndMessages(null);
        mConnecting = false;
        mWIFIHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mSocket != null && mSocket.isConnected()) {
                    try {
                        mSocket.close();
                        mSocket = null;
                        setState(STATE_NONE);
                    } catch (Exception e) {
                        Log.d(TAG, "disconnect: " + Log.getStackTraceString(e));
                    }
                }
            }
        });
    }

    private void connected(Socket socket){
        mSocket = socket;
        mConnectedThread = new ElmWifi.ConnectedThread(socket);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }

    public boolean isConnected() {
        return (mSocket != null && mSocket.isConnected());
    }

    @Override
    protected String write_raw(String raw_buffer) {
        raw_buffer += "\r\n";
        return mConnectedThread.write(raw_buffer.getBytes());
    }

    private class ConnectedThread extends Thread {
        private final Socket mmSocket;

        public ConnectedThread(Socket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            inStream = tmpIn;
            outStream = tmpOut;
        }

        public void run() {
            main_loop();
        }

        public String write(byte[] buffer) {
            writeDataToOBD(buffer);
            String result = readDataFromOBD();
            return result;
        }

        public void writeDataToOBD(byte[] buffer) {
            try {
                if(mSocket != null)
                {
                    logInfo("Begin write");
                    outStream = mSocket.getOutputStream();
                    byte[] arrayOfBytes = buffer;
                    outStream.write(arrayOfBytes);
                    outStream.flush();
                    logInfo("End write");
                }
            } catch (Exception localIOException1) {
                localIOException1.printStackTrace();
                connectionLost();
            }
        }

        public String readDataFromOBD() {

            while (true) {
                try {
                    if(mSocket != null)
                    {
                        String rawData;
                        byte b;
                        StringBuilder res = new StringBuilder();
                        inStream = mSocket.getInputStream();

                        long start = System.currentTimeMillis();
                        logInfo("Begin read");
                        while ((char) (b = (byte) inStream.read()) != '>') {
                            if (b == 0x0d)
                                b = 0x0a;
                            res.append((char) b);
                        }
                        rawData = res.toString();
                        System.out.println("?? Recv : " + rawData);
                        if (System.currentTimeMillis() - start > 1500) {
                            connectionLost();
                            break;
                        }
                        logInfo("Read buffer : " + rawData);
                        return rawData;
                    }

                } catch (IOException localIOException) {
                    connectionLost();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    connectionLost();
                    break;
                }
            }
            return "";
        }

        public void cancel() {
            mRunningStatus = false;
            interrupt();
            mmessages.clear();
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocket + " socket failed", e);
            }

            try {
                join();
            } catch (InterruptedException e) {

            }
        }
    }
}