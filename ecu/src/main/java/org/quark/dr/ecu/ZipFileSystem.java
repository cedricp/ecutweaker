package org.quark.dr.ecu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/*
 *
 * A simple class that fast extracts a file stored in a big (dense) zip file
 * I build an index that contains the position and zip information
 * in a hash map.
 * I can now unzip a file stored in a big zip file at the speed of light !
 * /!\ This class handles text files only
 *
 * Falls back to java.util.zip.ZipFile when the fast path fails (STORE entries,
 * non-standard local headers, nested paths, etc.).
 *
 */


public class ZipFileSystem {
    private final String m_zipFilePath;
    private final String m_indexFile;
    private HashMap<String, CustomZipEntry> m_directoryEntries;

    public ZipFileSystem(String zipFilePath, String applicationDirectory) {
        m_directoryEntries = new HashMap<>();
        m_zipFilePath = zipFilePath;
        m_indexFile = applicationDirectory + "/ecu.idx";
    }

    public boolean importZipEntries() {
        try {
            JSONArray mainJson = new JSONArray(readFile(m_indexFile));
            for (int i = 0; i < mainJson.length(); ++i) {
                JSONObject zipEntryJson = mainJson.getJSONObject(i);
                CustomZipEntry ze = new CustomZipEntry();
                ze.pos = zipEntryJson.getLong("pos");
                ze.compressedSize = zipEntryJson.getLong("compsize");
                ze.uncompressedSize = zipEntryJson.getLong("realsize");
                ze.method = zipEntryJson.optInt("method", ZipEntry.DEFLATED);
                String name = normalizeEntryName(zipEntryJson.getString("name"));
                m_directoryEntries.put(name, ze);
                // Also keep original basename lookup
                String base = basename(name);
                if (!base.equals(name) && !m_directoryEntries.containsKey(base)) {
                    m_directoryEntries.put(base, ze);
                }
            }
            return !m_directoryEntries.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public void exportZipEntries() {
        Iterator zeit = m_directoryEntries.entrySet().iterator();
        JSONArray mainJson = new JSONArray();
        // Export unique entries only (skip basename aliases that share the same object)
        HashMap<CustomZipEntry, String> unique = new HashMap<>();
        while (zeit.hasNext()) {
            Map.Entry pair = (Map.Entry) zeit.next();
            CustomZipEntry cze = (CustomZipEntry) pair.getValue();
            String name = (String) pair.getKey();
            if (!unique.containsKey(cze) || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
                // Prefer full path names over basenames
                if (!unique.containsKey(cze) || basename((String) unique.get(cze)).equals(unique.get(cze))) {
                    unique.put(cze, name);
                }
            }
        }
        for (Map.Entry<CustomZipEntry, String> pair : unique.entrySet()) {
            JSONObject jsonEntry = new JSONObject();
            try {
                jsonEntry.put("pos", pair.getKey().pos);
                jsonEntry.put("realsize", pair.getKey().uncompressedSize);
                jsonEntry.put("compsize", pair.getKey().compressedSize);
                jsonEntry.put("method", pair.getKey().method);
                jsonEntry.put("name", pair.getValue());
                mainJson.put(jsonEntry);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        try {
            FileWriter fileWriter = new FileWriter(m_indexFile);
            fileWriter.write(mainJson.toString(0));
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Map zip entries to fast load them
     * This is the slowest part
     */
    public void getZipEntries() {
        m_directoryEntries = new HashMap<>();
        try {
            FileInputStream zip_is = new FileInputStream(m_zipFilePath);
            ZipInputStream zis = new ZipInputStream(zip_is);
            ZipEntry ze;
            long pos = 0;
            //TODO: https://developer.android.com/about/versions/14/behavior-changes-14?hl=fr#zip-path-traversal
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory())
                    continue;
                String filename = normalizeEntryName(ze.getName());
                long offset = 30 + ze.getName().length() + (ze.getExtra() != null ? ze.getExtra().length : 0);
                pos += offset;
                CustomZipEntry cze = new CustomZipEntry();
                cze.pos = pos;
                cze.compressedSize = ze.getCompressedSize();
                cze.uncompressedSize = ze.getSize();
                cze.method = ze.getMethod();
                m_directoryEntries.put(filename, cze);
                String base = basename(filename);
                if (!base.equals(filename) && !m_directoryEntries.containsKey(base)) {
                    m_directoryEntries.put(base, cze);
                }
                pos += cze.compressedSize;
            }
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // If sequential scan failed or produced no entries, rebuild via ZipFile
        if (m_directoryEntries.isEmpty()) {
            rebuildEntriesViaZipFile();
        }
    }

    private void rebuildEntriesViaZipFile() {
        m_directoryEntries = new HashMap<>();
        try (ZipFile zipFile = new ZipFile(m_zipFilePath)) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.isDirectory()) {
                    continue;
                }
                String filename = normalizeEntryName(ze.getName());
                CustomZipEntry cze = new CustomZipEntry();
                cze.pos = -1; // force ZipFile fallback for reads
                cze.compressedSize = ze.getCompressedSize();
                cze.uncompressedSize = ze.getSize();
                cze.method = ze.getMethod();
                cze.zipEntryName = ze.getName();
                m_directoryEntries.put(filename, cze);
                String base = basename(filename);
                if (!base.equals(filename) && !m_directoryEntries.containsKey(base)) {
                    m_directoryEntries.put(base, cze);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean fileExists(String filename) {
        return resolveEntry(filename) != null;
    }

    public String getZipFile(String filename) {
        byte[] array = getZipFileAsBytes(filename);
        if (array == null || array.length == 0) {
            return "";
        }
        try {
            return new String(array, 0, array.length, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public byte[] getZipFileAsBytes(String filename) {
        CustomZipEntry entry = resolveEntry(filename);
        if (entry == null) {
            return readViaZipFile(filename);
        }

        // Prefer reliable ZipFile path when offset is unknown or STORE
        if (entry.pos < 0) {
            byte[] viaZip = readViaZipFile(entry.zipEntryName != null ? entry.zipEntryName : filename);
            if (viaZip != null) {
                return viaZip;
            }
        }

        if (entry.pos >= 0 && entry.compressedSize >= 0) {
            try {
                byte[] array = new byte[(int) entry.compressedSize];
                FileInputStream zip_is = new FileInputStream(m_zipFilePath);
                zip_is.getChannel().position(entry.pos);
                int read = zip_is.read(array, 0, (int) entry.compressedSize);
                zip_is.close();
                if (read == entry.compressedSize) {
                    if (entry.method == ZipEntry.STORED || entry.compressedSize == entry.uncompressedSize) {
                        if (entry.method == ZipEntry.STORED) {
                            return array;
                        }
                    }
                    if (entry.method == ZipEntry.DEFLATED || entry.method == -1) {
                        try {
                            Inflater inflater = new Inflater(true);
                            inflater.setInput(array, 0, (int) entry.compressedSize);
                            byte[] result = new byte[(int) entry.uncompressedSize];
                            inflater.inflate(result);
                            inflater.end();
                            return result;
                        } catch (DataFormatException e) {
                            // Fall through to ZipFile
                        }
                    } else if (entry.method == ZipEntry.STORED) {
                        return array;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return readViaZipFile(entry.zipEntryName != null ? entry.zipEntryName : filename);
    }

    private byte[] readViaZipFile(String filename) {
        try (ZipFile zipFile = new ZipFile(m_zipFilePath)) {
            ZipEntry ze = findZipEntry(zipFile, filename);
            if (ze == null) {
                return null;
            }
            try (InputStream is = zipFile.getInputStream(ze);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) > 0) {
                    bos.write(buffer, 0, n);
                }
                return bos.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ZipEntry findZipEntry(ZipFile zipFile, String filename) {
        if (filename == null) {
            return null;
        }
        ZipEntry direct = zipFile.getEntry(filename);
        if (direct != null) {
            return direct;
        }
        String wanted = normalizeEntryName(filename).toLowerCase(Locale.ROOT);
        String wantedBase = basename(wanted);
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        ZipEntry basenameMatch = null;
        while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement();
            if (ze.isDirectory()) {
                continue;
            }
            String name = normalizeEntryName(ze.getName()).toLowerCase(Locale.ROOT);
            if (name.equals(wanted)) {
                return ze;
            }
            if (basename(name).equals(wantedBase)) {
                basenameMatch = ze;
            }
        }
        return basenameMatch;
    }

    private CustomZipEntry resolveEntry(String filename) {
        if (filename == null || m_directoryEntries == null) {
            return null;
        }
        String normalized = normalizeEntryName(filename);
        CustomZipEntry entry = m_directoryEntries.get(normalized);
        if (entry != null) {
            return entry;
        }
        entry = m_directoryEntries.get(basename(normalized));
        if (entry != null) {
            return entry;
        }
        String wanted = normalized.toLowerCase(Locale.ROOT);
        String wantedBase = basename(wanted);
        for (Map.Entry<String, CustomZipEntry> e : m_directoryEntries.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (key.equals(wanted) || basename(key).equals(wantedBase)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String normalizeEntryName(String name) {
        if (name == null) {
            return "";
        }
        String n = name.replace('\\', '/');
        while (n.startsWith("./")) {
            n = n.substring(2);
        }
        while (n.startsWith("/")) {
            n = n.substring(1);
        }
        return n;
    }

    private static String basename(String path) {
        if (path == null) {
            return "";
        }
        int idx = path.lastIndexOf('/');
        if (idx >= 0 && idx < path.length() - 1) {
            return path.substring(idx + 1);
        }
        return path;
    }

    private String readFile(String file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            String ls = System.getProperty("line.separator");
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        }
    }

    static class CustomZipEntry {
        public long compressedSize, pos, uncompressedSize;
        public int method = ZipEntry.DEFLATED;
        public String zipEntryName;
    }
}
