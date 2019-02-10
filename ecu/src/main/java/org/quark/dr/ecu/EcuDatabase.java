package org.quark.dr.ecu;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EcuDatabase {
    boolean m_loaded;
    private HashMap<Integer, ArrayList<EcuInfo>> m_ecuInfo;
    private HashMap<Integer, String> m_ecuAddressing;
    private HashMap<String, Long> m_directoryEntries;
    private String m_ecuFilePath;

    public class EcuInfo {
        public Set<String> projects;
        public String href;
        public String ecuName;
        public int addressId;
    }

    public class DatabaseException extends Exception {
        public DatabaseException(String message) {
            super(message);
        }
    }

    public ArrayList<EcuInfo> getEcuInfo(int addr) {
        return m_ecuInfo.get(addr);
    }

    public ArrayList<String> getEcuByFunctions() {
        ArrayList<String> list = new ArrayList<>();
        Iterator<String> valueIterator = m_ecuAddressing.values().iterator();
        while (valueIterator.hasNext()) {
            list.add(valueIterator.next());
        }
        return list;
    }

    public ArrayList<String> getEcuByFunctionsAndType(String type) {
        Set<String> list = new HashSet<>();
        Iterator<ArrayList<EcuInfo>> ecuArrayIterator = m_ecuInfo.values().iterator();

        while (ecuArrayIterator.hasNext()) {
            ArrayList<EcuInfo> ecuArray = ecuArrayIterator.next();
            for (EcuInfo ecuInfo : ecuArray) {
                if (ecuInfo.projects.contains(type) && m_ecuAddressing.containsKey(ecuInfo.addressId)) {
                    list.add(m_ecuAddressing.get(ecuInfo.addressId));
                }
            }
        }
        ArrayList<String> ret = new ArrayList<>();
        for (String txt : list) {
            ret.add(txt);
        }
        return ret;
    }

    public int getAddressByFunction(String name) {
        Set<Integer> keySet = m_ecuAddressing.keySet();
        for (Integer i : keySet) {
            if (m_ecuAddressing.get(i) == name) {
                return i;
            }
        }
        return -1;
    }

    public EcuDatabase() {
        m_ecuInfo = new HashMap<>();
        m_ecuAddressing = new HashMap<>();
        m_loaded = false;
        loadAddressing();
    }

    private void loadAddressing() {
        String addressingResource = "addressing.json";
        InputStream ecu_stream = getClass().getClassLoader().getResourceAsStream(addressingResource);
        String line;
        BufferedReader br;
        StringBuilder sb = new StringBuilder();

        try {
            br = new BufferedReader(new InputStreamReader(ecu_stream));
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            JSONObject jobj = new JSONObject(sb.toString());
            Iterator<String> keys = jobj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray ecuArray = jobj.getJSONArray(key);
                String name = ecuArray.getString(1);
                m_ecuAddressing.put(Integer.parseInt(key, 16), name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String loadDatabase(String ecuFile) throws DatabaseException {
        if (ecuFile.isEmpty()) {
            ecuFile = walkDir(Environment.getExternalStorageDirectory());
        }
        if (ecuFile.isEmpty()) {
            ecuFile = walkDir(Environment.getDataDirectory());
        }
        if (ecuFile.isEmpty()) {
            ecuFile = walkDir(new File("/storage"));
        }
        if (ecuFile.isEmpty()) {
            ecuFile = walkDir(new File("/mnt"));
        }
        if (ecuFile.isEmpty()) {
            throw new DatabaseException("Ecu file not found");
        }
        m_ecuFilePath = ecuFile;
        getZipEntries(ecuFile);
        String test = getZipFile("db.json");
        System.out.println(">>>>" + test);

        JSONObject jobj;
        String bytes = getZipFile(ecuFile, "db.json");
        try {
            jobj = new JSONObject(bytes);
        } catch (Exception e) {
            throw new DatabaseException("JSON conversion issue");
        }

        Set<Integer> addrSet = new HashSet<>();

        Iterator<String> keys = jobj.keys();
        for (; keys.hasNext(); ) {
            String href = keys.next();
            try {
                JSONObject ecuobj = jobj.getJSONObject(href);
                JSONArray projobjects = ecuobj.getJSONArray("projects");
                Set<String> hashSet = new HashSet<>();
                for (int i = 0; i < projobjects.length(); ++i) {
                    String project = projobjects.getString(i);
                    hashSet.add(project.toUpperCase());
                }
                int addrId = Integer.parseInt(ecuobj.getString("address"), 16);
                addrSet.add(addrId);
                EcuInfo info = new EcuInfo();
                info.ecuName = ecuobj.getString("ecuname");
                info.href = href;
                info.projects = hashSet;
                info.addressId = addrId;
                ArrayList<EcuInfo> ecuList;
                if (!m_ecuInfo.containsKey(addrId)) {
                    ecuList = new ArrayList<>();
                    m_ecuInfo.put(addrId, ecuList);
                } else {
                    ecuList = m_ecuInfo.get(addrId);
                }
                ecuList.add(info);
            } catch (Exception e) {
                e.printStackTrace();
                throw new DatabaseException("JSON parsing issue");
            }
        }

        Set<Integer> keySet = new HashSet<>(m_ecuAddressing.keySet());
        for (Integer key : keySet) {
            if (!addrSet.contains(key)) {
                m_ecuAddressing.remove(key);
            }
        }

        m_loaded = true;
        return ecuFile;
    }

    public boolean isLoaded() {
        return m_loaded;
    }

    public String walkDir(File dir) {
        String searchFile = "ECU.ZIP";
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (File f : listFile) {
                if (f.isDirectory()) {
                    String res = walkDir(f);
                    if (!res.isEmpty())
                        return res;
                } else {
                    if (f.getName().toUpperCase().equals(searchFile)) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        return "";
    }

    public void getZipEntries(String zipFile) {
        m_directoryEntries = new HashMap<>();
        try {
            InputStream zip_is = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(zip_is);
            ZipEntry ze;

            while ((ze = zis.getNextEntry()) != null) {
                String filename = ze.getName();
                long offset = 30 + ze.getName().length() + (ze.getExtra() != null ? ze.getExtra().length : 0);
                long pos = ((FileInputStream) zip_is).getChannel().position() - 12;
                m_directoryEntries.put(filename, pos);
                System.out.println(">>> " + offset + " " + pos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getZipFile(String filename) {
        try {
            long pos = m_directoryEntries.get(filename);
            byte[] array = new byte[1024];
            FileInputStream zip_is = new FileInputStream(m_ecuFilePath);
            zip_is.skip(pos);
            System.out.println(">>> curpos " + pos);
            zip_is.read(array, 0, 1024);
            Inflater inflater = new Inflater();
            inflater.setInput(array, 0, 1024);
            byte[] result = new byte[1024];
            int resultLength = inflater.inflate(result);
            inflater.end();
            return result.toString();
        } catch(IOException e)
        {
             e.printStackTrace();
             return null;
        } catch (DataFormatException e){
            e.printStackTrace();
        }
        return "failed";
    }

    static public String getZipFile(String ecuFile, String filename) {
        try {
            FileInputStream zip_is = new FileInputStream(ecuFile);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zip_is));
            ZipEntry ze;

            while ((ze = zis.getNextEntry()) != null)
            {
                if (ze.getName().equals(filename)){
                    int read;
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
