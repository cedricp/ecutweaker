package org.quark.dr.canapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.quark.dr.ecu.IsoTPDecode;
import org.quark.dr.ecu.IsoTPEncode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public abstract class ElmBase {
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_DISCONNECTED = 3;

    protected ArrayList<String> mMessages;
    protected int mRxa, mTxa;
    protected HashMap<String, String> mEcuErrorCodeMap;
    protected final Handler mConnectionHandler;
    protected OutputStreamWriter mLogFile;
    protected String mLogDir;
    protected volatile boolean mRunningStatus;
    private boolean mTesterPresentFlag;
    static protected ElmBase mSingleton;

    static public ElmBase getSingleton(){
        return mSingleton;
    }

    protected static final String mEcuErrorCodeString =
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

    public abstract void disconnect();
    public abstract boolean connect(String address);
    public abstract int getState();
    protected abstract String writeRaw(String raw_buffer);

    public ElmBase(Handler handler, String logDir, boolean testerPresent) {
        mMessages = new ArrayList<>();
        mConnectionHandler = handler;
        mLogFile = null;
        mLogDir = logDir;
        mRxa = mTxa = -1;
        mTesterPresentFlag = testerPresent;
        buildMaps();
    }

    protected void logInfo(String log){
        Message msg = mConnectionHandler.obtainMessage(ScreenActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(ScreenActivity.TOAST, log);
        msg.setData(bundle);
        mConnectionHandler.sendMessage(msg);
    }

    protected void createLogFile() {
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
    }

    protected String getTimeStamp(){
        return new String("[" + new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss").format(new Date()) + "] ");
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

    public void initElm(){
        write("AT Z");        // reset ELM
        write("AT E1");
        write("AT S0");
        write("AT H0");
        write("AT L0");
        write("AT AL");
        write("AT CAF0");
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

    public void setTimeOut(int timeOut){
        int timeout = (timeOut / 4);
        if (timeout > 255)
            timeout = 255;
        write("AT ST " + Integer.toHexString(timeout));
    }

    public void buildMaps(){
        mEcuErrorCodeMap = new HashMap<>();
        String[] ERRC = mEcuErrorCodeString.replace(" ", "").split(",");
        for (String erc : ERRC){
            String[] idToAddr = erc.split(":");
            mEcuErrorCodeMap.put(idToAddr[0], idToAddr[1]);
        }
    }

    public String getEcuErrorCode(String hexError){
        return mEcuErrorCodeMap.get(hexError);
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

    protected void connectedThreadMainLoop(){
        long timer = System.currentTimeMillis();
        mRunningStatus = true;

        /*
          * Keep listening to the InputStream while connected
          * Thread can be stopped by switching the running status member
          */
        while (mRunningStatus) {

            if (ElmBase.this.mMessages.size() > 0) {
                String message;
                int num_queue;
                synchronized (this) {
                    message = mMessages.get(0);
                    mMessages.remove(0);
                    num_queue = mMessages.size();
                }
                int message_len = message.length();
                if ((message_len > 6) && message.substring(0, 6).toUpperCase().equals("DELAY:")) {
                    int delay = Integer.parseInt(message.substring(6));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        break;
                    }
                } else if ((message_len > 2) && message.substring(0, 2).toUpperCase().equals("AT")) {
                    String result = writeRaw(message);
                    result = message + ";" + result;

                    int result_length = result.length();
                    byte[] tmpbuf = new byte[result_length];
                    System.arraycopy(result.getBytes(), 0, tmpbuf, 0, result_length);  //Make copy for not to rewrite in other thread
                    mConnectionHandler.obtainMessage(ScreenActivity.MESSAGE_READ, result_length, mTxa, tmpbuf).sendToTarget();
                    mConnectionHandler.obtainMessage(ScreenActivity.MESSAGE_QUEUE_STATE, num_queue, -1, null).sendToTarget();
                } else {
                    sendCan(message);
                    mConnectionHandler.obtainMessage(ScreenActivity.MESSAGE_QUEUE_STATE, num_queue, -1, null).sendToTarget();
                }
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }

            // Keep session alive
            if (mTesterPresentFlag && ((System.currentTimeMillis() - timer) > 1500)  && mRxa > 0) {
                timer = System.currentTimeMillis();
                writeRaw("013E");
            }
        }
    }

    protected void sendCan(String message){
        IsoTPEncode isotpm = new IsoTPEncode(message);
        // Encode ISO_TP data
        ArrayList<String> raw_command = isotpm.getFormattedArray();
        ArrayList<String> responses = new ArrayList<>();
        boolean error = false;
        String errorMsg = "";

        // Send data
        for (String frame: raw_command) {
            String frsp = writeRaw(frame);

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
        mConnectionHandler.obtainMessage(ScreenActivity.MESSAGE_READ, result_length, -1, tmpbuf).sendToTarget();
    }

    public synchronized void write(String out) {
        mMessages.add(out);
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
}
