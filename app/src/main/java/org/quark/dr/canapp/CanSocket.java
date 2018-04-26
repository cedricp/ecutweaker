package org.quark.dr.canapp;

import android.util.Log;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class CanSocket implements Closeable {
    static {
        final String LIB_JNI_SOCKETCAN = "socketcan";
        try {
            System.loadLibrary(LIB_JNI_SOCKETCAN);
        } catch (final UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(LIB_JNI_SOCKETCAN);
        }
    }

    private static void copyStream(final InputStream in,
                                   final OutputStream out) throws IOException {
        final int BYTE_BUFFER_SIZE = 0x1000;
        final byte[] buffer = new byte[BYTE_BUFFER_SIZE];
        for (int len; (len = in.read(buffer)) != -1;) {
            out.write(buffer, 0, len);
        }
    }

    public static final CanInterface CAN_ALL_INTERFACES = new CanInterface(0);

    private static native int _getCANID_SFF(final int canid);
    private static native int _getCANID_EFF(final int canid);
    private static native int _getCANID_ERR(final int canid);

    private static native boolean _isSetEFFSFF(final int canid);
    private static native boolean _isSetRTR(final int canid);
    private static native boolean _isSetERR(final int canid);

    private static native int _setEFFSFF(final int canid);
    private static native int _setRTR(final int canid);
    private static native int _setERR(final int canid);

    private static native int _clearEFFSFF(final int canid);
    private static native int _clearRTR(final int canid);
    private static native int _clearERR(final int canid);

    private static native int _openSocketRAW() throws IOException;
    private static native int _openSocketBCM() throws IOException;
    private static native void _close(final int fd) throws IOException;

    private static native int _fetchInterfaceMtu(final int fd,
                                                 final String ifName) throws IOException;
    private static native int _fetch_CAN_MTU();
    private static native int _fetch_CAN_FD_MTU();

    private static native int _discoverInterfaceIndex(final int fd,
                                                      final String ifName) throws IOException;
    private static native String _discoverInterfaceName(final int fd,
                                                        final int ifIndex) throws IOException;

    private static native void _bindToSocket(final int fd,
                                             final int ifId) throws IOException;

    private static native CanFrame _recvFrame(final int fd) throws IOException;
    private static native void _sendFrame(final int fd, final int canif,
                                          final int canid, final byte[] data) throws IOException;

    public static final int CAN_MTU = _fetch_CAN_MTU();
    public static final int CAN_FD_MTU = _fetch_CAN_FD_MTU();

    private static native int _fetch_CAN_RAW_FILTER();
    private static native int _fetch_CAN_RAW_ERR_FILTER();
    private static native int _fetch_CAN_RAW_LOOPBACK();
    private static native int _fetch_CAN_RAW_RECV_OWN_MSGS();
    private static native int _fetch_CAN_RAW_FD_FRAMES();

    private static final int CAN_RAW_FILTER = _fetch_CAN_RAW_FILTER();
    private static final int CAN_RAW_ERR_FILTER = _fetch_CAN_RAW_ERR_FILTER();
    private static final int CAN_RAW_LOOPBACK = _fetch_CAN_RAW_LOOPBACK();
    private static final int CAN_RAW_RECV_OWN_MSGS = _fetch_CAN_RAW_RECV_OWN_MSGS();
    private static final int CAN_RAW_FD_FRAMES = _fetch_CAN_RAW_FD_FRAMES();

    private static native void _setsockopt(final int fd, final int op,
                                           final int stat) throws IOException;
    private static native int _getsockopt(final int fd, final int op)
            throws IOException;

    public final static class CanId implements Cloneable {
        private int _canId = 0;

        public static enum StatusBits {
            ERR, EFFSFF, RTR
        }

        public CanId(final int address) {
            _canId = address;
        }

        public boolean isSetEFFSFF() {
            return _isSetEFFSFF(_canId);
        }

        public boolean isSetRTR() {
            return _isSetRTR(_canId);
        }

        public boolean isSetERR() {
            return _isSetERR(_canId);
        }

        public CanId setEFFSFF() {
            _canId = _setEFFSFF(_canId);
            return this;
        }

        public CanId setRTR() {
            _canId = _setRTR(_canId);
            return this;
        }

        public CanId setERR() {
            _canId = _setERR(_canId);
            return this;
        }

        public CanId clearEFFSFF() {
            _canId = _clearEFFSFF(_canId);
            return this;
        }

        public CanId clearRTR() {
            _canId = _clearRTR(_canId);
            return this;
        }

        public CanId clearERR() {
            _canId = _clearERR(_canId);
            return this;
        }

        public int getCanId_SFF() {
            return _getCANID_SFF(_canId);
        }

        public int getCanId_EFF() {
            return _getCANID_EFF(_canId);
        }

        public int getCanId_ERR() {
            return _getCANID_ERR(_canId);
        }

        @Override
        protected Object clone() {
            return new CanId(_canId);
        }

        private Set<StatusBits> _inferStatusBits() {
            final EnumSet<StatusBits> bits = EnumSet.noneOf(StatusBits.class);
            if (isSetERR()) {
                bits.add(StatusBits.ERR);
            }
            if (isSetEFFSFF()) {
                bits.add(StatusBits.EFFSFF);
            }
            if (isSetRTR()) {
                bits.add(StatusBits.RTR);
            }
            return Collections.unmodifiableSet(bits);
        }

        @Override
        public String toString() {
            return "CanId [canId=" + (isSetEFFSFF()
                    ? getCanId_EFF() : getCanId_SFF())
                    + "flags=" + _inferStatusBits() + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + _canId;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CanId other = (CanId) obj;
            if (_canId != other._canId)
                return false;
            return true;
        }
    }

    public final static class CanInterface implements Cloneable {
        private final int _ifIndex;
        private String _ifName;

        public CanInterface(final CanSocket socket, final String ifName)
                throws IOException {
            this._ifIndex = _discoverInterfaceIndex(socket._fd, ifName);
            this._ifName = ifName;
        }

        private CanInterface(int ifIndex, String ifName) {
            this._ifIndex = ifIndex;
            this._ifName = ifName;
        }

        private CanInterface(int ifIndex) {
            this(ifIndex, null);
        }

        public int getInterfaceIndex() {
            return _ifIndex;
        }

        @Override
        public String toString() {
            return "CanInterface [_ifIndex=" + _ifIndex + ", _ifName="
                    + _ifName + "]";
        }

        public String getIfName() {
            return _ifName;
        }

        public String resolveIfName(final CanSocket socket) {
            if (_ifName == null) {
                try {
                    _ifName = _discoverInterfaceName(socket._fd, _ifIndex);
                } catch (IOException e) { /* EMPTY */ }
            }
            return _ifName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + _ifIndex;
            result = prime * result
                    + ((_ifName == null) ? 0 : _ifName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CanInterface other = (CanInterface) obj;
            if (_ifIndex != other._ifIndex)
                return false;
            if (_ifName == null) {
                if (other._ifName != null)
                    return false;
            } else if (!_ifName.equals(other._ifName))
                return false;
            return true;
        }

        @Override
        protected Object clone() {
            return new CanInterface(_ifIndex, _ifName);
        }
    }

    public final static class CanFrame implements Cloneable {
        private final CanInterface canIf;
        private final CanId canId;
        private final byte[] data;

        public CanFrame(final CanInterface canIf, final CanId canId,
                        byte[] data) {
            this.canIf = canIf;
            this.canId = canId;
            this.data = data;
        }

        /* this constructor is used in native code */
        @SuppressWarnings("unused")
        private CanFrame(int canIf, int canid, byte[] data) {
            if (data.length > 8) {
                throw new IllegalArgumentException();
            }
            this.canIf = new CanInterface(canIf);
            this.canId = new CanId(canid);
            this.data = data;
        }

        public CanId getCanId() {
            return canId;
        }

        public byte[] getData() {
            return data;
        }

        public CanInterface getCanInterfacae() {
            return canIf;
        }

        @Override
        public String toString() {
            return "CanFrame [canIf=" + canIf + ", canId=" + canId + ", data="
                    + Arrays.toString(data) + "]";
        }

        @Override
        protected Object clone() {
            return new CanFrame(canIf, (CanId)canId.clone(),
                    Arrays.copyOf(data, data.length));
        }
    }

    public static enum Mode {
        RAW, BCM
    }

    private final int _fd;
    private final Mode _mode;
    private CanInterface _boundTo;

    public CanSocket(Mode mode) throws IOException {
        switch (mode) {
            case BCM:
                _fd = _openSocketBCM();
                break;
            case RAW:
                _fd = _openSocketRAW();
                break;
            default:
                throw new IllegalStateException("unkown mode " + mode);
        }
        this._mode = mode;
    }

    public void bind(CanInterface canInterface) throws IOException {
        _bindToSocket(_fd, canInterface._ifIndex);
        this._boundTo = canInterface;
    }

    public void send(CanFrame frame) throws IOException {
        _sendFrame(_fd, frame.canIf._ifIndex, frame.canId._canId, frame.data);
    }

    public CanFrame recv() throws IOException {
        return _recvFrame(_fd);
    }

    @Override
    public void close() throws IOException {
        _close(_fd);
    }

    public int getMtu(final String canif) throws IOException {
        return _fetchInterfaceMtu(_fd, canif);
    }

    public void setLoopbackMode(final boolean on) throws IOException {
        _setsockopt(_fd, CAN_RAW_LOOPBACK, on ? 1 : 0);
    }

    public boolean getLoopbackMode() throws IOException {
        return _getsockopt(_fd, CAN_RAW_LOOPBACK) == 1;
    }

    public void setRecvOwnMsgsMode(final boolean on) throws IOException {
        _setsockopt(_fd, CAN_RAW_RECV_OWN_MSGS, on ? 1 : 0);
    }

    public boolean getRecvOwnMsgsMode() throws IOException {
        return _getsockopt(_fd, CAN_RAW_RECV_OWN_MSGS) == 1;
    }
}