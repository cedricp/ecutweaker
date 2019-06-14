package org.quark.dr.canapp;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;

import org.quark.dr.usbserial.driver.UsbSerialPort;

import java.io.IOException;

public class ElmSerial extends ElmBase {
    private static UsbSerialPort msPort = null;
    private final Context mContext;
    private ConnectedThread mConnectedThread;

    ElmSerial(Context context, Handler handler, String logDir) {
        super(handler, logDir);
        mContext = context;
    }

    @Override
    public int getMode(){
        return MODE_USB;
    }

    @Override
    public boolean connect(String address) {
        final UsbManager usbManager = (UsbManager) mContext.getApplicationContext().getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(msPort.getDriver().getDevice());
        if (connection == null) {
            return false;
        }

        try {
            msPort.open(connection);
            msPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        }catch (IOException e) {
            try {
                msPort.close();
            } catch (IOException e2) {
                // Ignore.
            }
            msPort = null;
            return false;
        }

        // Launch thread
        mConnectedThread = new ConnectedThread(msPort);
        mConnectedThread.start();
        return true;
    }

    @Override
    public boolean reconnect(){
        disconnect();
        return connect("");
    }

    @Override
    public void disconnect() {
        if (mConnectedThread != null)
            mConnectedThread.cancel();

        clearMessages();

        synchronized (this) {
            if (mConnectionHandler != null) {
                mConnectionHandler.removeCallbacksAndMessages(null);
            }
        }
        mConnecting = false;

        setState(STATE_NONE);
    }

    @Override
    protected String writeRaw(String raw_buffer) {
        return "";
    }

    private void connectionLost(String message) {
        // Send a failure message back to the Activity;
        logInfo("Wifi device connection was lost : " + message);
        mRunningStatus = false;
        setState(STATE_DISCONNECTED);
    }

    /*
     * Connected thread class
     * Asynchronously manage ELM connection
     *
     */
    private class ConnectedThread extends Thread {
        private UsbSerialPort mUsbSerialPort;

        public ConnectedThread(UsbSerialPort usbSerialPort) {
            mUsbSerialPort = usbSerialPort;
        }

        public void run() {
            connectedThreadMainLoop();
        }

        public String write(byte[] buffer) {
            writeToElm(buffer);
            String result = readFromElm();
            return result;
        }

        public void writeToElm(byte[] buffer) {
            try {
                if(mUsbSerialPort != null)
                {
                    byte[] arrayOfBytes = buffer;
                    mUsbSerialPort.write(arrayOfBytes, 500);
                }
            } catch (Exception localIOException1) {
                connectionLost(localIOException1.getMessage());
                try {
                    mUsbSerialPort.close();
                } catch (IOException e){

                }
            }
        }

        public String readFromElm() {
            while (true) {
                try {
                    if(mUsbSerialPort != null)
                    {
                        byte b[] = new byte[1];
                        StringBuilder res = new StringBuilder();
                        int charCount = 0;
                        int numCharRead;
                        while ((char) (numCharRead = mUsbSerialPort.read(b, 700)) != '>') {
                            if (numCharRead == 0 || ++charCount > 32768){
                                try {
                                    mUsbSerialPort.close();
                                } catch (IOException e){

                                }
                                connectionLost("USB Socket overflow");
                                return "";
                            }
                            if (b[0] == 0x0d)
                                b[0] = 0x0a;
                            res.append((char) b[0]);
                        }
                        return "";
                    }

                } catch (IOException localIOException) {
                    connectionLost(localIOException.getMessage());
                    break;
                } catch (Exception e) {
                    connectionLost(e.getMessage());
                    break;
                }
            }
            return "";
        }

        public void cancel() {
            mRunningStatus = false;
            interrupt();

            try {
                mUsbSerialPort.close();
            } catch (IOException e) {
            }

            try {
                join();
            } catch (InterruptedException e) {

            }
        }
    }
}
