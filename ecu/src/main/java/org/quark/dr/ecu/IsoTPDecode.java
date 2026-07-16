package org.quark.dr.ecu;

import java.util.ArrayList;

/**
 * Decodes ISO-TP (ISO 15765-2 Transport Protocol) CAN frames.
 * <p>
 * ISO-TP is used for transmitting large packets over CAN bus. This class handles
 * both single-frame (SF) and multi-frame (FF+CF) message decoding.
 *
 * Frame types:
 * - Single Frame (starts with '0'): For messages up to 7 bytes
 * - First Frame (starts with '1'): For messages larger than 7 bytes
 * - Consecutive Frame (starts with '2'): Continuation of multi-frame messages
 */
public class IsoTPDecode {
    /** List of received CAN response frames to decode. */
    private final ArrayList<String> responses;

    /**
     * Creates a decoder for the given response frames.
     *
     * @param mess List of hexadecimal CAN response strings
     */
    public IsoTPDecode(ArrayList<String> mess) {
        responses = mess;
    }

    /**
     * Checks if a string contains only valid hexadecimal characters.
     *
     * @param text The string to validate
     * @return true if the string is valid hexadecimal, false otherwise
     */
    public static boolean isHexadecimal(String text) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'};

        for (char symbol : text.toCharArray()) {
            boolean found = false;
            for (char hexDigit : hexDigits) {
                if (symbol == hexDigit) {
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }
        return true;
    }

    /**
     * Decodes the received CAN frames into a single data string.
     *
     * @return The decoded hexadecimal data string, or an error message
     */
    public String decodeCan() {
        String result;
        int cframe = 0;
        int nbytes = 0;

        if (responses.isEmpty())
            return "ERROR : NO DATA";

        if (responses.size() == 1) {
            // Single frame message (up to 7 bytes)
            String line = responses.get(0);
            if (!isHexadecimal(line)) {
                return "ERROR : NON HEXA";
            }
            if (line.charAt(0) == '0') {
                // Single Frame: '0' + byte count + data
                String nbytes_hex = responses.get(0).substring(1, 2);
                nbytes = Integer.parseInt(nbytes_hex, 16);
                result = line.substring(2, 2 + nbytes * 2);
            } else {
                result = "ERROR : BAD CAN FORMAT (SINGLE LINE)";
            }
        } else {
            // Multi-frame message
            if (responses.get(0).charAt(0) == '1') {
                // First Frame: '1' + total length (3 hex chars) + first 6 data bytes
                String line = responses.get(0);
                if (!isHexadecimal(line)) {
                    return "ERROR : NON HEXA";
                }
                String nbytes_hex = line.substring(1, 4);
                nbytes = Integer.parseInt(nbytes_hex, 16);
                cframe = 1;
                result = line.substring(4, 16);
            } else {
                result = "ERROR : BAD CAN FORMAT (MULTILINE)";
            }

            // Create a copy to avoid modifying the original list
            ArrayList<String> remainingResponses = new ArrayList<>(responses.subList(1, responses.size()));
            StringBuilder resultBuilder = new StringBuilder(result);
            for (String fr : remainingResponses) {
                if (!isHexadecimal(fr)) {
                    return "ERROR : NON HEXA";
                }
                if (fr.charAt(0) == '2') {
                    // Consecutive Frame: '2' + sequence number + data
                    int tmp_fn = Integer.parseInt(fr.substring(1, 2), 16);
                    if (tmp_fn != (cframe % 16)) {
                        return "ERROR : BAD CFC";
                    }
                    cframe += 1;
                    resultBuilder.append(fr, 2, (Math.min(fr.length(), 16)));
                } else {
                    return "ERROR : BAD CAN FORMAT";
                }
            }
            result = resultBuilder.toString();
        }
        if (result.length() >= nbytes * 2)
            result = result.substring(0, nbytes * 2);
        else
            result = "ERROR : RESPONSE TOO SHORT (" + result + ")";
        return result;
    }
}
