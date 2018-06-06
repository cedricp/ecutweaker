package org.quark.dr.ecu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Ecu {
    public String global_endian;
    public HashMap<String, EcuRequest> requests;
    public HashMap<String, EcuDevice> devices;
    public HashMap<String, EcuData> data;
    public String protocol, funcname, funcaddr, ecu_name;
    public String kw1, kw2, ecu_send_id, ecu_recv_id;
    public boolean fastinit;
    public int baudrate;

    public class EcuDataItem{
        public int firstbyte;
        public int bitoffset;
        public boolean ref;
        public String endian = "";
        public String req_endian;
        public String name;
        EcuDataItem(JSONObject json, String name){
            req_endian = global_endian;
            this.name = name;
            try {
                if (json.has("firstbyte")) firstbyte = json.getInt("firstbyte");
                if (json.has("bitoffset")) bitoffset = json.getInt("bitoffset");
                if (json.has("ref")) ref = json.getBoolean("ref");
                if (json.has("endian")) endian = json.getString("endian");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public class EcuDevice {
        public int dtc;
        public int dtctype;
        HashMap<String, String> devicedata;
        public String name;

        EcuDevice(JSONObject json){
            devicedata = new HashMap<>();
            try {
                this.name = json.getString("name");
                dtctype = json.getInt("dtctype");
                dtc = json.getInt("dtc");

                JSONObject devdataobj = json.getJSONObject("devicedata");
                Iterator<String> keys = devdataobj.keys();
                for (;keys.hasNext();){
                    String key = keys.next();
                    devicedata.put(key, devdataobj.getString(key));
                }
            } catch (Exception e){

            }
        }
    }

    public class EcuData {
        public int bitscount = 8;
        public boolean scaled = false;
        public boolean signed = false;
        public boolean isbyte = false;
        public boolean binary = false;
        public int bytescount = 1;
        public boolean bytesascii = false;
        public float step = 1.0f;
        public float offset = 0.0f;
        public float divideby = 1.0f;
        public String format = "";
        public HashMap<Integer, String> lists;
        public HashMap<String, Integer> items;
        public String description;
        public String unit = "";
        public String comment = "";
        public String name;
        EcuData(JSONObject json, String name) {
            this.name = name;
            try {
                lists = new HashMap<>();
                items = new HashMap<>();
                if(json.has("bitscount")) bitscount = json.getInt("bitscount");
                if(json.has("scaled")) scaled = json.getBoolean("scaled");
                if(json.has("byte")) isbyte = json.getBoolean("byte");
                if(json.has("signed")) signed = json.getBoolean("signed");
                if(json.has("binary")) binary = json.getBoolean("binary");
                if(json.has("bytesascii")) bytesascii = json.getBoolean("bytesascii");
                if(json.has("bytescount")) bytescount = json.getInt("bytescount");
                if(json.has("step")) step = (float)json.getDouble("step");
                if(json.has("offset")) offset = (float)json.getDouble("offset");
                if(json.has("divideby")) divideby = (float)json.getDouble("divideby");
                if(json.has("format")) format = json.getString("format");
                if(json.has("description")) description = json.getString("description");
                if(json.has("unit")) unit = json.getString("unit");
                if(json.has("comment")) comment = json.getString("comment");

                if (json.has("lists")) {
                    JSONObject listobj = json.getJSONObject("lists");
                    Iterator<String> keys = listobj.keys();
                    for (; keys.hasNext(); ) {
                        String key = keys.next();
                        lists.put(Integer.parseInt(key), listobj.getString(key));
                        items.put(listobj.getString(key), Integer.parseInt(key));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public byte[] setValue(Object value, byte[] byte_list, EcuDataItem dataitem){
            int start_byte = dataitem.firstbyte - 1;
            int start_bit = dataitem.bitoffset;
            boolean little_endian = false;

            if (global_endian == "Little")
                little_endian = true;

            if (dataitem.endian == "Little")
                little_endian = true;

            if (dataitem.endian == "Big")
                little_endian = false;

            String finalbinvalue = "";

            if (bytesascii){
                if (value instanceof String == false){
                    throw new ClassCastException("Value must be a string");
                }
                String strvalue = (String)value;
                if (bytescount > strvalue.length())
                    strvalue = padLeft(strvalue, bytescount, " ");
                if (bytescount < strvalue.length())
                    strvalue = strvalue.substring(0, bytescount);

                strvalue = stringToHex(strvalue);
                finalbinvalue = hexToBinary(strvalue);
            } else {
                if (scaled) {
                    // We want a float or integer here
                    float floatval;
                    if (value instanceof Integer) {
                        floatval = (float) ((Integer) value);
                    } else if (value instanceof Float) {
                        floatval = (float) value;
                    } else if (value instanceof String) {
                        floatval = Float.valueOf((String)value);
                    } else {
                        throw new ClassCastException("Value must be an integer or float");
                    }


                    floatval = ((floatval * divideby) - offset) / step;
                    int intval = (int) floatval;
                    finalbinvalue = integerToBinaryString(intval, bitscount);
                } else {
                    // Hex string
                    if (value instanceof String == false) {
                        throw new ClassCastException("Value must be hex a string");
                    }
                    String vv = (String)value;
                    finalbinvalue = hexToBinary(vv.replaceAll(" ", ""));
                }
            }

            finalbinvalue = padLeft(finalbinvalue, bitscount, "0");

            int numreqbytes = (int)(Math.ceil((float)bitscount + start_bit) / 8.f);
            byte[] request_bytes = Arrays.copyOfRange(byte_list, start_byte, start_byte + numreqbytes);
            String requestasbin = "";

            for (int i = 0; i < request_bytes.length; ++i){
                requestasbin += integerToBinaryString(request_bytes[i], 8);
            }

            char[] binreq = requestasbin.toCharArray();
            char[] binfin = finalbinvalue.toCharArray();

            if (!little_endian){
                // Big endian
                for (int i = 0; i < bitscount; ++i){
                    binreq[i + start_bit] = binfin[i];
                }
            } else {
                // Little endian
                int remainingbytes = bitscount;
                int lastbit = 7 - start_bit + 1;
                int firstbit = lastbit - bitscount;

                if (firstbit < 0)
                    firstbit = 0;

                int count = 0;
                for (int i = firstbit; i < lastbit; ++i, ++count){
                    binreq[i] = binfin[count];
                }

                remainingbytes -= count;

                int currentbyte = 1;
                while(remainingbytes >= 8){
                    for (int i = 0; i < 8; ++i){
                        binreq[currentbyte * 8 + i] = binfin[count];
                        ++count;
                        remainingbytes -= 8;
                        currentbyte += 1;
                    }
                }

                if (remainingbytes > 0){
                    lastbit = 8;
                    firstbit = lastbit - remainingbytes;
                    for(int i = firstbit; i < lastbit; ++i){
                        binreq[currentbyte * 8 + i] = binfin[count];
                        ++count;
                    }

                }
            }

            BigInteger valueashex = new BigInteger(new String(binreq), 2);
            String str16 = padLeft(valueashex.toString(16), bytescount*2, "0");

            for (int i = 0; i < numreqbytes; ++i){
                String hexpart = str16.substring(i*2, i*2 + 2);
                byte[] b = hexStringToByteArray(hexpart);
                byte_list[i + start_byte] = b[0];
            }

            return byte_list;
        }

        public String getHexValue(byte[] resp, EcuDataItem dataitem){
            int startByte = dataitem.firstbyte;
            int startBit = dataitem.bitoffset;
            int bits = bitscount;

            boolean little_endian = false;

            if (global_endian.equals("Little"))
                little_endian = true;

            if (dataitem.endian.equals("Little"))
                little_endian = true;
            if (dataitem.endian.equals("Big"))
                little_endian = false;

            int databytelen = (int)(Math.ceil((float)bits) / 8.0f);
            int reqdatabytelen = (int)(Math.ceil((float)bits + (float)startBit) / 8.0f);

            int sb = startByte - 1;

            if ((sb + databytelen) > resp.length) {
                throw new ArrayIndexOutOfBoundsException("Response too short");
            }

            String hextobin = new String();

            for (int i = 0; i < reqdatabytelen; ++i){
                byte b = resp[i+sb];
                hextobin += byteToBinaryString(b, 8);
            }

            String hex = new String();
            if (little_endian){
                int totalremainingbits = bits;
                int lastbit = 7 - startBit + 1;
                int firstbit = lastbit - bits;
                if (firstbit < 0)
                    firstbit = 0;

                String tmp_bin = hextobin.substring(firstbit, lastbit);
                totalremainingbits -= lastbit -firstbit;

                if (totalremainingbits > 8) {
                    int offset1 = 8;
                    int offset2 = offset1 + ((reqdatabytelen - 2) * 8);
                    tmp_bin += hextobin.substring(offset1, offset2);
                    totalremainingbits -= offset2 - offset1;
                }

                if (totalremainingbits > 0){
                    int offset1 = (reqdatabytelen - 1) * 8;
                    int offset2 = offset1 - totalremainingbits;
                    tmp_bin += hextobin.substring(offset2, offset1);
                    totalremainingbits -= offset1 - offset2;
                    BigInteger bigval = new BigInteger(tmp_bin, 2);
                    hex = bigval.toString(16);
                }

                if (totalremainingbits != 0){
                    throw new ArithmeticException("Problem computing little endian value");
                }
            } else {
                BigInteger bigval = new BigInteger(hextobin, 2);
                hex = bigval.toString(16);
            }

            return padLeft(hex, databytelen * 2, "0");
        }

        public int getIntValue(byte[] resp, EcuDataItem dataitem){
            String val = getHexValue(resp, dataitem);
            return Integer.parseInt(val);
        }

        public String getDisplayValueWithUnit(byte[] resp, EcuDataItem dataitem){
            return getDisplayValue(resp, dataitem) + " " + unit;
        }

        public String getDisplayValue(byte[] resp, EcuDataItem dataitem){
            String hexval = getHexValue(resp, dataitem);
             if (bytesascii){
                 byte[] s = hexStringToByteArray(hexval);
                 return new String(s);
             }

             if (!scaled){
                 BigInteger bigInteger = new BigInteger(hexval, 16);
                 int val = bigInteger.intValue();

                 if (signed){
                     // Check that
                     if (bytescount == 1) {
                         val = hex8ToSigned(val);
                     } else if (bytescount == 2){
                         val = hex16ToSigned(val);
                     } // 32 bits are already signed
                 }

                 if (lists.containsKey(val))
                     return lists.get(val);

                 return hexval;
             }

            BigInteger bigInteger = new BigInteger(hexval, 16);
            int val = bigInteger.intValue();

            if (signed){
                if (bytescount == 1){
                    val = hex8ToSigned(val);
                } else if (bytescount == 2){
                    val = hex16ToSigned(val);
                }
            }

            if (divideby == 0.f){
                throw new ArithmeticException("Division by zero");
            }

            float res = ((float)val * step + (offset)) / divideby;

            if (format.length() > 0 && format.contains(".")){
                String[] parts = format.split(".");
                if (parts.length > 0){
                    String fmt = "%." + parts[1].length() + "f";
                    String fmtd = String.format(fmt, res);
                    return fmtd;
                }
            }

            return Float.toString(res);
        }
    }

    public static String integerToBinaryString(int b, int padding){
        return padLeft(Integer.toBinaryString(b), padding, "0");
    }

    public static String byteToBinaryString(int b, int padding){
        return padLeft(Integer.toBinaryString(b & 0xFF), padding, "0");
    }

    public String hexToBinary(String Hex)
    {
        BigInteger i = new BigInteger(Hex, 16);
        String Bin = i.toString(2);
        return Bin;
    }

    public static int hex8ToSigned(int val){
        return -((val) & 0x80) | (val & 0x7f);
    }

    public static int hex16ToSigned(int val){
        return -((val) & 0x8000) | (val & 0x7fff);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0, j = 0; i < len; i += 2, ++j) {
            data[j] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static String stringToHex(String string) {
        StringBuilder buf = new StringBuilder(1024);
        for (char ch: string.toCharArray()) {
            buf.append(String.format("%02x", (int) ch));
        }
        return buf.toString();
    }

    public static String padLeft(String str, int length, String padChar) {
        if (str.length() >= length)
            return str.substring(0, length);
        String pad = "";
        for (int i = 0; i < length; i++) {
            pad += padChar;
        }
        return pad.substring(str.length()) + str;
    }

    public class EcuRequest {
        public class SDS {
            public boolean nosds = true;
            public boolean plant = true;
            public boolean aftersales = true;
            public boolean engineering = true;
            public boolean supplier = true;
        }
        public int minbytes = 0;
        public int shiftbytescount = 0;
        public String replybytes;
        public String sentbytes;
        public boolean manualsend = false;
        public HashMap<String, EcuDataItem> recvbyte_dataitems;
        public HashMap<String, EcuDataItem> sendbyte_dataitems;
        public String name;
        public SDS sds;

        EcuDataItem getSendDataItem(String item){
            return sendbyte_dataitems.get(item);
        }

        EcuRequest(JSONObject json) {
            sds = new SDS();
            recvbyte_dataitems = new HashMap<>();
            sendbyte_dataitems = new HashMap<>();
            try {
                name = json.getString("name");
                if (json.has("minbytes")) minbytes = json.getInt("minbytes");
                if (json.has("shiftbytescount")) shiftbytescount = json.getInt("shiftbytescount");
                if (json.has("replybytes")) replybytes = json.getString("replybytes");
                if (json.has("manualsend")) manualsend = json.getBoolean("manualsend");
                if (json.has("sentbytes")) sentbytes = json.getString("sentbytes");

                if (json.has("deny_sds")) {
                    JSONArray denysdsobj = json.getJSONArray("deny_sds");
                    for (int i = 0; i < denysdsobj.length(); ++i){
                        String s = denysdsobj.getString(i);
                        if (s.equals("nosds")) sds.nosds = false;
                        if (s.equals("plant")) sds.plant = false;
                        if (s.equals("aftersales")) sds.aftersales = false;
                        if (s.equals("engineering")) sds.engineering = false;
                        if (s.equals("supplier")) sds.supplier = false;
                    }
                }
                if (json.has("sendbyte_dataitems")) {
                    JSONObject sbdiobj = json.getJSONObject("sendbyte_dataitems");
                    Iterator<String> keys = sbdiobj.keys();
                    for (; keys.hasNext(); ) {
                        String key = keys.next();
                        JSONObject dataitemobj = sbdiobj.getJSONObject(key);
                        EcuDataItem dataitem = new EcuDataItem(dataitemobj, key);
                        sendbyte_dataitems.put(key, dataitem);

                    }
                }
                if (json.has("receivebyte_dataitems")) {
                    JSONObject sbdiobj = json.getJSONObject("receivebyte_dataitems");
                    Iterator<String> keys = sbdiobj.keys();
                    for (; keys.hasNext(); ) {
                        String key = keys.next();
                        JSONObject dataitemobj = sbdiobj.getJSONObject(key);
                        EcuDataItem dataitem = new EcuDataItem(dataitemobj, key);
                        recvbyte_dataitems.put(key, dataitem);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    Ecu(InputStream is){
        String line;
        BufferedReader br;
        StringBuilder sb = new StringBuilder();

        try {
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            init(new JSONObject(sb.toString()));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    Ecu(String json){
        try {
            init(new JSONObject(json));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    Ecu(JSONObject ecudef) {
        init(ecudef);
    }

    public EcuData getData(String dataname){
        EcuData d = data.get(dataname);
        return d;
    }

    public EcuRequest getRequest(String req_name){
        EcuRequest er = requests.get(req_name);
        return er;
    }

    public String getRequestData(byte[] bytes, String requestname, String dataname){
        EcuDataItem dataitem = getRequest(requestname).recvbyte_dataitems.get(dataname);
        EcuData ecudata = getData(dataname);
        return ecudata.getDisplayValue(bytes, dataitem);
    }

    public int getTxId(){
        return Integer.getInteger(ecu_send_id);
    }

    public int getRxId(){
        return Integer.getInteger(ecu_recv_id);
    }

    public HashMap<String, String> getRequestValues(byte[] bytes, String requestname, boolean with_units){
        EcuRequest request = getRequest(requestname);
        HashMap<String, String> hash = new HashMap<>();
        Set<String> keys = request.recvbyte_dataitems.keySet();
        Iterator<String> it = keys.iterator();
        for (;it.hasNext();){
            String key = it.next();
            EcuDataItem dataitem = request.recvbyte_dataitems.get(key);
            EcuData ecudata = getData(key);
            if (with_units) {
                String val = ecudata.getDisplayValue(bytes, dataitem);
                hash.put(key, val);
            } else {
                String val = ecudata.getDisplayValueWithUnit(bytes, dataitem);
                hash.put(key, val);
            }
        }
        return hash;
    }

    public byte[] setRequestValues(String requestname, HashMap<String, Object> hash){
        EcuRequest req = getRequest(requestname);
        byte[] barray = hexStringToByteArray(req.sentbytes);
        for (Map.Entry<String, Object> entry: hash.entrySet()){
            EcuDataItem item = req.getSendDataItem(entry.getKey());
            EcuData data = getData(entry.getKey());
            barray = data.setValue(entry.getValue(), barray, item);
        }
        return barray;
    }

    private void init(JSONObject ecudef){
        requests = new HashMap<>();
        devices = new HashMap<>();
        data = new HashMap<>();

        try {
            if (ecudef.has("endian")) global_endian = ecudef.getString("endian");
            if (ecudef.has("ecuname")) ecu_name = ecudef.getString("ecuname");

            if (ecudef.has("obd")) {
                JSONObject obd = ecudef.getJSONObject("obd");
                protocol = obd.getString("protocol");
                funcname = obd.getString("funcname");
                if (protocol.equals("CAN")) {
                    ecu_send_id = obd.getString("send_id");
                    ecu_recv_id = obd.getString("recv_id");
                    if (obd.has("baudrate ")) baudrate = obd.getInt("baudrate");

                }
                if (protocol.equals("KWP2000")) {
                    fastinit = obd.getBoolean("fastinit");
                }
                if (obd.has("funcaddr")) funcaddr = obd.getString("funcaddr");
            }

            JSONArray req_array = ecudef.getJSONArray("requests");
            for (int i = 0; i < req_array.length(); ++i) {
                JSONObject reqobj = req_array.getJSONObject(i);
                EcuRequest ecureq = new EcuRequest(reqobj);
                requests.put(ecureq.name, ecureq);
            }

            JSONArray dev_array = ecudef.getJSONArray("devices");
            for (int i = 0; i < dev_array.length(); ++i) {
                JSONObject devobj = dev_array.getJSONObject(i);
                EcuDevice ecudev = new EcuDevice(devobj);
                devices.put(ecudev.name, ecudev);
            }

            JSONObject dataobjs = ecudef.getJSONObject("data");
            Iterator<String> keys = dataobjs.keys();
            for (; keys.hasNext(); ) {
                String key = keys.next();
                JSONObject dataobj = dataobjs.getJSONObject(key);
                EcuData ecudata = new EcuData(dataobj, key);
                data.put(key, ecudata);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
