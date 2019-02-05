package org.quark.dr.ecu;

import android.os.Environment;
import android.util.JsonReader;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EcuDatabase {
    boolean m_ok;

    public EcuDatabase(){
        m_ok = false;
        String ecufile = walkdir(Environment.getExternalStorageDirectory());
        if (ecufile == null) {
            ecufile = walkdir(Environment.getDataDirectory());
        }

        byte[] bytes = getZipFile(ecufile, "db.json");
        try {
            JSONObject jobj = new JSONObject(bytes.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        m_ok = true;
    }


    public String walkdir(File dir) {
        String searchFile = "ECU.ZIP";

        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (File f : listFile) {
                if (f.isDirectory()) {
                    walkdir(f);
                } else {
                    if (f.getName().toUpperCase().equals(searchFile)){
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    private byte[] getZipFile(String ecufile, String filename)
    {
        try {
            InputStream zip_is = new FileInputStream(ecufile);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zip_is));
            ZipEntry ze;

            while ((ze = zis.getNextEntry()) != null)
            {
                if (ze.getName().equals(filename)){
                    ByteArrayOutputStream streamBuilder = new ByteArrayOutputStream();
                    int bytesRead;
                    byte[] tempBuffer = new byte[8192*2];
                    while ( (bytesRead = zis.read(tempBuffer)) != -1 ){
                        streamBuilder.write(tempBuffer, 0, bytesRead);
                    }
                    return streamBuilder.toByteArray();
                }
            }
        } catch(IOException e)
        {
             e.printStackTrace();
             return null;
        }

        return null;
    }
}
