package org.quark.dr.ecu;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Optimized ZIP file system for fast extraction of individual files.
 * <p>
 * Creates an index of ZIP entries for O(1) file access without scanning
 * the entire archive. This is particularly useful for large ECU database files.
 * <p>
 * Note: This class handles text files only. Binary files may not decode correctly.
 */
public class ZipFileSystem {
    /** Path to the ZIP archive file. */
    private final String m_zipFilePath;
    /** Path to the index file for cached entry positions. */
    private final String m_indexFile;
    /** Map of file names to their ZIP entry metadata. */
    private HashMap<String, CustomZipEntry> m_directoryEntries;

    /**
     * Creates a new ZipFileSystem instance.
     *
     * @param zipFilePath Path to the ZIP archive
     * @param applicationDirectory Directory for storing the index file
     */
    public ZipFileSystem(String zipFilePath, String applicationDirectory) {
        m_directoryEntries = new HashMap<>();
        m_zipFilePath = zipFilePath;
        m_indexFile = applicationDirectory + "/ecu.idx";
    }

    /**
     * Loads the index from a previously saved file.
     *
     * @return true if the index was loaded successfully, false otherwise
     */
    public boolean importZipEntries() {
        try {
            JSONArray mainJson = new JSONArray(readFile(m_indexFile));
            for (int j = 0; j < mainJson.length(); ++j) {
                JSONObject zipEntryJson = mainJson.getJSONObject(j);
                CustomZipEntry ze = new CustomZipEntry();
                ze.pos = zipEntryJson.getLong("pos");
                ze.compressedSize = zipEntryJson.getLong("compsize");
                ze.uncompressedSize = zipEntryJson.getLong("realsize");
                m_directoryEntries.put(zipEntryJson.getString("name"), ze);
            }
            return true;
        } catch (Exception e) {
            Log.e("ZipFileSystem", "Error importing zip entries", e);
        }

        return false;
    }

    /**
     * Saves the current index to a file for faster future loads.
     */
    public void exportZipEntries() {
        JSONArray mainJson = new JSONArray();
        for (Map.Entry<String, CustomZipEntry> pair : m_directoryEntries.entrySet()) {
            JSONObject jsonEntry = new JSONObject();
            try {
                jsonEntry.put("pos", pair.getValue().pos);
                jsonEntry.put("realsize", pair.getValue().uncompressedSize);
                jsonEntry.put("compsize", pair.getValue().compressedSize);
                jsonEntry.put("name", pair.getKey());
                mainJson.put(jsonEntry);
            } catch (Exception e) {
                Log.e("ZipFileSystem", "Error exporting zip entries", e);
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

    /**
     * Scans the ZIP file and builds an index of all entries.
     * <p>
     * This is the slowest operation and is only called when the index file
     * doesn't exist or is older than the ZIP file.
     */
    public void getZipEntries() {
        m_directoryEntries = new HashMap<>();
        try {
            FileInputStream zip_is = new FileInputStream(m_zipFilePath);
            ZipInputStream zis = new ZipInputStream(zip_is);
            ZipEntry ze;
            long pos = 0;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory())
                    continue;
                String filename = ze.getName();
                if (filename.contains("..") || filename.startsWith("/")) {
                    Log.w("ZipFileSystem", "Path traversal attempt or invalid name: " + filename);
                    continue;
                }
                long offset = 30 + filename.length() + (ze.getExtra() != null ? ze.getExtra().length : 0);
                pos += offset;
                CustomZipEntry cze = new CustomZipEntry();
                cze.pos = pos;
                cze.compressedSize = ze.getCompressedSize();
                cze.uncompressedSize = ze.getSize();
                m_directoryEntries.put(filename, cze);
                pos += cze.compressedSize;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean fileExists(String filename) {
        return m_directoryEntries.containsKey(filename);
    }

    public String getZipFile(String filename) {
        byte[] array = getZipFileAsBytes(filename);
        if (array == null) return "";
        return new String(array, StandardCharsets.UTF_8);
    }

    public byte[] getZipFileAsBytes(String filename) {
        CustomZipEntry entry = m_directoryEntries.get(filename);
        if (entry == null) return null;
        
        try (FileInputStream zip_is = new FileInputStream(m_zipFilePath)) {
            long pos = entry.pos;
            long compressedSize = entry.compressedSize;
            long realSize = entry.uncompressedSize;
            byte[] array = new byte[(int) compressedSize];
            if (zip_is.getChannel().position(pos).read(java.nio.ByteBuffer.wrap(array)) != (int) compressedSize) {
                Log.w("ZipFileSystem", "Could not read all bytes from zip");
            }
            Inflater inflater = new Inflater(true);
            inflater.setInput(array, 0, (int) compressedSize);
            byte[] result = new byte[(int) realSize];
            inflater.inflate(result);
            inflater.end();
            return result;
        } catch (IOException | DataFormatException e) {
            Log.e("ZipFileSystem", "Error reading zip file", e);
            return null;
        }
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
    }
}
