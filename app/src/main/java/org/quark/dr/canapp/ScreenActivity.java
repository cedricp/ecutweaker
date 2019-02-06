package org.quark.dr.canapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.quark.dr.ecu.Ecu;
import org.quark.dr.ecu.EcuDatabase;
import org.quark.dr.ecu.IsotpDecode;
import org.quark.dr.ecu.Layout;

import java.util.ArrayList;
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
    private ScrollView m_scrollView;
    private RelativeLayout m_layoutView;
    private Ecu m_ecu;
    private Layout m_currentLayoutData;
    private Layout.ScreenData m_currentScreenData;
    private ImageButton m_searchButton, m_reloadButton, m_screenButton;
    private ImageView m_btIconStatus, m_btCommStatus;
    private TextView m_logView;
    private String m_currentScreenName, m_currentEcuName, m_ecuZipFileName;

    private HashMap<String, EditText> m_editTextViews;
    private HashMap<String, EditText> m_displayViews;
    private HashMap<String, Spinner> m_spinnerViews;
    private HashMap<String, Button> m_buttonsViews;
    private HashMap<Button, String> m_buttonsCommand;
    private HashMap<String, ArrayList<Layout.InputData>> m_requestsInputs;
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
    public static final int     MESSAGE_QUEUE_STATE     = 6;
    public static final String  DEVICE_NAME = "device_name";
    public static final String  TOAST       = "toast";
    private String              mConnectedDeviceName = null;

    public float convertToPixel(float val){
        return val / 6;
    }

    public float convertFontToPixel(int val){
        return val * 1.2f;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);

        String ecuFile = "";
        String ecuHref = "";

        Bundle b = getIntent().getExtras();
        if (b != null) {
            ecuFile = b.getString("ecuFile");
            ecuHref = b.getString("ecuRef");
        }

        m_ecuZipFileName = ecuFile;
        m_searchButton = findViewById(R.id.buttonSearch);
        m_reloadButton = findViewById(R.id.reloadButton);
        m_btIconStatus = findViewById(R.id.iconBt);
        m_screenButton = findViewById(R.id.screenButton);
        m_btCommStatus = findViewById(R.id.bt_comm);

        m_reloadButton.setEnabled(false);

        m_searchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connectElm();
            }
        });


        m_reloadButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateDisplays();
            }
        });

        m_screenButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                chooseCategory();
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
        m_btIconStatus.clearColorFilter();
        if (!ecuFile.isEmpty()){
            openEcu(ecuFile, ecuHref);
        }
        chooseCategory();
    }

    void openEcu(String ecuFile, String ecuName){
        String layoutFileName = ecuName + ".layout";

        System.out.println(">>>> Opening " + ecuName);
        System.out.println(">>>> Opening layout " + layoutFileName);

        String ecuJson = EcuDatabase.getZipFile(ecuFile, ecuName);
        String layoutJson = EcuDatabase.getZipFile(ecuFile, layoutFileName);

        m_ecu = new Ecu(ecuJson);
        m_currentLayoutData = new Layout(layoutJson);
        m_currentEcuName = ecuName;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        openEcu(m_ecuZipFileName, savedInstanceState.getString("ecu_name"));
        drawScreen(savedInstanceState.getString("screen_name"));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("screen_name", m_currentScreenName);
        outState.putString("ecu_name", m_currentEcuName);
        super.onSaveInstanceState(outState);
    }

    void drawScreen(String screenName)
    {
        m_displayViews = new HashMap<>();
        m_editTextViews = new HashMap<>();
        m_spinnerViews = new HashMap<>();
        m_buttonsViews = new HashMap<>();
        m_buttonsCommand = new HashMap<>();
        m_requestsInputs = new HashMap<>();
        m_displaysRequestSet = new HashSet<>();

        m_currentScreenData = m_currentLayoutData.getScreen(screenName);
        if (m_currentScreenData == null)
            return;

        m_layoutView.removeAllViews();
        m_layoutView.setLayoutParams(new FrameLayout.LayoutParams(
                (int) convertToPixel(m_currentScreenData.m_width + 50),
                (int) convertToPixel(m_currentScreenData.m_height) + 50));
        m_layoutView.setBackgroundColor(m_currentScreenData.m_color.get());

        Set<String> labels = m_currentScreenData.getLabels();
        for (String label : labels) {
            Layout.LabelData labelData = m_currentScreenData.getLabelData(label);
            TextView textView = new TextView(this);
            textView.setX(convertToPixel(labelData.rect.x));
            textView.setY(convertToPixel(labelData.rect.y));
            textView.setWidth((int) convertToPixel(labelData.rect.w));
            textView.setHeight((int) convertToPixel(labelData.rect.h));
            textView.setText(labelData.text);
            textView.setBackgroundColor(labelData.color.get());
            textView.setTextColor(labelData.font.color.get());
            textView.setTextSize(convertFontToPixel(labelData.font.size));
            if (labelData.alignment == 2){
                textView.setGravity(Gravity.CENTER_HORIZONTAL);
            } else if (labelData.alignment == 1){
                textView.setGravity(Gravity.RIGHT);
            }
            m_layoutView.addView(textView);
        }

        Set<String> displays = m_currentScreenData.getDisplays();
        for (String display : displays){
            Layout.DisplayData displaydata = m_currentScreenData.getDisplayData(display);
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
                textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
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

        Set<String> inputs = m_currentScreenData.getInputs();
        for (String input : inputs){
            Layout.InputData inputdata = m_currentScreenData.getInputData(input);
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
                spinner.setY(convertToPixel(inputdata.rect.y) - 5);
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        ((int) convertToPixel(inputdata.rect.w - inputdata.width)),
                        (int) convertToPixel(inputdata.rect.h) + 5);
                spinner.setLayoutParams(params);
                spinner.setBackgroundColor(inputdata.color.get());
                dataAdapter.setSpinnerTextSize((int)convertFontToPixel(inputdata.font.size));
                dataAdapter.setSpinnerTextColor(inputdata.font.color.get());
                spinner.setAdapter(dataAdapter);
                m_spinnerViews.put(inputdata.text, spinner);
                m_layoutView.addView(spinner);
            }
            if (!m_requestsInputs.containsKey(inputdata.request)){
                m_requestsInputs.put(inputdata.request, new ArrayList<Layout.InputData>());
            }
            ArrayList<Layout.InputData> tmp = m_requestsInputs.get(inputdata.request);
            tmp.add(inputdata);
        }

        Set<String> buttons = m_currentScreenData.getButtons();
        for (String button : buttons) {
            Layout.ButtonData buttondata = m_currentScreenData.getButtonData(button);
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
            m_buttonsCommand.put(buttonView, buttondata.uniqueName);
            m_layoutView.addView(buttonView);
            m_buttonsViews.put(buttondata.uniqueName, buttonView);
        }

        m_scrollView.requestLayout();
    }

    void updateDisplays(){
        if (!isChatConnected()) {
            setConnected(false);
            return;
        }

        if (m_currentScreenData == null){
            // No screen defined yet
            return;
        }

        sendCmd(m_ecu.default_sds);

        // screen pre-send data
        for (Pair<Integer, String> pair : m_currentScreenData.getPreSendData()){
            if (pair.first > 0)
                sendDelay(pair.first);
            Ecu.EcuRequest request = m_ecu.getRequest(pair.second);
            sendCmd(request.sentbytes);
        }

        if (m_displaysRequestSet == null)
            return;

        // Display requests send
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
        if (req.isEmpty() && response.isEmpty()) {
            // Test data
            response = "610A163232025800B43C3C1E3C0A0A0A0A012C5C6167B5BBC10A5C";
            req = "210A";
//            response = "610F1F1F7F01";
//            req = "210F";
        }
        try {
            for (String requestname : m_displaysRequestSet) {
                Ecu.EcuRequest request = m_ecu.getRequest(requestname);
                Log.i("CanApp", "Managing request : " + requestname);
                if (request == null) {
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
        } catch (Exception e) {
            m_logView.append(e.toString());
            e.printStackTrace();
        }
    }

    private View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (! m_buttonsCommand.containsKey(v))
                return;
            String uniqueName = m_buttonsCommand.get(v);
            exectuteButtonCommands(uniqueName);
        }
    };

    void exectuteButtonCommands(String buttonUniqueName){
        Layout.ButtonData buttonData = m_currentScreenData.getButtonData(buttonUniqueName);
        if (buttonData == null)
            return;

        ArrayList<Pair<Integer, String>> commands = new ArrayList<>();
        for(Pair<Integer, String> sendData: buttonData.sendData){
            Integer delay = sendData.first;
            Ecu.EcuRequest request = m_ecu.getRequest(sendData.second);
            if (request == null){
                Log.d("CanApp", "Cannot find request " + sendData.second);
                return;
            }

            if (!m_requestsInputs.containsKey(sendData.second)){
                commands.add(new Pair<>(delay, request.sentbytes));
                Log.d("CanApp", ">>>>>>>>>>> Computed (no data) " + request.sentbytes);
                continue;
            }

            HashMap<String, Object> inputBuilder = new HashMap<>();

            ArrayList<Layout.InputData> inputs = m_requestsInputs.get(sendData.second);
            for (Layout.InputData input : inputs){
                if (m_editTextViews.containsKey(input.text)){
                    EditText editText = m_editTextViews.get(input.text);
                    String currentText = editText.getText().toString();
                    if (request.sendbyte_dataitems.containsKey(input.text)){
                        Ecu.EcuData ecuData = m_ecu.getData(input.text);
                        if (!ecuData.bytesascii) {
                            if (ecuData.scaled) {
                                if (!currentText.matches("[-+]?[0-9]*\\.?[0-9]+")) {
                                    Toast.makeText(getApplicationContext(), "Invalid value, check inputs", Toast.LENGTH_LONG).show();
                                    return;
                                }
                            } else {
                                if (!IsotpDecode.isHexadecimal(currentText)) {
                                    Toast.makeText(getApplicationContext(), "Invalid value, check inputs", Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                        }
                    }
                    inputBuilder.put(input.text, currentText);
                }

                if (m_spinnerViews.containsKey(input.text)){
                    Spinner spinner = m_spinnerViews.get(input.text);
                    String currentText = spinner.getSelectedItem().toString();
                    inputBuilder.put(input.text, currentText);
                }
            }

            byte[] builtStream = m_ecu.setRequestValues(sendData.second, inputBuilder);
            Log.d("CanApp","Computed frame : " + Ecu.byteArrayToHex(builtStream));
            commands.add(new Pair<Integer, String>(delay, Ecu.byteArrayToHex(builtStream)));
        }

        for (Pair<Integer, String> command : commands){
            if (command.first > 0)
                sendDelay(command.first);
            sendCmd(command.second);
        }
    }

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
            m_btIconStatus.setImageResource(R.drawable.ic_bt_connected);
            m_btIconStatus.setColorFilter(Color.GREEN);
            m_reloadButton.setEnabled(true);
        } else {
            m_btIconStatus.setImageResource(R.drawable.ic_bt_disconnected);
            m_btIconStatus.clearColorFilter();
            m_reloadButton.setEnabled(false);
        }
    }

    private void chooseCategory(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose a category");

        final String[] categories =
                m_currentLayoutData.getCategories().toArray(
                        new String[m_currentLayoutData.getCategories().size()]);

        builder.setItems(categories, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selected = categories[which];
                chooseScreen(selected);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void chooseScreen(String screenname){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose a screen");

        final String[] screens =
                m_currentLayoutData.getScreenNames(screenname).toArray(
                        new String[m_currentLayoutData.getScreenNames(screenname).size()]);

        builder.setItems(screens, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selected = screens[which];
                drawScreen(selected);
                m_currentScreenName = selected;
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
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

    private void sendDelay(int delay) {
        // Check that we're actually connected before trying anything
        if (mBluetoothAdapter == null) {
            m_logView.append("Trying to send command without BT connection\n");
            return;
        }
        if (!isChatConnected()) {
            m_logView.append("Trying to send command without chat session\n");
            return;
        }

        // Send command
        mChatService.write("DELAY:" + Integer.toString(delay));
    }

    private void setElMWorking(boolean isWorking){
        if (isWorking) {
            m_btCommStatus.setColorFilter(Color.GREEN);
            for (Button button : m_buttonsViews.values()){
                button.setEnabled(false);
            }
        } else {
            m_btCommStatus.clearColorFilter();
            for (Button button : m_buttonsViews.values()){
                button.setEnabled(true);
            }
        }
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
                            //activity.setStatus(activity.getString(R.string.title_connected_to, activity.mConnectedDeviceName));
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
                case MESSAGE_QUEUE_STATE:
                    int queue_len = msg.arg1;
                    activity.setElMWorking(queue_len > 0);
                    break;
            }
        }
    }
}
