package org.quark.dr.canapp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.quark.dr.ecu.IsoTPDecode;
import org.quark.dr.ecu.IsoTPEncode;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has  a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class ElmBluetooth extends ElmBase {
    // Debugging
    private static final String TAG = "ElmThread";
    private static final boolean D = false;

    // UUID for this application
    private static final UUID SPP_UUID = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private OutputStreamWriter mLogFile;
    private String mLogDir;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     //     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */

    public ElmBluetooth(Handler handler, String logDir) {
        mState = STATE_NONE;
        mHandler = handler;
        mTxa = mRxa = -1;
        buildMaps();
        mLogFile = null;
        mLogDir = logDir;
    }


    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(ScreenActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    @Override
    public int getState() {
        synchronized (this) {
            return mState;
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param address  The BluetoothDevice address to connect
     */
    @Override
    public boolean connect(String address) {
        synchronized (this) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = btAdapter.getRemoteDevice(address);

            // Cancel any thread attempting to make a connection
            if (mState == STATE_CONNECTING) {
                if (mConnectThread != null) {
                    mConnectThread.cancel();
                    mConnectThread = null;
                }
            }

            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            File file = new File(mLogDir + "/log.txt");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                mLogFile = new OutputStreamWriter(fileOutputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // Start the thread to connect with the given device
            mConnectThread = new ConnectThread(device);
            mConnectThread.start();
            setState(STATE_CONNECTING);
        }
        return true;
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
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
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(ScreenActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(ScreenActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }

        mConnectThread = null;
        mConnectedThread = null;

        setState(STATE_NONE);

        if (mLogFile != null){
            try {
                mLogFile.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void disconnect(){
        stop();
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     */
    @Override
    public void write(String out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.post_message(out);
    }

    public void setEcuName(String name){
        if (mLogFile != null){
            try {
                mLogFile.append("New session with ECU " + name + "\n");
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public void initCan(String rxa, String txa){
        write("AT SP 6");
        write("AT SH " + txa);
        write("AT CRA " + rxa.toUpperCase());
        write("AT FC SH " + txa.toUpperCase());
        write("AT FC SD 30 00 00");
        write("AT FC SM 1");
        mRxa = Integer.parseInt(rxa, 16);
        mTxa = Integer.parseInt(txa, 16);
    }



    public boolean queueEmpty(){
        return mConnectedThread.queue_empty();
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
//        Message msg = mHandler.obtainMessage(ScreenActivity.MESSAGE_TOAST);
//        Bundle bundle = new Bundle();
//        bundle.putString(ScreenActivity.TOAST, "Unable to connect device");
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);
        setState(STATE_NONE);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
//         Send a failure message back to the Activity
//        Message msg = mHandler.obtainMessage(ScreenActivity.MESSAGE_TOAST);
//        Bundle bundle = new Bundle();
//        bundle.putString(ScreenActivity.TOAST, "Device connection was lost");
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);
        setState(STATE_DISCONNECTED);
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType = "ELM-socket";

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                //Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            //Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            btAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
//                    Log.e(TAG, "unable to close() " + mSocketType +
//                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ElmBluetooth.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            if (!isAlive())
                return;

            interrupt();

            try {
                mmSocket.close();
            } catch (IOException e) {
                //Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }

            try {
                join();
            } catch (InterruptedException e) {

            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream     mmInStream;
        private final OutputStream    mmOutStream;
        private ArrayList<String>     mmessages;
        private volatile boolean      mRunningStatus;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            //Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream  tmpIn  = null;
            OutputStream tmpOut = null;
            mmessages = new ArrayList<>();

            // Get the BluetoothSocket input and output streams
            // The InputStream read() method should block
            try {
                tmpIn  = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                //Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream  = tmpIn;
            mmOutStream = tmpOut;
        }

        public synchronized void post_message(String buffer){
            mmessages.add(buffer);
        }

        public synchronized boolean queue_empty(){
            return mmessages.size() == 0;
        }

        public void run() {
            //Log.i(TAG, "BEGIN mConnectedThread");
            long timer = System.currentTimeMillis();
            mRunningStatus = true;

            // Keep listening to the InputStream while connected
            while (mRunningStatus) {
                if (mmessages.size() > 0){
                    String message;
                    int num_queue;
                    synchronized (this) {
                        message = mmessages.get(0);
                        mmessages.remove(0);
                        num_queue = mmessages.size();
                    }
                    int message_len = message.length();
                    if ( (message_len > 6) && message.substring(0,6).toUpperCase().equals("DELAY:") ) {
                        int delay = Integer.parseInt(message.substring(6));
                        try {
                            Thread.sleep(delay);
                        }  catch (InterruptedException e) {
                            break;
                        }
                    } else if ( (message_len > 2) && message.substring(0,2).toUpperCase().equals("AT") ) {
                        String result = write_raw(message);
                        result = message + ";" + result;
                        int result_length = result.length();
                        byte[] tmpbuf = new byte[result_length];
                        System.arraycopy(result.getBytes(), 0, tmpbuf, 0, result_length);  //Make copy for not to rewrite in other thread
                        mHandler.obtainMessage(ScreenActivity.MESSAGE_READ, result_length, mTxa, tmpbuf).sendToTarget();
                        mHandler.obtainMessage(ScreenActivity.MESSAGE_QUEUE_STATE, num_queue, -1, null).sendToTarget();
                    }
                    else {
                        send_can(message);
                        mHandler.obtainMessage(ScreenActivity.MESSAGE_QUEUE_STATE, num_queue, -1, null).sendToTarget();
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }

                // Keep session alive
                if (System.currentTimeMillis() - timer > 1500){
                    timer = System.currentTimeMillis();
                    write_raw("013E");
                }
            }
        }

        public void cancel() {
            mRunningStatus = false;
            interrupt();
            mmessages.clear();
            try {
                mmSocket.close();
            } catch (IOException e) {
                //Log.e(TAG, "close() of connect socket failed", e);
            }

            if (!isAlive())
                return;

            try {
                join();
            } catch (InterruptedException e) {

            }
        }

        private String write_raw(String raw_buffer){
            raw_buffer += "\r";
            byte[] reply_buffer = new byte[4096];
            try {
                mmOutStream.write(raw_buffer.getBytes());
            } catch (IOException e) {
                //Log.e(TAG, "write_raw(1): disconnected", e);
                connectionLost();
                // Start the service over to restart listening mode
                // ElmBluetooth.this.start();
                return "ERROR : DISCONNECTED";
            }

            long time_start = System.currentTimeMillis();
            // Wait ELM response
            int u = -1;
            while (true) {
                if (System.currentTimeMillis() - time_start > 1500){
                    return "ERROR : TIMEOUT";
                }
                try {
                    // Read from the InputStream
                    u = u + 1;
                    int bytes = mmInStream.read(reply_buffer, u, 1);
                    if (bytes < 1) { --u; continue;}

                    // Convert carriage return to line feed
                    if (reply_buffer[u] == 0x0d)
                        reply_buffer[u] = 0x0a;

                    if (reply_buffer[u] == '>') { // End of communication
                        return new String(reply_buffer, 0, u -1);
                    }
                } catch (IOException e) {
                    //Log.e(TAG, "write_raw(2): disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    // ElmBluetooth.this.start();
                    break;
                }
            }
            return "ERROR : UNKNOWN";
        }

        public boolean isHexadecimal(String text) {
            char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F' };

            for (char symbol : text.toCharArray()) {
                boolean found = false;
                for (char hexDigit : hexDigits) {
                    if (symbol == hexDigit) {
                        found = true;
                        break;
                    }
                }
                if(!found)
                    return false;
            }
            return true;
        }

        private void send_can(String message){
            IsoTPEncode isotpm = new IsoTPEncode(message);
            // Encode ISO_TP data
            ArrayList<String> raw_command = isotpm.getFormattedArray();
            ArrayList<String> responses = new ArrayList<>();
            boolean error = false;
            String errorMsg = "";

            // Send data
            for (String frame: raw_command) {
                String frsp = write_raw(frame);

                for(String s: frsp.split("\n")){
                    // Remove unwanted characters
                    s = s.replace("\n", "");
                    // Echo cancellation
                    if (s.equals(frame))
                        continue;

                    // Remove whitespaces
                    s = s.replace(" ", "");
                    if (s.length() == 0)
                        continue;

                    if (isHexadecimal(s)){
                        // Filter out frame control (FC) response
                        if (s.substring(0, 1).equals("3"))
                            continue;
                        responses.add(s);
                    } else {
                        errorMsg += frsp;
                        error = true;
                    }
                }
            }
            String result;
            if (error){
                result = "ERROR : " + errorMsg;
            } else {
                // Decode received ISO_TP data
                IsoTPDecode isotpdec = new IsoTPDecode(responses);
                result = isotpdec.decodeCan();
            }

            try {
                if (mLogFile != null) {
                    mLogFile.append("SENT: " + getTimeStamp() + message + "\n");
                    mLogFile.append("RECV: " + getTimeStamp() + result + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            result = message + ";" + result;
            int result_length = result.length();
            byte[] tmpbuf = new byte[result_length];
            //Make copy for not to rewrite in other thread
            System.arraycopy(result.getBytes(), 0, tmpbuf, 0, result_length);
            mHandler.obtainMessage(ScreenActivity.MESSAGE_READ, result_length, -1, tmpbuf).sendToTarget();
        }
    }

}