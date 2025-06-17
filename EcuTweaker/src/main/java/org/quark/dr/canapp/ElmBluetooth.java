package org.quark.dr.canapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.core.app.ActivityCompat;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class ElmBluetooth extends ElmBase {
    // Debugging
    private static final String TAG = "ElmBluetoothThread";
    private static final boolean D = false;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final Context mContext;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private String mBtAddress;

    protected ElmBluetooth(Context context, Handler handler, String logDir) {
        super(handler, logDir);
        mContext = context;
    }

    @Override
    public int getMode() {
        return MODE_BT;
    }

    @Override
    public boolean connect(String address) {
        // Android version-specific permission checking
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                logInfo("BLUETOOTH_CONNECT permission denied on Android " + Build.VERSION.SDK_INT);
                return false;
            }
        } else { // Android 11 and below
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                logInfo("BLUETOOTH permissions denied on Android " + Build.VERSION.SDK_INT);
                return false;
            }
        }
        
        setState(STATE_CONNECTING);
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            logInfo("BluetoothAdapter is null");
            return false;
        }

        if (!btAdapter.isEnabled()) {
            logInfo("Bluetooth is disabled");
            return false;
        }

        BluetoothDevice device;
        try {
            device = btAdapter.getRemoteDevice(address);
        } catch (Exception e) {
            logInfo("Failed to get remote device: " + e.getMessage());
            return false;
        }
        if (device == null) {
            logInfo("Remote device is null");
            return false;
        }

        disconnect();

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        mBtAddress = address;
        return true;
    }

    @Override
    public boolean reconnect() {
        return connect(mBtAddress);
    }

    @SuppressLint("MissingPermission")
    public synchronized void createConnectedThread(BluetoothSocket socket, BluetoothDevice
            device) {
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        synchronized (this) {
            if (mConnectionHandler != null) {
                // Send the name of the connected device back to the UI Activity
                Message msg = mConnectionHandler.obtainMessage(ScreenActivity.MESSAGE_DEVICE_NAME);
                Bundle bundle = new Bundle();
                bundle.putString(ScreenActivity.DEVICE_NAME, device.getName());
                msg.setData(bundle);
                mConnectionHandler.sendMessage(msg);
            }
        }

        setState(STATE_CONNECTED);
    }

    @Override
    public void disconnect() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }

        mConnectThread = null;
        mConnectedThread = null;

        clearMessages();

        setState(STATE_NONE);
        synchronized (this) {
            if (mConnectionHandler != null) {
                mConnectionHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    @Override
    protected String writeRaw(String raw_buffer) {
        raw_buffer += "\r";
        return mConnectedThread.write(raw_buffer);
    }

    private void connectionFailed() {
        logInfo("Bluetooth connection failed");
        setState(STATE_NONE);
    }

    private void connectionLost() {
        mRunningStatus = false;
        logInfo("Bluetooth connection lost");
        setState(STATE_DISCONNECTED);
    }

    /*
     * Connected thread class
     * Asynchronously manage ELM connection
     *
     */
    @SuppressLint("MissingPermission")
    private class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            /*
             * Comprehensive socket creation for Android 8-15 compatibility
             * Uses multiple fallback methods to handle different device quirks
             */
            
            // Method 1: Standard secure socket (works on most newer devices)
            try {
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
                logInfo("Socket created using standard secure method");
            } catch (IOException e) {
                logInfo("Standard secure method failed: " + e.getMessage());
                
                // Method 2: Insecure socket (better for Android 9-12, Samsung devices)
                try {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                    logInfo("Socket created using insecure method");
                } catch (IOException e2) {
                    logInfo("Insecure method failed: " + e2.getMessage());
                    
                    // Method 3: Reflection method for Samsung and other problematic devices
                    try {
                        Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                        tmp = (BluetoothSocket) m.invoke(device, 1);
                        logInfo("Socket created using reflection method (channel 1)");
                    } catch (Exception e3) {
                        logInfo("Reflection method channel 1 failed: " + e3.getMessage());
                        
                        // Method 4: Alternative reflection with different channel
                        try {
                            Method m = device.getClass().getMethod("createRfcommSocket", int.class);  
                            tmp = (BluetoothSocket) m.invoke(device, 2);
                            logInfo("Socket created using reflection method (channel 2)");
                        } catch (Exception e4) {
                            logInfo("Reflection method channel 2 failed: " + e4.getMessage());
                            
                            // Method 5: Last resort - try with channel 3
                            try {
                                Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                                tmp = (BluetoothSocket) m.invoke(device, 3);
                                logInfo("Socket created using reflection method (channel 3)");
                            } catch (Exception e5) {
                                logInfo("All socket creation methods failed. Device may not support SPP.");
                            }
                        }
                    }
                }
            }
            mmSocket = tmp;
        }

        public void run() {
            String mSocketType = "ELM-socket";
            setName("ConnectThread" + mSocketType);

            // Verify socket was created
            if (mmSocket == null) {
                logInfo("No socket available - all creation methods failed");
                connectionFailed();
                return;
            }

            // Always cancel discovery because it will slow down a connection
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null) {
                logInfo("BluetoothAdapter is null in ConnectThread");
                connectionFailed();
                return;
            }

            try {
                btAdapter.cancelDiscovery();
            } catch (SecurityException e) {
                logInfo("Permission denied canceling discovery: " + e.getMessage());
            }

            // Android version-specific connection handling
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) { // Android 11 and below
                // Add delay for older Android versions - helps with connection stability
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // Make a connection to the BluetoothSocket
            try {
                logInfo("Attempting connection to " + mmDevice.getAddress() + " on Android " + Build.VERSION.SDK_INT);
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
                logInfo("Connection successful!");
            } catch (IOException e) {
                logInfo("Connection failed: " + e.getMessage());
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    logInfo("Failed to close socket: " + e2.getMessage());
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ElmBluetooth.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            createConnectedThread(mmSocket, mmDevice);
        }

        public void cancel() {
            if (!isAlive())
                return;

            interrupt();

            if (mmSocket != null) {
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    logInfo("Error closing socket in cancel: " + e.getMessage());
                }
            }

            try {
                join(5000); // Wait max 5 seconds for thread to finish
            } catch (InterruptedException e) {
                logInfo("Thread join interrupted");
            }
        }
    }

    /*
     * Connect thread class
     * Asynchronously create a Bluetooth socket
     *
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mMessages.clear();
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            // The InputStream read() method should block
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            connectedThreadMainLoop();
        }

        public void cancel() {
            mRunningStatus = false;
            interrupt();
            mMessages.clear();
            try {
                mmSocket.close();
            } catch (IOException e) {
            }

            if (!isAlive())
                return;

            try {
                join();
            } catch (InterruptedException e) {

            }
        }

        private String write(String raw_buffer) {
            byte[] reply_buffer = new byte[4096];
            try {
                mmOutStream.write(raw_buffer.getBytes());
            } catch (IOException e) {
                connectionLost();
                // Start the service over to restart listening mode
                try {
                    mmSocket.close();
                } catch (IOException ioe) {

                }
                return "ERROR : DISCONNECTED";
            }

            // Wait ELM response
            int u = -1;
            while (true) {
                try {
                    // Read from the InputStream
                    u = u + 1;
                    int bytes = mmInStream.read(reply_buffer, u, 1);
                    if (bytes < 1) {
                        --u;
                        continue;
                    }

                    // Convert carriage return to line feed
                    if (reply_buffer[u] == 0x0d)
                        reply_buffer[u] = 0x0a;

                    if (reply_buffer[u] == '>') { // End of communication
                        return new String(reply_buffer, 0, u - 1);
                    }
                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
            return "ERROR : UNKNOWN";
        }
    }
}
