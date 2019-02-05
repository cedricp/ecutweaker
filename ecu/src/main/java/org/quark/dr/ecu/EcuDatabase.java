package org.quark.dr.ecu;

import android.os.Environment;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EcuDatabase {
    boolean m_ok;

    public EcuDatabase(){
        m_ok = false;
        System.out.println("?? Start walking");
        //String ecufile;// = walkdir(Environment.getExternalStorageDirectory());
        //if (ecufile == null) {
        //    ecufile = walkdir(Environment.getDataDirectory());
        //}
        //if (ecufile == null) {
        String    ecufile = walkDir(new File("/storage"));

        System.out.println("?? Found " + ecufile);
        System.out.println("?? End walking");
        String bytes = getZipFile(ecufile, "db.json");
        try {
            JSONObject jobj = new JSONObject(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("?? End Json");
        m_ok = true;
    }


    public String walkDir(File dir) {
        String searchFile = "ECU.ZIP";
        String result = "";
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (File f : listFile) {
                if (f.isDirectory()) {
                    String res = walkDir(f);
                    if (!res.isEmpty())
                        result = res;
                } else {
                    if (f.getName().toUpperCase().equals(searchFile)){
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        return result;
    }

    private String getZipFile(String ecufile, String filename)
    {
        try {
            InputStream zip_is = new FileInputStream(ecufile);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zip_is));
            ZipEntry ze;

            while ((ze = zis.getNextEntry()) != null)
            {
                if (ze.getName().equals(filename)){
                    int read = 0;
                    byte[] buffer = new byte[1024];
                    StringBuilder s = new StringBuilder();

                    while ((read = zis.read(buffer, 0, 1024)) >= 0) {
                        s.append(new String(buffer, 0, read));
                    }
                    return s.toString();
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
