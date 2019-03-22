package org.quark.dr.canapp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public abstract class ElmBase {
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_DISCONNECTED = 4;  // now connected to a remote device

    protected int mRxa, mTxa;
    protected HashMap<String, String> ECUERRCODEMAP;

    protected static final String ECUERRORCODE =
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
    public abstract void write(String buffer);
    public abstract boolean connect(String address);
    public abstract int getState();

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
}
