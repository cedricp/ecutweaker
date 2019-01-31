package org.quark.dr.ecu;

import java.util.ArrayList;

/*
 * Class to decode a CAN frame (single or multi line)
 */

public class IsotpDecode {
    private ArrayList<String> responses;

    public IsotpDecode(ArrayList<String> mess){
        responses = mess;
    }

    public static boolean isHexadecimal(String text) {
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

    public String decodeCan(){
        String result = "";
        boolean noerrors = true;
        int cframe = 0;
        int nbytes = 0;
        int nframes = 0;

        if (responses.size() == 0)
            return "ERROR : NO DATA";

        if (responses.size() == 1){
            String line = responses.get(0);
            if (!isHexadecimal(line)){
                noerrors = false;
                return "ERROR : NON HEXA";
            }
            if (line.substring(0,1).equals("0")) {
                String nbytes_hex = responses.get(0).substring(1,2);
                nbytes = Integer.parseInt(nbytes_hex, 16);
                nframes = 1;
                result = line.substring(2, 2 + nbytes * 2);
            } else {
                noerrors = false;
                result = "ERROR : BAD CAN FORMAT (SINGLE LINE)";
            }
        } else {
            if (responses.get(0).substring(0,1).equals("1")){
                String line = responses.get(0);
                if (!isHexadecimal(line)){
                    noerrors = false;
                    return "ERROR : NON HEXA";
                }
                String nbytes_hex = line.substring(1,4);
                nbytes = Integer.parseInt(nbytes_hex, 16);
                nframes = nbytes / 7 + 1;
                cframe = 1;
                result = line.substring(4,16);
            } else {
                noerrors = false;
                result = "ERROR : BAD CAN FORMAT (MULTILINE)";
            }

            responses.remove(0);
            for (String fr: responses){
                if (!isHexadecimal(fr)){
                    return "ERROR : NON HEXA";
                }
                if (fr.substring(0,1).equals("2")){
                    int tmp_fn = Integer.parseInt(fr.substring(1,2), 16);
                    if (tmp_fn != (cframe % 16)){
                        noerrors = false;
                        result = "ERROR : BAD CFC";
                        break;
                    }
                    cframe += 1;
                    result += fr.substring(2, (fr.length() >= 16 ? 16 : fr.length()));
                } else {
                    noerrors = false;
                    result = "ERROR : BAD CAN FORMAT";
                }
            }
        }
        if (result.length() >= nbytes*2)
            result = result.substring(0, nbytes*2);
        else
            result = "ERROR : RESPONSE TOO SHORT (" + result + ")";
        return result;
    }
}
