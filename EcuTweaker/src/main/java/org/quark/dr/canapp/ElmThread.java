package org.quark.dr.canapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
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
public class ElmThread {
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
    private int mTxa, mRxa;
    private HashMap<String, String> ECUERRCODEMAP;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_DISCONNECTED = 4;  // now connected to a remote device

    private static final String ECUERRORCODE =
                    "10:General Reject," +
                    "11:Service Not Supported," +
                    "12:SubFunction Not Supported," +
                    "13:Incorrect Message Length Or Invalid Format," +
                    "21:Busy Repeat Request," +
                    "22:Conditions Not Correct Or Request Sequence Error," +
                    "23:Routine Not Complete," +
                    "24:Request Sequence Error," +
                    "31:Request Out Of Range," +
                    "33:Security Access Denied- Security Access Requested," +
                    "35:Invalid Key," +
                    "36:Exceed Number Of Attempts," +
                    "37:Required Time Delay Not Expired," +
                    "40:Download not accepted," +
                    "41:Improper download type," +
                    "42:Can not download to specified address," +
                    "43:Can not download number of bytes requested," +
                    "50:Upload not accepted," +
                    "51:Improper upload type," +
                    "52:Can not upload from specified address," +
                    "53:Can not upload number of bytes requested," +
                    "70:Upload Download NotAccepted," +
                    "71:Transfer Data Suspended," +
                    "72:General Programming Failure," +
                    "73:Wrong Block Sequence Counter," +
                    "74:Illegal Address In Block Transfer," +
                    "75:Illegal Byte Count In Block Transfer," +
                    "76:Illegal Block Transfer Type," +
                    "77:Block Transfer Data Checksum Error," +
                    "78:Request Correctly Received-Response Pending," +
                    "79:Incorrect ByteCount During Block Transfer," +
                    "7E:SubFunction Not Supported In Active Session," +
                    "7F:Service Not Supported In Active Session," +
                    "80:Service Not Supported In Active Diagnostic Mode," +
                    "81:Rpm Too High," +
                    "82:Rpm Too Low," +
                    "83:Engine Is Running," +
                    "84:Engine Is Not Running," +
                    "85:Engine RunTime TooLow," +
                    "86:Temperature Too High," +
                    "87:Temperature Too Low," +
                    "88:Vehicle Speed Too High," +
                    "89:Vehicle Speed Too Low," +
                    "8A:Throttle/Pedal Too High," +
                    "8B:Throttle/Pedal Too Low," +
                    "8C:Transmission Range In Neutral," +
                    "8D:Transmission Range In Gear," +
                    "8F:Brake Switch(es)NotClosed (brake pedal not pressed or not applied)," +
                    "90:Shifter Lever Not In Park ," +
                    "91:Torque Converter Clutch Locked," +
                    "92:Voltage Too High," +
                    "93:Voltage Too Low";

    /**
     * Constructor. Prepares a new BluetoothChat session.
     //     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */

    public ElmThread(Handler handler) {
        mState = STATE_NONE;
        mHandler = handler;
        mTxa = mRxa = -1;
        buildMaps();
    }

    public void buildMaps(){
        ECUERRCODEMAP = new HashMap<>();
        String[] ERRC = ECUERRORCODE.replace(" ", "").split(",");
        for (String erc : ERRC){
            String[] idToAddr = erc.split(":");
            ECUERRCODEMAP.put(idToAddr[0], idToAddr[1]);
        }
    }

    public String getEcuErrorCode(String hexError){
        return ECUERRCODEMAP.get(hexError);
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        //if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(ScreenActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        //if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        //if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

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
        //if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }

        mConnectThread = null;
        mConnectedThread = null;

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     */
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

    public void initElm(){
        write("AT Z");        // reset ELM
        write("AT E1");
        write("AT S0");
        write("AT H0");
        write("AT L0");
        write("AT AL");
        write("AT CAF0");
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

    public void initKwp(String addr, boolean fastInit){
        write("AT SH 81 " + addr + " F1");
        write("AT SW 96");
        write("AT WM 81 " + addr + " F1 3E");
        write("AT IB10");
        write("AT ST FF");
        write("AT AT 0");
        if (!fastInit) {
            write("AT SP 4");
            write("AT " + addr);
            write("AT AT 1");
        } else {
            write("AT SP 5");
            write("AT FI");
        }
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
            synchronized (ElmThread.this) {
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
                // ElmThread.this.start();
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
                    // ElmThread.this.start();
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
            result = message + ";" + result;
            int result_length = result.length();
            byte[] tmpbuf = new byte[result_length];
            //Make copy for not to rewrite in other thread
            System.arraycopy(result.getBytes(), 0, tmpbuf, 0, result_length);
            mHandler.obtainMessage(ScreenActivity.MESSAGE_READ, result_length, -1, tmpbuf).sendToTarget();
        }
    }
}