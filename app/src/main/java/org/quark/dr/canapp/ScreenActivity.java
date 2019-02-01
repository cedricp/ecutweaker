package org.quark.dr.canapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.quark.dr.ecu.Ecu;
import org.quark.dr.ecu.Layout;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.quark.dr.canapp.ElmThread.STATE_CONNECTED;
import static org.quark.dr.canapp.ElmThread.STATE_CONNECTING;
import static org.quark.dr.canapp.ElmThread.STATE_DISCONNECTED;
import static org.quark.dr.canapp.ElmThread.STATE_LISTEN;
import static org.quark.dr.canapp.ElmThread.STATE_NONE;

public class ScreenActivity extends AppCompatActivity {
    private static final String TAG = "org.quark.dr.canapp";
    private TextToSpeech mTts;
    private ScrollView m_scrollView;
    private RelativeLayout m_layoutView;
    private Ecu m_ecu;
    private Layout m_layout;
    private ImageButton m_searchButton;
    private ImageView m_iconStatus;
    private TextView m_logView;

    private HashMap<String, EditText> m_editTextViews;
    private HashMap<String, EditText> m_displayViews;
    private HashMap<String, Spinner> m_spinnerViews;
    private Set<String> m_displaysRequestSet;

    private BluetoothAdapter mBluetoothAdapter = null;
    private ElmThread mChatService = null;
    private Handler mHandler = null;

    // Intent request codes
    private static final int    REQUEST_CONNECT_DEVICE = 1;
    private static final int    REQUEST_ENABLE_BT      = 3;

    public static final int     MESSAGE_STATE_CHANGE    = 1;
    public static final int     MESSAGE_READ            = 2;
    public static final int     MESSAGE_WRITE           = 3;
    public static final int     MESSAGE_DEVICE_NAME     = 4;
    public static final int     MESSAGE_TOAST           = 5;
    public static final String  DEVICE_NAME = "device_name";
    public static final String  TOAST       = "toast";
    private String              mConnectedDeviceName = null;

    public float convertToPixel(float val){
        return val / 6;
    }

    public float convertFontToPixel(int val){
        return val * 1.5f;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_searchButton = findViewById(R.id.buttonSearch);
        m_iconStatus = findViewById(R.id.iconBt);

        m_searchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connectElm();
            }
        });

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }

        mHandler = new messageHandler(this);

        m_scrollView = this.findViewById(R.id.scrollView);
        m_layoutView = this.findViewById(R.id.mainLayout);
        m_logView = this.findViewById(R.id.logView);
        m_logView.setMovementMethod(new ScrollingMovementMethod());

        InputStream ecu_stream = getClass().getClassLoader().getResourceAsStream("test.json");

        m_ecu = new Ecu(ecu_stream);
        InputStream layout_stream = getClass().getClassLoader().getResourceAsStream("test.json.layout");
        m_layout = new Layout(layout_stream);
        drawScreen( "Misc Values and Timings");
    }

    void drawScreen(String screenName)
    {
        m_displayViews = new HashMap<>();
        m_editTextViews = new HashMap<>();
        m_spinnerViews = new HashMap<>();
        m_displaysRequestSet = new HashSet<>();

        Layout.ScreenData screenData = m_layout.getScreen(screenName);

        m_layoutView.getLayoutParams().width = (int) convertToPixel(screenData.m_width);
        m_layoutView.getLayoutParams().height = (int) convertToPixel(screenData.m_height);
        m_layoutView.setBackgroundColor(screenData.m_color.get());

        Set<String> labels = screenData.getLabels();
        for (String label : labels) {
            Layout.LabelData labeldata = screenData.getLabelData(label);
            TextView textView = new TextView(this);
            textView.setX(convertToPixel(labeldata.rect.x));
            textView.setY(convertToPixel(labeldata.rect.y));
            textView.setWidth((int) convertToPixel(labeldata.rect.w));
            textView.setHeight((int) convertToPixel(labeldata.rect.h));
            textView.setText(labeldata.text);
            textView.setBackgroundColor(labeldata.color.get());
            textView.setTextColor(labeldata.font.color.get());
            textView.setTextSize(convertFontToPixel(labeldata.font.size));
            if (labeldata.alignment == 2){
                textView.setGravity(Gravity.CENTER_HORIZONTAL);
            } else if (labeldata.alignment == 1){
                textView.setGravity(Gravity.RIGHT);
            }
            m_layoutView.addView(textView);
        }

        Set<String> displays = screenData.getDisplays();
        for (String display : displays){
            Layout.DisplayData displaydata = screenData.getDisplayData(display);
            if (displaydata.width > 0) {
                TextView textView = new TextView(this);
                textView.setX(convertToPixel(displaydata.rect.x));
                textView.setY(convertToPixel(displaydata.rect.y));
                textView.setWidth((int) convertToPixel(displaydata.width));
                textView.setHeight((int) convertToPixel(displaydata.rect.h));
                textView.setText(displaydata.text);
                textView.setTextColor(displaydata.font.color.get());
                textView.setBackgroundColor(displaydata.color.get());
                textView.setTextSize(convertFontToPixel(displaydata.font.size));
                m_layoutView.addView(textView);
            }

            EditText textEdit = new EditText(this);
            textEdit.setX(convertToPixel(displaydata.rect.x + displaydata.width));
            textEdit.setY(convertToPixel(displaydata.rect.y));
            textEdit.setWidth((int) convertToPixel(displaydata.rect.w - displaydata.width));
            textEdit.setHeight((int) convertToPixel(displaydata.rect.h));
            textEdit.setTextSize(convertFontToPixel(displaydata.font.size));
            textEdit.setEnabled(false);
            textEdit.setText("NO DATA");
            textEdit.setPadding(0,0,0,4);
            textEdit.setBackgroundColor(displaydata.color.get());
            textEdit.setTextColor(displaydata.font.color.get());
            m_displayViews.put(displaydata.text, textEdit);
            m_layoutView.addView(textEdit);
            m_displaysRequestSet.add(displaydata.request);
        }

        Set<String> inputs = screenData.getInputs();
        for (String input : inputs){
            Layout.InputData inputdata = screenData.getInputData(input);
            if (inputdata.width > 0) {
                TextView textView = new TextView(this);
                textView.setX(convertToPixel(inputdata.rect.x));
                textView.setY(convertToPixel(inputdata.rect.y));
                textView.setWidth((int) convertToPixel(inputdata.width));
                textView.setHeight((int) convertToPixel(inputdata.rect.h));
                textView.setText(inputdata.text);
                textView.setTextColor(inputdata.font.color.get());
                textView.setBackgroundColor(inputdata.color.get());
                m_layoutView.addView(textView);
            }

            if (m_ecu.getData(inputdata.text).items.size() == 0) {
                EditText textEdit = new EditText(this);
                textEdit.setX(convertToPixel(inputdata.rect.x + inputdata.width));
                textEdit.setY(convertToPixel(inputdata.rect.y));
                textEdit.setWidth((int) convertToPixel(inputdata.rect.w - inputdata.width));
                textEdit.setHeight((int) convertToPixel(inputdata.rect.h));
                textEdit.setTextSize(convertFontToPixel(inputdata.font.size));
                textEdit.setPadding(0, 0, 0, 4);
                textEdit.setTextColor(inputdata.font.color.get());
                textEdit.setBackgroundColor(inputdata.color.get());
                textEdit.setSingleLine();
                m_editTextViews.put(inputdata.text, textEdit);
                m_layoutView.addView(textEdit);
            } else {
                String items[] = new String[m_ecu.getData(inputdata.text).items.size()];
                int i = 0;
                for (String item : m_ecu.getData(inputdata.text).items.keySet()){
                    items[i++] = item;
                }
                CustomAdapter dataAdapter = new CustomAdapter(this,
                        android.R.layout.simple_spinner_item, items);

                Spinner spinner = new Spinner(this);
                spinner.setX(convertToPixel(inputdata.rect.x + inputdata.width));
                spinner.setY(convertToPixel(inputdata.rect.y));
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        ((int) convertToPixel(inputdata.rect.w - inputdata.width)),
                        (int) convertToPixel(inputdata.rect.h));
                spinner.setLayoutParams(params);
                spinner.setBackgroundColor(inputdata.color.get());
                dataAdapter.setSpinnerTextSize((int)convertFontToPixel(inputdata.font.size));
                dataAdapter.setSpinnerTextColor(inputdata.font.color.get());
                spinner.setAdapter(dataAdapter);
                m_spinnerViews.put(inputdata.text, spinner);
                m_layoutView.addView(spinner);
            }
        }

        Set<String> buttons = screenData.getButtons();
        for (String button : buttons) {
            Layout.ButtonData buttondata = screenData.getButtonData(button);
            Button buttonView = new Button(this);
            buttonView.setX(convertToPixel(buttondata.rect.x));
            buttonView.setY(convertToPixel(buttondata.rect.y) - 15);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    (int) convertToPixel(buttondata.rect.w),
                    (int) convertToPixel(buttondata.rect.h) + 15);
            buttonView.setLayoutParams(params);
            buttonView.setPadding(0, 0, 0, 0);
            buttonView.setText(buttondata.text);
            buttonView.setTextSize(convertFontToPixel(buttondata.font.size));
            buttonView.setOnClickListener(buttonClickListener);
            m_layoutView.addView(buttonView);
        }

        m_scrollView.requestLayout();
    }

    void updateDisplays(){
        if (m_displaysRequestSet == null)
            return;

        sendCmd("10C0");

        for(String requestname : m_displaysRequestSet){
            Ecu.EcuRequest request = m_ecu.getRequest(requestname);
            Log.i("CanApp", "Managing request : " + requestname);
            if (request == null){
                Log.i("CanApp", "Cannot find request " + requestname);
            }

            Log.i("CanApp", "Send bytes " + request.sentbytes);

            sendCmd(request.sentbytes);
        }
    }

    private void updateScreen(String req, String response){
        for(String requestname : m_displaysRequestSet){
            Ecu.EcuRequest request = m_ecu.getRequest(requestname);
            Log.i("CanApp", "Managing request : " + requestname);
            if (request == null){
                Log.i("CanApp", "Cannot find request " + requestname);
            }

            if (request.sentbytes.equals(req)) {
                byte[] bytes = Ecu.hexStringToByteArray(response);
                HashMap<String, Pair<String, String>> mapValues = m_ecu.getRequestValuesWithUnit(bytes, requestname);

                for (String key : mapValues.keySet()) {
                    if (m_displayViews.containsKey(key)) {
                        m_displayViews.get(key).setText(mapValues.get(key).first + " " + mapValues.get(key).second);
                    }
                    if (m_editTextViews.containsKey(key)) {
                        m_editTextViews.get(key).setText(mapValues.get(key).first);
                    }
                    if (m_spinnerViews.containsKey(key)) {
                        Spinner spinner = m_spinnerViews.get(key);
                        spinner.setSelection(((CustomAdapter) spinner.getAdapter()).getPosition(mapValues.get(key).first));
                    }
                }
            }
        }
    }

    private View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button clickedButton = (Button)v;
            System.out.println("Clicked on " + clickedButton.getText());
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Log.d(TAG, "onActivityResult " + address);
                    connectDevice(address);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    //setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mBluetoothAdapter == null)
            return;
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {

            if (mChatService == null) setupChat();
        }
    }

    @Override
    public void onDestroy()
    {
        Log.e(TAG, "+ ON DESTROY +");
        super.onDestroy();
        if (mChatService != null)
            mChatService.stop();
    }


    private void setupChat() {
        Log.d(TAG, "setupChat()");
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new ElmThread(mHandler);
    }

    private void connectDevice(String address) {
        if (mBluetoothAdapter == null)
            return;

        // address is the device MAC address
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device);
    }

    public void connectElm(){
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            return;
        }

        if (mBluetoothAdapter.isEnabled()) {
            try {
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            } catch (android.content.ActivityNotFoundException e) {
                Log.e(TAG, "+++ ActivityNotFoundException +++");
            }
        }
    }


    private void setStatus(CharSequence status) {
        m_logView.append(status + "\n");
    }

    private void setConnected(boolean c){
        if (c) {
            m_iconStatus.setImageResource(R.drawable.ic_bt_connected);
        } else {
            m_iconStatus.setImageResource(R.drawable.ic_bt_disconnected);
        }
    }

    private boolean isChatConnected(){
        return (mChatService != null && mChatService.getState() == STATE_CONNECTED);
    }

    private void initELM() {
        sendCmd("AT Z");        // reset ELM
        sendCmd("AT E1");
        sendCmd("AT S0");
        sendCmd("AT H0");
        sendCmd("AT L0");
        sendCmd("AT AL");
        sendCmd("AT CAF0");

        initCan();
    }

    private void initCan(){
        String txa = m_ecu.ecu_send_id;
        String rxa = m_ecu.ecu_recv_id;

        sendCmd("AT SP 6");
        sendCmd("AT SH " + txa);
        sendCmd("AT CRA " + rxa.toUpperCase());
        sendCmd("AT FC SH " + txa.toUpperCase());
        sendCmd("AT FC SD 30 00 00");
        sendCmd("AT FC SM 1");

        updateDisplays();
    }

    private void sendCmd(String cmd) {
        // Check that we're actually connected before trying anything
        if (mBluetoothAdapter == null) {
            m_logView.append("Trying to send command without BT connection " + cmd + "\n");
            return;
        }
        if (!isChatConnected()) {
            m_logView.append("Trying to send command without chat session " + cmd + "\n");
            return;
        }

        // Send command
        mChatService.write(cmd);

    }

    void handleElmResult(String result){
        String[] results = result.split(";");
        if (results.length < 2){
            m_logView.append("No ELM Response (" + result + ")\n");
            return;
        }
        m_logView.append("ELM Response : " + results[1] + " to " + results[0] +"\n");

        if (results[1].isEmpty() || results[0].substring(0,2).toUpperCase().equals("AT")){
            return;
        }

        updateScreen(results[0], results[1]);
    }

    private static class messageHandler extends Handler {
        private ScreenActivity activity;
        messageHandler(ScreenActivity ac){
            activity = ac;
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case STATE_CONNECTED:
                            activity.setStatus(activity.getString(R.string.title_connected_to, activity.mConnectedDeviceName));
                            activity.initELM();
                            activity.setConnected(true);
                            break;
                        case STATE_CONNECTING:
                            activity.setStatus(activity.getString(R.string.title_connecting));
                            break;
                        case STATE_LISTEN:
                        case STATE_NONE:
                            activity.setStatus(activity.getString(R.string.title_not_connected));
                            break;
                        case STATE_DISCONNECTED:
                            activity.setConnected(false);
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    byte[] m = (byte[]) msg.obj;
                    String readMessage = new String(m, 0, msg.arg1);
                    activity.handleElmResult(readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    activity.mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    activity.m_logView.append("New device : " + activity.mConnectedDeviceName + "\n");
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(activity.getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}
