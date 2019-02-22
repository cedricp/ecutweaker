package org.quark.dr.canapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import org.quark.dr.ecu.Ecu;
import org.quark.dr.ecu.EcuDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import static org.quark.dr.canapp.ElmThread.STATE_CONNECTED;
import static org.quark.dr.canapp.ElmThread.STATE_CONNECTING;
import static org.quark.dr.canapp.ElmThread.STATE_DISCONNECTED;
import static org.quark.dr.canapp.ElmThread.STATE_LISTEN;
import static org.quark.dr.canapp.ElmThread.STATE_NONE;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_DEVICE_NAME;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_QUEUE_STATE;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_READ;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_STATE_CHANGE;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_TOAST;
import static org.quark.dr.canapp.ScreenActivity.TOAST;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "EcuTweaker";
    final static int PERMISSIONS_ACCESS_EXTERNAL_STORAGE = 0;
    // Intent request codes
    private static final int    REQUEST_CONNECT_DEVICE = 1;
    private static final int    REQUEST_SCREEN         = 2;
    private static final int    REQUEST_ENABLE_BT      = 3;
    private static final String DEFAULT_PREF_TAG = "default";

    public static final String PREF_DEVICE_ADDRESS = "btAdapterAddress";
    public static final String PREF_ECUZIPFILE = "ecuZipFile";


    private EcuDatabase m_ecuDatabase;
    private TextView m_statusView;
    private Button m_btButton, m_scanButton, m_scanNewButton;
    private ImageButton m_chooseProjectButton;
    private ImageView m_btIconImage;
    private ListView m_ecuListView, m_specificEcuListView;
    private ArrayList<EcuDatabase.EcuInfo> m_currentEcuInfoList;
    private String m_ecuFilePath, m_btDeviceAddress, m_currentProject;
    private int m_currentEcuAddressId;
    private TextView m_viewSupplier, m_viewDiagVersion, m_viewVersion, m_viewSoft, m_logView;

    private ElmThread m_chatService;
    private Handler mHandler = null;
    private EcuDatabase.EcuIdentifierNew m_ecuIdentifierNew = null;
    private Timer mConnectionTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
    }

    private void initialize(){
        m_currentProject = "";
        m_currentEcuAddressId = -1;

        mHandler = new MainActivity.messageHandler(this);

        m_statusView = findViewById(R.id.statusView);
        m_btButton = findViewById(R.id.btButton);
        m_ecuListView = findViewById(R.id.ecuListView);
        m_specificEcuListView = findViewById(R.id.deviceView);
        m_scanButton = findViewById(R.id.buttonScan);
        m_scanNewButton = findViewById(R.id.buttonScanNew);
        m_chooseProjectButton = findViewById(R.id.projectButton);
        m_btIconImage = findViewById(R.id.btIcon);
        m_viewDiagVersion = findViewById(R.id.textViewDiagversion);
        m_viewSupplier = findViewById(R.id.textViewSupplier);
        m_viewSoft = findViewById(R.id.textViewSoft);
        m_viewVersion = findViewById(R.id.textViewVersion);
        m_logView = findViewById(R.id.logView);

        m_logView.setGravity(Gravity.BOTTOM);
        m_logView.setMovementMethod(new ScrollingMovementMethod());
        m_logView.setBackgroundResource(R.drawable.edittextroundgreen);

        m_scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanBus();
            }
        });

        m_scanNewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanBusNew();
            }
        });

        m_btButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectBtDevice();
            }
        });

        m_ecuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String info = ((TextView) view).getText().toString();
                ecuTypeSelected(info, m_currentProject);
            }
        });

        m_specificEcuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if ( m_currentEcuInfoList == null || m_ecuFilePath == null){
                    return;
                }
                String stringToSearch = ((TextView)view).getText().toString();
                for (EcuDatabase.EcuInfo ecuinfo : m_currentEcuInfoList){
                    if (stringToSearch.equals(ecuinfo.ecuName)){
                        startScreen(m_ecuFilePath, ecuinfo.href);
                    }
                }
            }
        });

        m_chooseProjectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseProject();
            }
        });

        SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        m_btDeviceAddress = defaultPrefs.getString(PREF_DEVICE_ADDRESS, "");

        m_ecuDatabase = new EcuDatabase();
        m_ecuIdentifierNew = m_ecuDatabase.new EcuIdentifierNew();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){
            parseDatabase();
        } else {
            askPermission();
        }

        startConnectionTimer();

        // Only for debug purpose
        //startScreen("/sdcard/ecu.zip", "UCH_84P2_85_V3.json");
    }

    private void startConnectionTimer(){
        TimerTask timertask = new TimerTask() {
            @Override
            public void run() {
                if(!isChatConnected())
                    connectDevice();
            }
        };

        mConnectionTimer = new Timer();
        mConnectionTimer.schedule(timertask, 1000, 3000);
    }

    private void stopConnectionTimer(){
        mConnectionTimer.cancel();
    }

    private void connectDevice() {
        // address is the device MAC address
        // Get the BluetoothDevice object
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null || m_btDeviceAddress.isEmpty())
            return;

        BluetoothDevice device = btAdapter.getRemoteDevice(m_btDeviceAddress);
        // Attempt to connect to the device
        setupChat(true);
        m_chatService.connect(device);
    }

    private void setupChat(boolean force) {
        if (force && m_chatService != null){
            m_chatService.stop();
        }

        Log.d(TAG, "setupChat()");
        // Initialize the BluetoothChatService to perform bluetooth connections
        if (m_chatService != null)
            m_chatService.stop();
        m_chatService = new ElmThread(mHandler);
        m_chatService.start();
    }

    void initBus(String protocol){
        if (m_chatService != null) {
            String txa = m_ecuDatabase.getTxAddressById(m_currentEcuAddressId);
            String rxa = m_ecuDatabase.getRxAddressById(m_currentEcuAddressId);
            if (protocol.equals("CAN")) {
                m_chatService.initCan(rxa, txa);
            } else if (protocol.equals("KWP2000")){
                String hexAddr = Ecu.padLeft(Integer.toHexString(m_currentEcuAddressId),
                        2, "0");
                m_chatService.initKwp(hexAddr, true);
            }
        }
    }

    void scanBus(){
        if(!isChatConnected()){
            return;
        }

        if (m_currentEcuInfoList == null || m_currentEcuInfoList.isEmpty())
            return;

        m_chatService.initElm();
        initBus("CAN");

        sendCmd("10C0");
        sendCmd("2180");
    }

    void scanBusNew(){
        if(m_chatService == null || m_chatService.getState() != STATE_CONNECTED){
            return;
        }

        if (m_currentEcuInfoList == null || m_currentEcuInfoList.isEmpty())
            return;

        m_ecuIdentifierNew.reInit(m_currentEcuAddressId);

        m_chatService.initElm();
        initBus("CAN");

        sendCmd("1003");
        sendCmd("22F1A0");
        sendCmd("22F18A");
        sendCmd("22F194");
        sendCmd("22F195");
    }

    void ecuTypeSelected(String type, String project){
        m_specificEcuListView.setBackgroundColor(Color.WHITE);

        int ecuAddress = m_ecuDatabase.getAddressByFunction(type);
        if (ecuAddress < 0) {
            m_specificEcuListView.setAdapter(null);
            return;
        }
        m_currentEcuAddressId = ecuAddress;
        ArrayList<EcuDatabase.EcuInfo> ecuArray = m_ecuDatabase.getEcuInfo(ecuAddress);
        m_currentEcuInfoList = ecuArray;
        if (ecuArray == null) {
            m_specificEcuListView.setAdapter(null);
            return;
        }
        ArrayList<String> ecuNames = new ArrayList<>();
        for(EcuDatabase.EcuInfo info : ecuArray){
            if (project.isEmpty() || info.projects.contains(project))
                ecuNames.add(info.ecuName);
        }
        Collections.sort(ecuNames);
        ArrayAdapter<String> adapter;

        adapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                ecuNames);
        m_specificEcuListView.setAdapter(adapter);
    }

    void selectBtDevice(){
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            try {
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            } catch (android.content.ActivityNotFoundException e) {
                Log.e(TAG, "+++ ActivityNotFoundException +++");
            }
        }
    }

    void startScreen(String ecuFile, String ecuHREFName){
        if (m_chatService != null)
            m_chatService.stop();

        stopConnectionTimer();

        try {
            Intent serverIntent = new Intent(this, ScreenActivity.class);
            Bundle b = new Bundle();
            b.putString("ecuFile", ecuFile);
            b.putString("ecuRef", ecuHREFName);
            b.putString("deviceAddress", m_btDeviceAddress);
            serverIntent.putExtras(b);
            startActivityForResult(serverIntent, REQUEST_SCREEN);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "+++ ActivityNotFoundException +++");
        }
    }

    void askPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_ACCESS_EXTERNAL_STORAGE);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_ACCESS_EXTERNAL_STORAGE);
            }
        } else {
            parseDatabase();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_ACCESS_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    parseDatabase();
                } else {

                }
            }
        }
    }

    @Override
    public void onDestroy()
    {
        Log.d(TAG, "+ ON DESTROY +");
        super.onDestroy();
        stopConnectionTimer();
        if (m_chatService != null)
            m_chatService.stop();
    }

    @Override
    public void onStop()
    {
        Log.d(TAG, "+ ON STOP +");
        super.onStop();
        stopConnectionTimer();
        if (m_chatService != null)
            m_chatService.stop();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "+ ON START +");
        super.onStart();

        connectDevice();

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Log.d(TAG, "onActivityResult " + address);
                    SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
                    SharedPreferences.Editor edit = defaultPrefs.edit();
                    edit.putString(PREF_DEVICE_ADDRESS, address);
                    edit.commit();
                    m_btDeviceAddress = address;
                    connectDevice();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                }
                break;
            case REQUEST_SCREEN:
                setConnected(false);
                startConnectionTimer();
                break;
        }
    }

    void parseDatabase(){
        String ecuFile = "";
        SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        if (defaultPrefs.contains(PREF_ECUZIPFILE)) {
            ecuFile = defaultPrefs.getString(PREF_ECUZIPFILE, "");
        }
        m_statusView.setText("INDEXING DATABASE...");
        new LoadDbTask(m_ecuDatabase).execute(ecuFile);
    }

    void updateEcuTypeListView(String ecuFile, String project){
        if (ecuFile.isEmpty()){
            m_statusView.setText("DATABASE NOT FOUND");
            return;
        }
        m_statusView.setText("DATABASE LOADED");
        SharedPreferences defaultPrefs = getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        SharedPreferences.Editor edit = defaultPrefs.edit();
        edit.putString(PREF_ECUZIPFILE, ecuFile);
        edit.commit();

        m_ecuFilePath = ecuFile;

        ArrayAdapter<String> adapter;
        ArrayList<String> adapterList = m_ecuDatabase.getEcuByFunctionsAndType(project);
        Collections.sort(adapterList);
        if (adapterList.isEmpty())
            return;
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                adapterList);

        m_ecuListView.setAdapter(adapter);

        Log.i(TAG, "Database sucessfully loaded");
    }

    public class LoadDbTask extends AsyncTask<String, Void, String> {

        private final EcuDatabase db;

        public LoadDbTask(EcuDatabase data) {
            this.db = data;
        }

        @Override
        protected String doInBackground(String... params) {
            String ecuFile = params[0];
            try {
                String appDir = getApplicationContext().getFilesDir().getAbsolutePath();
                ecuFile = m_ecuDatabase.loadDatabase(ecuFile, appDir);
            } catch (EcuDatabase.DatabaseException e){
                Log.e(TAG, "Database exception : " + e.getMessage());
                return "";
            }

            return ecuFile;
        }

        @Override
        protected void onPostExecute(String ecuFile) {
            updateEcuTypeListView(ecuFile, "");
        }
    }

    private void chooseProject(){
        if (!m_ecuDatabase.isLoaded())
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose a project");

        final String[] projects = m_ecuDatabase.getProjects();
        Arrays.sort(projects);
        builder.setItems(projects, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_currentProject = projects[which];
                updateEcuTypeListView(m_ecuFilePath, m_currentProject);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void handleElmResult(String elmMessage, int txa){
        String[] results = elmMessage.split(";");
        if (results.length < 2){
            return;
        }
        if (results[1].isEmpty() || results[0].substring(0,2).toUpperCase().equals("AT")){
            return;
        }

        String ecuRequest = results[0].replace(" ", "");
        String ecuResponse = results[1].replace(" ", "");

        m_logView.append("> " + results[0] + " : " + results[1] + "\n");

        /*
         * Old method auto identification
         */
        if (ecuResponse.length() > 39 && ecuResponse.substring(0,4).equals("6180")) {
            String supplier = new String(Ecu.hexStringToByteArray(ecuResponse.substring(16, 22)));
            String soft_version = ecuResponse.substring(32, 36);
            String version = ecuResponse.substring(36, 40);
            String diag_version_string = ecuResponse.substring(14, 16);
            int diag_version = Integer.parseInt(diag_version_string, 16);
            m_viewDiagVersion.setText(diag_version_string);
            m_viewSupplier.setText(supplier);
            m_viewSoft.setText(version);
            m_viewVersion.setText(soft_version);
            m_logView.append("Found ECU : Supplier " + supplier + " Diagnostic version : "
                    + diag_version_string + " Version : " + version
                    + " Soft version : " + soft_version + "\n");

            EcuDatabase.EcuInfo ecuInfo = m_ecuDatabase.identifyOldEcu(m_currentEcuAddressId,
                    supplier, soft_version, version, diag_version);

            if (ecuInfo != null) {
                ArrayList<String> ecuNames = new ArrayList<>();
                ecuNames.add(ecuInfo.ecuName);
                Collections.sort(ecuNames);
                ArrayAdapter<String> adapter;
                adapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_list_item_1,
                        ecuNames);
                m_specificEcuListView.setAdapter(adapter);
                if (!ecuInfo.exact_match) {
                    m_specificEcuListView.setBackgroundColor(Color.RED);
                    m_logView.append("ECU partially match file (use with caution) "
                            + ecuInfo.ecuName + "\n");
                } else {
                    m_specificEcuListView.setBackgroundColor(Color.GREEN);
                    m_logView.append("ECU perfectly match file " + ecuInfo.ecuName + "\n");
                }
            }
        }

        /*
         * New method auto identification
         */
        if (ecuResponse.length() > 5) {
            if (ecuResponse.substring(0, 6).equals("62F1A0)")) {
                m_ecuIdentifierNew.diag_version = ecuResponse.substring(6);
                m_viewDiagVersion.setText(m_ecuIdentifierNew.diag_version);

            }
            if (ecuResponse.substring(0, 6).equals("62F18A")) {
                m_ecuIdentifierNew.supplier = ecuResponse.substring(6);
                m_viewSupplier.setText(m_ecuIdentifierNew.supplier);
            }
            if (ecuResponse.substring(0, 6).equals("62F194")) {
                m_ecuIdentifierNew.version = ecuResponse.substring(6);
                m_viewSoft.setText(m_ecuIdentifierNew.version);
            }
            if (ecuResponse.substring(0, 6).equals("62F195")) {
                m_ecuIdentifierNew.soft_version = ecuResponse.substring(6);
                m_viewVersion.setText(m_ecuIdentifierNew.soft_version);
            }
        }

        // If we get all ECU info, search in DB
        if (m_ecuIdentifierNew.isFullyFilled()){
            m_ecuIdentifierNew.reInit(-1);
            ArrayList<EcuDatabase.EcuInfo> ecuInfos = m_ecuDatabase.identifyNewEcu(m_ecuIdentifierNew);
            ArrayList<String> ecuNames = new ArrayList<>();
            boolean isExact = false;
            for (EcuDatabase.EcuInfo ecuInfo : ecuInfos) {
                ecuNames.add(ecuInfo.ecuName);
                if (ecuInfo.exact_match)
                    isExact = true;
            }
            Collections.sort(ecuNames);
            ArrayAdapter<String> adapter;
            adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1,
                    ecuNames);
            m_specificEcuListView.setAdapter(adapter);
            if (isExact){
                m_specificEcuListView.setBackgroundColor(Color.GREEN);
            } else {
                m_specificEcuListView.setBackgroundColor(Color.RED);
            }
        }
    }

    private static class messageHandler extends Handler {
        private MainActivity activity;
        messageHandler(MainActivity ac){
            activity = ac;
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case STATE_CONNECTED:
                            activity.setConnected(true);
                            break;
                        case STATE_CONNECTING:
                            activity.setConnected(false);
                            break;
                        case STATE_LISTEN:
                        case STATE_NONE:

                            break;
                        case STATE_DISCONNECTED:
                            activity.setConnected(false);
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    byte[] m = (byte[]) msg.obj;
                    String readMessage = new String(m, 0, msg.arg1);
                    int txa = msg.arg2;
                    try {
                        activity.handleElmResult(readMessage, txa);
                    } catch (Exception e){
                        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(activity);
                        dlgAlert.setMessage(e.getMessage());
                        dlgAlert.setTitle("Exception caught");
                        dlgAlert.setPositiveButton("OK", null);
                        dlgAlert.create().show();
                        e.printStackTrace();
                    }
                    break;
                case MESSAGE_DEVICE_NAME:

                    break;
                case MESSAGE_TOAST:
                    //Toast.makeText(activity.getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    activity.m_logView.append("Bluetooth manager message : " + msg.getData().getString(TOAST) + "\n");
                    break;
                case MESSAGE_QUEUE_STATE:
                    int queue_len = msg.arg1;
                    break;
            }
        }
    }

    void setConnected(boolean c){
        m_scanButton.setEnabled(c);
        m_scanNewButton.setEnabled(c);
        if (c){
            m_btIconImage.setColorFilter(Color.GREEN);
        } else {
            m_btIconImage.clearColorFilter();
        }
    }

    private void sendCmd(String cmd) {
        // Check that we're actually connected before trying anything
        if (!isChatConnected()) {
            return;
        }

        // Send command
        m_chatService.write(cmd);
    }

    private void sendDelay(int delay) {
        // Check that we're actually connected before trying anything
        if (!isChatConnected()) {
            return;
        }

        // Send command
        m_chatService.write("DELAY:" + Integer.toString(delay));
    }

    private boolean isChatConnected(){
        return (m_chatService != null && m_chatService.getState() == STATE_CONNECTED);
    }
}
