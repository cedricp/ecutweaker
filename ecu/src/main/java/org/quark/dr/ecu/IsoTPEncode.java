package org.quark.dr.ecu;

import java.util.ArrayList;

/**
 * Encodes data into ISO-TP (ISO 15765-2 Transport Protocol) CAN frames.
 * <p>
 * ISO-TP is used for transmitting large packets over CAN bus. This class handles
 * both single-frame (SF) and multi-frame (FF+CF) message encoding.
 *
 * Frame types:
 * - Single Frame (starts with '0'): For messages up to 7 bytes
 * - First Frame (starts with '1'): For messages larger than 7 bytes, followed by Consecutive Frames
 * - Consecutive Frame (starts with '2'): Continuation of multi-frame messages
 */
public class IsoTPEncode {
    /** The hexadecimal message to encode. */
    private final String mmessage;

    /**
     * Creates an encoder for the given message.
     *
     * @param mess Hexadecimal string to encode
     */
    public IsoTPEncode(String mess) {
        mmessage = mess;
    }

    /**
     * Returns the formatted frames as a newline-separated string.
     *
     * @return Formatted CAN frames
     */
    public String getFormatted() {
        ArrayList<String> raw_command = getFormattedArray();
        StringBuilder ret = new StringBuilder();
        for (String frame : raw_command) {
            ret.append(frame);
            ret.append("\n");
        }
        return ret.toString();
    }

    /**
     * Encodes the message into ISO-TP frames.
     * <p>
     * For messages under 8 bytes, returns a single frame.
     * For larger messages, returns a first frame followed by consecutive frames.
     *
     * @return List of ISO-TP frame strings
     */
    public ArrayList<String> getFormattedArray() {
        ArrayList<String> raw_command = new ArrayList<>();
        String message = mmessage;
        message = message.replace(" ", "");
        if (!IsoTPDecode.isHexadecimal(message)) {
            return raw_command;
        }

        if ((message.length() % 2) != 0)
            return raw_command;

        int cmd_len = message.length() / 2;
        if (cmd_len < 8) {
            // Single Frame: '0' + byte count (1 hex digit) + data
            raw_command.add(String.format("%02X", cmd_len) + message);
        } else {
            // Multi-frame: First Frame + Consecutive Frames
            int frame_number = 1;
            String header = "1" + String.format("%03X", cmd_len);
            // First Frame: '1' + total length (3 hex chars) + first 6 data bytes
            raw_command.add(header.substring(0, 3) + message.substring(0, 12));
            message = message.substring(12);
            while (!message.isEmpty()) {
                // Consecutive Frame: '2' + sequence number (0-15) + up to 7 data bytes
                header = "2" + String.format("%X", frame_number++);
                int remaining_len = message.length();
                raw_command.add(header + message.substring(0, (Math.min(remaining_len, 14))));
                message = message.substring((Math.min(remaining_len, 14)));
            }
        }

        return raw_command;
    }
}
