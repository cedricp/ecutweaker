package org.quark.dr.ecu;

import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;


public class Layout {
    public class Color {
        int r,g,b;

        Color(){
            r = g = b = 0;
        }

        Color(JSONObject jobj){
            try {
                r = g = b = 10;
                if (jobj.has("color")){
                    String scol = jobj.getString("color");
                    scol = scol.substring(4, scol.length() -1);
                    String[] cols = scol.split(",");
                    if (cols.length == 3) {
                        r = Integer.parseInt(cols[0]);
                        g = Integer.parseInt(cols[1]);
                        b = Integer.parseInt(cols[2]);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public int get(){
            return 255 << 24 | r << 16 | g << 8 | b;
        }
    }

    public class Font {
        public String name;
        public int size;
        public Color color;
        public Font(JSONObject fobj){
            color = new Color();
            size = 10;
            try {
                if (fobj.has("name")) name = fobj.getString("name");
                if (fobj.has("size")) size = fobj.getInt("size");
                if (fobj.has("color")) color = new Color(fobj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class Rect {
        public int x, y, w, h;
        Rect(JSONObject jrect){
            try {
                if (jrect.has("width")) w = jrect.getInt("width");
                if (jrect.has("height")) h = jrect.getInt("height");
                if (jrect.has("top")) y = jrect.getInt("top");
                if (jrect.has("left")) x = jrect.getInt("left");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class InputData {
        public String text;
        public String request;
        public Rect rect;
        public int width;
        public Font font;
        public Color color;
    }

    public class DisplayData {
        public String text;
        public String request;
        public Rect rect;
        public int width;
        public Font font;
        public Color color;
    }

    public class LabelData {
        public String text;
        public Rect rect;
        public Font font;
        public int alignment;
        public Color color;
    }

    public class ButtonData {
        public String text, uniqueName;
        public Rect rect;
        public Font font;
        public ArrayList<Pair<Integer, String>> sendData;
    }

    public class ScreenData {
        HashMap<String, InputData> m_inputs;
        HashMap<String, LabelData> m_labels;
        HashMap<String, DisplayData> m_displays;
        HashMap<String, ButtonData> m_buttons;
        public String m_screen_name;
        public int m_width, m_height;
        public Color m_color;
        public ArrayList<Pair<Integer, String>> preSendData;

        ScreenData(String name, JSONObject jobj){
            m_inputs = new HashMap<>();
            m_labels = new HashMap<>();
            m_displays = new HashMap<>();
            m_buttons = new HashMap<>();

            m_screen_name = name;

            try {
                if (jobj.has("presend")) {
                    JSONArray sendData = jobj.getJSONArray("presend");
                    preSendData = new ArrayList<>();
                    for (int j = 0; j < sendData.length(); ++j) {
                        JSONObject jdata = sendData.getJSONObject(j);
                        Pair<Integer, String> pair = new Pair<>(Integer.parseInt(jdata.getString("Delay")), jdata.getString("RequestName"));
                        preSendData.add(pair);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (jobj.has("width")) m_width = jobj.getInt("width");
                if (jobj.has("height")) m_height = jobj.getInt("height");
                if (jobj.has("color")) m_color = new Color(jobj);

                JSONArray jinputs = jobj.getJSONArray("inputs");
                for (int i = 0; i < jinputs.length(); ++i) {
                    JSONObject inputobj = jinputs.getJSONObject(i);
                    InputData data = new InputData();
                    if (inputobj.has("text")) data.text = inputobj.getString("text");
                    if (inputobj.has("request")) data.request = inputobj.getString("request");
                    if (inputobj.has("width")) data.width = inputobj.getInt("width");
                    if (inputobj.has("rect")) data.rect = new Rect(inputobj.getJSONObject("rect"));
                    if (inputobj.has("font")) data.font = new Font(inputobj.getJSONObject("font"));
                    if (inputobj.has("color")) data.color = new Color(inputobj);
                    m_inputs.put(data.text, data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                JSONArray jinputs = jobj.getJSONArray("displays");
                for (int i = 0; i < jinputs.length(); ++i) {
                    JSONObject displayobj = jinputs.getJSONObject(i);
                    DisplayData data = new DisplayData();
                    if (displayobj.has("text")) data.text = displayobj.getString("text");
                    if (displayobj.has("request")) data.request = displayobj.getString("request");
                    if (displayobj.has("width")) data.width = displayobj.getInt("width");
                    if (displayobj.has("rect")) data.rect = new Rect(displayobj.getJSONObject("rect"));
                    if (displayobj.has("font")) data.font = new Font(displayobj.getJSONObject("font"));
                    if (displayobj.has("color")) data.color = new Color(displayobj);
                    m_displays.put(data.text, data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                JSONArray linputs = jobj.getJSONArray("labels");
                for (int i = 0; i < linputs.length(); ++i) {
                    JSONObject inputobj = linputs.getJSONObject(i);
                    LabelData data = new LabelData();
                    if (inputobj.has("text")) data.text = inputobj.getString("text");
                    if (inputobj.has("bbox")) data.rect = new Rect(inputobj.getJSONObject("bbox"));
                    if (inputobj.has("font")) data.font = new Font(inputobj.getJSONObject("font"));
                    if (inputobj.has("alignment")) data.alignment = inputobj.getInt("alignment");
                    if (inputobj.has("color")) data.color = new Color(inputobj);
                    m_labels.put(data.text, data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                JSONArray binputs = jobj.getJSONArray("buttons");
                for (int i = 0; i < binputs.length(); ++i) {
                    JSONObject inputobj = binputs.getJSONObject(i);
                    ButtonData data = new ButtonData();
                    if (inputobj.has("text")) data.text = inputobj.getString("text");
                    if (inputobj.has("rect")) data.rect = new Rect(inputobj.getJSONObject("rect"));
                    if (inputobj.has("font")) data.font = new Font(inputobj.getJSONObject("font"));
                    if (inputobj.has("uniquename")) data.uniqueName = inputobj.getString("uniquename");
                    if (inputobj.has("send")) {
                        JSONArray sendData = inputobj.getJSONArray("send");
                        data.sendData = new ArrayList<>();
                        for (int j = 0; j < sendData.length(); ++j) {
                            JSONObject jdata = sendData.getJSONObject(j);
                            Pair<Integer, String> pair = new Pair<>(Integer.parseInt(jdata.getString("Delay")), jdata.getString("RequestName"));
                            data.sendData.add(pair);
                        }
                    }
                    m_buttons.put(data.uniqueName, data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public Set<String> getInputs(){
            return m_inputs.keySet();
        }

        public Set<String> getLabels(){
            return m_labels.keySet();
        }

        public Set<String> getDisplays(){
            return m_displays.keySet();
        }

        public Set<String> getButtons(){
            return m_buttons.keySet();
        }

        public InputData getInputData(String inputname){
            if (m_inputs.containsKey(inputname)){
                return m_inputs.get(inputname);
            } else {
                return null;
            }
        }

        public LabelData getLabelData(String labelname){
            if (m_labels.containsKey(labelname)){
                return m_labels.get(labelname);
            } else {
                return null;
            }
        }

        public DisplayData getDisplayData(String displayname){
            if (m_displays.containsKey(displayname)){
                return m_displays.get(displayname);
            } else {
                return null;
            }
        }

        public ButtonData getButtonData(String buttonname){
            if (m_buttons.containsKey(buttonname)){
                return m_buttons.get(buttonname);
            } else {
                return null;
            }
        }

        public ArrayList<Pair<Integer, String>> getPreSendData(){
            return preSendData;
        }
    }

    public HashMap<String, ScreenData> m_screens;
    HashMap<String, ArrayList<String>> m_categories;
    public Layout(InputStream is){
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

    void init(JSONObject jobj){
        m_screens = new HashMap<>();
        m_categories = new HashMap<>();
        try {
            // Gather all screens
            if (jobj.has("screens")) {
                JSONObject scr_object = jobj.getJSONObject("screens");
                Iterator<String> keys = scr_object.keys();
                for (; keys.hasNext(); ) {
                    String key = keys.next();
                    JSONObject sobj = scr_object.getJSONObject(key);
                    ScreenData sdata = new ScreenData(key, sobj);
                    m_screens.put(key, sdata);
                    System.out.println("Adding screen " + key);
                }
            }
        }  catch (Exception e) {
            e.printStackTrace();
        }

        try{

            JSONObject categories = jobj.getJSONObject("categories");
            Iterator<String> iterator = categories.keys();
            while(iterator.hasNext()) {
                String currentKey = iterator.next();
                ArrayList<String> screennames = new ArrayList<>();
                JSONArray jscreenarry = categories.getJSONArray(currentKey);
                for (int i = 0; i < jscreenarry.length(); ++i) {
                    screennames.add(jscreenarry.getString(i));
                }
                m_categories.put(currentKey, screennames);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Set<String> getScreens(){
        return m_screens.keySet();
    }

    public Set<String> getCategories(){
        return m_categories.keySet();
    }

    public ArrayList<String> getScreenNames(String category){
        return m_categories.get(category);
    }

    public ScreenData getScreen(String screenName){
        if(m_screens.containsKey(screenName)) {
            return m_screens.get(screenName);
        } else {
            return null;
        }
    }
}
