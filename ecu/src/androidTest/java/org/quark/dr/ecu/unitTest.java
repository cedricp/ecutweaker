package org.quark.dr.ecu;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class unitTest {
    @Test
    public void test_ecu() {
        assertTrue(getClass().getResource("test.json") == null);
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("test.json");

        Ecu ecu = new Ecu(is);
        System.out.println(ecu.funcaddr);
        System.out.println(ecu.protocol);
        System.out.println(ecu.ecu_name);
        System.out.println(ecu.funcname);
        // ECU Methods check
        byte[] testbyte = new byte[] {0x00, -1, 10};
        byte[] ucttest = new byte[] {0x61, 0x0A, 0x16, 0x32, 0x32, 0x02, 0x58, 0x00, (byte)0xB4, 0x3C,
                0x3C, 0x1E, 0x3C, 0x0A, 0x0A, 0x0A, 0x0A, 0x01, 0x2C, 0x5C, 0x61, 0x67, (byte)0xB5, (byte)0xBB,
                (byte)0xC1, 0X0A};
        byte[] ucttest2 = new byte[26];

        BigInteger bi = new BigInteger("FF", 16);
        BigInteger bi2 = new BigInteger("11111110", 2);

        assertThat(ecu.hex8ToSigned(125), is(125));
        assertThat(ecu.hex8ToSigned(254), is(-2));
        assertThat(ecu.hex16ToSigned(63000), is(-2536));
        assertThat(ecu.hex16ToSigned(6000), is(6000));
        assertThat(ecu.padLeft("FFFF", 8, "0"), is("0000FFFF"));
        assertThat(ecu.hexStringToByteArray("00FF0A"), is(testbyte));
        assertThat(ecu.integerToBinaryString(1, 8), is ("00000001"));
        assertThat(ecu.integerToBinaryString(254, 8), is ("11111110"));

        assertThat(bi.intValue(), is(255));
        assertThat(bi2.intValue(), is(254));

        HashMap<String, String> hash = ecu.getRequestValues(ucttest, "ReadDataByLocalIdentifier: misc timings and values", true);
        HashMap<String, Object> hash2 = new HashMap<>();

        Iterator<String> it = hash.keySet().iterator();
        for (;it.hasNext();){
            String key = it.next();
            System.out.println(">>>>>>> " + key + " = " + hash.get(key));
            hash2.put(key, hash.get(key));
        }

        byte[] frame = ecu.setRequestValues("WriteDataByLocalIdentifier: misc timings and val.", hash2);
        for(byte c : frame) {
            System.out.format("%02X ", c);
        }
        System.out.println();
    }
}