package org.quark.dr.ecu;

import android.os.Environment;
import android.support.v4.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EcuDatabase {
    class CustomZipEntry{
        public long compressedSize, pos, uncompressedSize;
    }

    boolean m_loaded;
    private HashMap<Integer, ArrayList<EcuInfo>> m_ecuInfo;
    private HashMap<Integer, String> m_ecuAddressing;
    private HashMap<String, CustomZipEntry> m_directoryEntries;
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

    public String loadDatabase(String ecuFilename, String appDir) throws DatabaseException {
        if (ecuFilename.isEmpty()) {
            ecuFilename = walkDir(Environment.getExternalStorageDirectory());
        }
        if (ecuFilename.isEmpty()) {
            ecuFilename = walkDir(Environment.getDataDirectory());
        }
        if (ecuFilename.isEmpty()) {
            ecuFilename = walkDir(new File("/storage"));
        }
        if (ecuFilename.isEmpty()) {
            ecuFilename = walkDir(new File("/mnt"));
        }
        if (ecuFilename.isEmpty()) {
            throw new DatabaseException("Ecu file not found");
        }

        String bytes;
        m_ecuFilePath = ecuFilename;
        String indexFileName = appDir + "/ecu.idx";

        File indexFile = new File(indexFileName);
        File ecuFile = new File(m_ecuFilePath);
        long indexTimeStamp = indexFile.lastModified();
        long ecuTimeStamp = ecuFile.lastModified();

        /*
         * If index is already made, use it
         * Also check files exists and timestamps to force [re]scan
         */
        if (indexFile.exists() && (indexTimeStamp < ecuTimeStamp) && importZipEntries(appDir)){
            bytes = getZipFile("db.json");
        } else {
            /*
             * Else create it
             */
            getZipEntries(m_ecuFilePath);
            exportZipEntries(appDir);
            bytes = getZipFile("db.json");
        }

        JSONObject jsonRootObject;
        try {
            jsonRootObject = new JSONObject(bytes);
        } catch (Exception e) {
            throw new DatabaseException("JSON conversion issue");
        }

        Set<Integer> addressSet = new HashSet<>();

        Iterator<String> keys = jsonRootObject.keys();
        for (; keys.hasNext(); ) {
            String href = keys.next();
            try {
                JSONObject ecuJsonObject = jsonRootObject.getJSONObject(href);
                JSONArray projectJsonObject = ecuJsonObject.getJSONArray("projects");
                Set<String> hashSet = new HashSet<>();
                for (int i = 0; i < projectJsonObject.length(); ++i) {
                    String project = projectJsonObject.getString(i);
                    hashSet.add(project.toUpperCase());
                }
                int addrId = Integer.parseInt(ecuJsonObject.getString("address"), 16);
                addressSet.add(addrId);
                EcuInfo info = new EcuInfo();
                info.ecuName = ecuJsonObject.getString("ecuname");
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
            if (!addressSet.contains(key)) {
                m_ecuAddressing.remove(key);
            }
        }

        m_loaded = true;
        return ecuFilename;
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

    public void exportZipEntries(String directoryName){
        Iterator zeit = m_directoryEntries.entrySet().iterator();
        JSONArray mainJson =  new JSONArray();
        while(zeit.hasNext()){
            HashMap.Entry pair = (HashMap.Entry)zeit.next();
            JSONObject jsonEntry = new JSONObject();
            try {
                jsonEntry.put("pos", ((CustomZipEntry) pair.getValue()).pos);
                jsonEntry.put("realsize", ((CustomZipEntry) pair.getValue()).uncompressedSize);
                jsonEntry.put("compsize", ((CustomZipEntry) pair.getValue()).compressedSize);
                jsonEntry.put("name", ((String)pair.getKey()));
                mainJson.put(jsonEntry);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String fileName = directoryName + "/ecu.idx";
        try {
            FileWriter fileWriter = new FileWriter(fileName);
            fileWriter.write(mainJson.toString(0));
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean importZipEntries(String directoryName){
        String fileName =  directoryName + "/ecu.idx";
        m_directoryEntries = new HashMap<>();
        try {
            JSONArray mainJson = new JSONArray(readFile(fileName));
            for (int i = 0; i < mainJson.length(); ++i){
                JSONObject zipEntryJson = mainJson.getJSONObject(i);
                CustomZipEntry ze = new CustomZipEntry();
                ze.pos = zipEntryJson.getLong("pos");
                ze.compressedSize = zipEntryJson.getLong("compsize");
                ze.uncompressedSize = zipEntryJson.getLong("realsize");
                m_directoryEntries.put(zipEntryJson.getString("name"), ze);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private String readFile(String file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader (file));
        String         line = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls = System.getProperty("line.separator");
        try {
            while((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        } finally {
            reader.close();
        }
    }

    /*
     * Map zip entries to fast load them
     */
    public boolean getZipEntries(String zipFile) {
        m_directoryEntries = new HashMap<>();
        try {
            FileInputStream zip_is = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(zip_is);
            ZipEntry ze;
            long pos = 0;
            while ((ze = zis.getNextEntry()) != null) {
                String filename = ze.getName();
                long offset = 30 + ze.getName().length() + (ze.getExtra() != null ? ze.getExtra().length : 0);
                pos += offset;
                CustomZipEntry cze = new CustomZipEntry();
                cze.pos = pos;
                cze.compressedSize = ze.getCompressedSize();
                cze.uncompressedSize = ze.getSize();
                m_directoryEntries.put(filename, cze);
                pos += cze.compressedSize;
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getZipFile(String filename) {
        try {
            long pos = m_directoryEntries.get(filename).pos;
            long compressedSize = m_directoryEntries.get(filename).compressedSize;
            long realSize = m_directoryEntries.get(filename).uncompressedSize;
            byte[] array = new byte[(int)compressedSize];
            FileInputStream zip_is = new FileInputStream(m_ecuFilePath);
            zip_is.getChannel().position(pos);
            zip_is.read(array, 0, (int)compressedSize);
            Inflater inflater = new Inflater(true);
            inflater.setInput(array, 0, (int)compressedSize);
            byte[] result = new byte[(int)realSize];
            int resultLength = inflater.inflate(result);
            inflater.end();
            return new String(result, 0, resultLength, "UTF-8");
        } catch(IOException e)
        {
             e.printStackTrace();
             return null;
        } catch (DataFormatException e){
            e.printStackTrace();
        }
        return "";
    }
}
