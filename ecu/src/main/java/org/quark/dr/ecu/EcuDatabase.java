package org.quark.dr.ecu;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class EcuDatabase {
    boolean m_loaded;
    private HashMap<Integer, ArrayList<EcuInfo>> m_ecuInfo;
    private HashMap<Integer, String> m_ecuAddressing;
    private Set<String> m_projectSet;
    private String m_ecuFilePath;
    private ZipFastFileSystem m_zipFileSystem;

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
                if ((ecuInfo.projects.contains(type) || type.isEmpty()) && m_ecuAddressing.containsKey(ecuInfo.addressId)) {
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

    public String[] getProjects(){
        return m_projectSet.toArray(new String[m_projectSet.size()]);
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
        m_zipFileSystem = new ZipFastFileSystem(m_ecuFilePath, appDir);

        /*
         * If index is already made, use it
         * Also check files exists and timestamps to force [re]scan
         */
        if (indexFile.exists() && (indexTimeStamp > ecuTimeStamp) && m_zipFileSystem.importZipEntries()){
            bytes = m_zipFileSystem.getZipFile("db.json");
        } else {
            /*
             * Else create it
             */
            m_zipFileSystem.getZipEntries();
            m_zipFileSystem.exportZipEntries();
            bytes = m_zipFileSystem.getZipFile("db.json");
        }

        JSONObject jsonRootObject;
        try {
            jsonRootObject = new JSONObject(bytes);
        } catch (Exception e) {
            throw new DatabaseException("JSON conversion issue");
        }

        m_projectSet = new HashSet<>();
        Set<Integer> addressSet = new HashSet<>();
        Iterator<String> keys = jsonRootObject.keys();
        for (; keys.hasNext(); ) {
            String href = keys.next();
            try {
                JSONObject ecuJsonObject = jsonRootObject.getJSONObject(href);
                JSONArray projectJsonObject = ecuJsonObject.getJSONArray("projects");
                Set<String> projectsSet = new HashSet<>();
                for (int i = 0; i < projectJsonObject.length(); ++i) {
                    String project = projectJsonObject.getString(i);
                    String upperCaseProject = project.toUpperCase();
                    projectsSet.add(upperCaseProject);
                    m_projectSet.add(upperCaseProject);
                }
                int addrId = Integer.parseInt(ecuJsonObject.getString("address"), 16);
                addressSet.add(addrId);
                EcuInfo info = new EcuInfo();
                info.ecuName = ecuJsonObject.getString("ecuname");
                info.href = href;
                info.projects = projectsSet;
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

    public String getZipFile(String filePath){
        return m_zipFileSystem.getZipFile(filePath);
    }
}
