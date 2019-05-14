package org.quark.dr.canapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.quark.dr.ecu.Ecu;
import org.quark.dr.ecu.EcuDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import static org.quark.dr.canapp.ElmBluetooth.STATE_CONNECTED;
import static org.quark.dr.canapp.ElmBluetooth.STATE_CONNECTING;
import static org.quark.dr.canapp.ElmBluetooth.STATE_DISCONNECTED;
import static org.quark.dr.canapp.ElmBluetooth.STATE_NONE;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_DEVICE_NAME;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_QUEUE_STATE;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_READ;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_STATE_CHANGE;
import static org.quark.dr.canapp.ScreenActivity.MESSAGE_TOAST;
import static org.quark.dr.canapp.ScreenActivity.TOAST;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "EcuTweaker";
    final static int PERMISSIONS_ACCESS_EXTERNAL_STORAGE = 0;
    final static int PERMISSIONS_ACCESS_COARSE_LOCATION = 1;
    // Intent request codes
    private static final int    REQUEST_CONNECT_DEVICE = 1;
    private static final int    REQUEST_SCREEN         = 2;
    private static final int    REQUEST_ENABLE_BT      = 3;
    public static final String  DEFAULT_PREF_TAG = "default";

    public static final int     LINK_WIFI=0;
    public static final int     LINK_BLUETOOTH=1;

    public static final String PREF_DEVICE_ADDRESS = "btAdapterAddress";
    public static final String PREF_LICENSE_CODE = "licenseCode";
    public static final String PREF_GLOBAL_SCALE = "globalScale";
    public static final String PREF_FONT_SCALE = "fontScale";
    public static final String PREF_ECUZIPFILE = "ecuZipFile";
    public static final String PREF_PROJECT = "project";
    public static final String PREF_LINK_MODE =  "BT";

    public static String mLastLog;
    private EcuDatabase mEcuDatabase;
    private TextView mStatusView;
    private Button mBtButton, mScanButton;
    private ImageButton mChooseProjectButton, mLinkChooser;
    private ImageView mBtIconImage;
    private ListView mEcuListView, mSpecificEcuListView;
    private ArrayList<EcuDatabase.EcuInfo> mCurrentEcuInfoList;
    private String mEcuFilePath, mBtDeviceAddress, mCurrentProject;
    private int mCurrentEcuAddressId;
    private TextView mViewSupplier, mViewDiagVersion, mViewVersion, mViewSoft, mLogView;

    private ElmBase mChatService;
    private Handler mHandler = null;
    private EcuDatabase.EcuIdentifierNew mEcuIdentifierNew = null;
    private Timer mConnectionTimer;
    private LicenseLock mLicenseLock;
    private int mLinkMode;
    private boolean mActivateBluetoothAsked;

    public void displayLog(String message){

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
    }

    private String readFileAsString(String filePath) {
        String result = "No log file found";
        File file = new File(filePath);
        if ( file.exists() ) {
            result = "";
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                char current;
                while (fis.available() > 0) {
                    current = (char) fis.read();
                    result += String.valueOf(current);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fis != null)
                    try {
                        fis.close();
                    } catch (IOException ignored) {
                    }
            }
        }
        return result;
    }

    private void initialize(){
        long id = new BigInteger(Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID), 16).longValue();
        mLicenseLock = new LicenseLock(id);
        SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        String linkMode = defaultPrefs.getString(PREF_LINK_MODE, "BT");

        mCurrentProject = "";
        mCurrentEcuAddressId = -1;
        mActivateBluetoothAsked = false;

        mHandler = new MainActivity.messageHandler(this);
        mChatService = null;

        mStatusView = findViewById(R.id.statusView);
        mBtButton = findViewById(R.id.btButton);
        mEcuListView = findViewById(R.id.ecuListView);
        mSpecificEcuListView = findViewById(R.id.deviceView);
        mScanButton = findViewById(R.id.buttonScan);
        mChooseProjectButton = findViewById(R.id.projectButton);
        mLinkChooser = findViewById(R.id.linkChooser);
        mBtIconImage = findViewById(R.id.btIcon);
        mViewDiagVersion = findViewById(R.id.textViewDiagversion);
        mViewSupplier = findViewById(R.id.textViewSupplier);
        mViewSoft = findViewById(R.id.textViewSoft);
        mViewVersion = findViewById(R.id.textViewVersion);
        mLogView = findViewById(R.id.logView);

        mLogView.setGravity(Gravity.BOTTOM);
        mLogView.setMovementMethod(new ScrollingMovementMethod());
        mLogView.setBackgroundResource(R.drawable.edittextroundgreen);
        mLogView.setTextIsSelectable(true);

        mBtIconImage.setColorFilter(Color.RED);

        if (linkMode.equals("BT")) {
            mLinkMode = LINK_BLUETOOTH;
        } else {
            mLinkMode = LINK_WIFI;
        }
        setLink();

        Button viewLogButton = findViewById(R.id.viewLogButton);
        viewLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLastLog = readFileAsString(
                        MainActivity.this.getApplicationContext().getFilesDir().
                                getAbsolutePath() + "/log.txt");
                System.out.println("?? "  + MainActivity.this.getApplicationContext().getFilesDir().
                        getAbsolutePath());
                LayoutInflater inflater= LayoutInflater.from(MainActivity.this);
                View view = inflater.inflate(R.layout.custom_scroll, null);

                TextView textview = view.findViewById(R.id.textmsg);
                textview.setEnabled(true);
                textview.setText(mLastLog);
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle("LOGS");
                alertDialog.setView(view);
                AlertDialog alert = alertDialog.create();
                alert.setButton(AlertDialog.BUTTON_NEUTRAL,getResources().
                        getString(R.string.COPY_TO_CLIPBOARD),
                        new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager  clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("EcuTeakerLog", mLastLog);
                        clipboard.setPrimaryClip(clip);
                    }
                });
                alert.show();
            }
        });

        Button clearLogButton = findViewById(R.id.clearLogButton);

        clearLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(),
                        getResources().getString(R.string.LONGPRESS_TO_DELETE),
                        Toast.LENGTH_SHORT).show();
            }
        });

        clearLogButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String logFilename = MainActivity.this.getApplicationContext().getFilesDir().
                        getAbsolutePath() + "/log.txt";
                File logFile = new File(logFilename);
                if (logFile.exists()){
                    if (logFile.delete()){
                        Toast.makeText(getApplicationContext(),
                                getResources().getString(R.string.LOGFILE_DELETED),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(),
                                getResources().getString(R.string.LOGFILE_DELETE_FAILED),
                                Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });

        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onScanBus();
            }
        });

        mBtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectBtDevice();
            }
        });

        mEcuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String info = ((TextView) view).getText().toString();
                ecuTypeSelected(info, mCurrentProject);
            }
        });

        mSpecificEcuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if ( mCurrentEcuInfoList == null || mEcuFilePath == null){
                    return;
                }
                String stringToSearch = ((TextView)view).getText().toString();
                for (EcuDatabase.EcuInfo ecuinfo : mCurrentEcuInfoList){
                    if (stringToSearch.equals(ecuinfo.ecuName)){
                        startScreen(mEcuFilePath, ecuinfo.href);
                    }
                }
            }
        });

        ImageButton licenseButton = findViewById(R.id.licenseButton);
        licenseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLicenseCheck();
            }
        });

        mChooseProjectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseProject();
            }
        });

        mLinkChooser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLinkMode == LINK_WIFI) {
                    mLinkMode = LINK_BLUETOOTH;
                } else {
                    mLinkMode = LINK_WIFI;
                }
                setLink();
            }
        });

        mBtDeviceAddress = defaultPrefs.getString(PREF_DEVICE_ADDRESS, "");

        mEcuDatabase = new EcuDatabase();
        mEcuIdentifierNew = mEcuDatabase.new EcuIdentifierNew();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){
            parseDatabase();
        } else {
            askStoragePermission();
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            askLocationPermission();
        }

        mLogView.append("EcuTweaker " + BuildConfig.BUILD_TYPE + " "
                + getResources().getString(R.string.VERSION) + "\n");

        String licenseCode = defaultPrefs.getString(PREF_LICENSE_CODE, "");
        if (!licenseCode.isEmpty()){
            mLicenseLock.checkUnlock(licenseCode);
            setLicenseSatus();
        } else {
            mLogView.append(getResources().getString(R.string.USER_REQUEST_CODE) +" : "
                    + mLicenseLock.getPublicCode() + "\n");
            ((ImageButton)findViewById(R.id.licenseButton)).setColorFilter(Color.RED);
            mLogView.append("contact email : paillecedric@gmail.com\n");
            displayHelp();
        }
    }

    private void setLink(){
        /*
         * First disconnect chat
         */
        if (mChatService != null)
            mChatService.disconnect();

        if (mLinkMode == LINK_BLUETOOTH){
            mLinkChooser.setImageResource(R.drawable.ic_bt_connected);
            mBtButton.setEnabled(true);
            SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
            SharedPreferences.Editor edit = defaultPrefs.edit();
            edit.putString(PREF_LINK_MODE, "BT");
            edit.apply();

            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mActivateBluetoothAsked && btAdapter != null && !btAdapter.isEnabled()){
                mActivateBluetoothAsked = true;
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
        } else {
            mActivateBluetoothAsked = false;
            mLinkChooser.setImageResource(R.drawable.ic_wifi);
            mBtButton.setEnabled(false);
            SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
            SharedPreferences.Editor edit = defaultPrefs.edit();
            edit.putString(PREF_LINK_MODE, "WIFI");
            edit.apply();
        }
    }

    private void onLicenseCheck(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.APP_ENTER_CODE));
        builder.setMessage(getResources().getString(R.string.USER_REQUEST_CODE) + " : "
                + mLicenseLock.getPublicCode());
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mLicenseLock.checkUnlock(input.getText().toString());
                setLicenseSatus();
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.CANCEL), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void startConnectionTimer(){
        stopConnectionTimer();

        TimerTask timertask = new TimerTask() {
            @Override
            public void run() {
                if(!isChatConnected() && !isChatConnecting())
                    connectDevice();
            }
        };

        mConnectionTimer = new Timer();
        mConnectionTimer.schedule(timertask, 1000, 4000);
    }

    private void stopConnectionTimer(){
        if (mConnectionTimer != null) {
            mConnectionTimer.cancel();
            mConnectionTimer.purge();
            mConnectionTimer = null;
        }
    }

    private void setLicenseSatus(){
        if (mLicenseLock.isLicenseOk()){
            mLogView.append(getResources().getString(R.string.APP_UNLOCKED) + "\n");
            (findViewById(R.id.licenseButton)).setEnabled(false);
            SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG,
                    MODE_PRIVATE);
            SharedPreferences.Editor edit = defaultPrefs.edit();
            edit.putString(PREF_LICENSE_CODE, mLicenseLock.getPrivateCode());
            edit.apply();
            ((ImageButton)findViewById(R.id.licenseButton)).setColorFilter(Color.GREEN);
        } else {
            ((ImageButton)findViewById(R.id.licenseButton)).setColorFilter(Color.RED);
            mLogView.append(getResources().getString(R.string.WRONG_CODE) + "\n");
        }
    }

    private void connectDevice() {
        if (mChatService != null)
            mChatService.disconnect();

        if (mLinkMode == LINK_BLUETOOTH) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null && !btAdapter.isEnabled()){
                return;
            }

            mChatService = ElmBluetooth.createSingleton(mHandler,
                    getApplicationContext().getFilesDir().getAbsolutePath(),
                    false);
                    //new ElmBluetooth(mHandler,
                    //getApplicationContext().getFilesDir().getAbsolutePath(),
                    //false);
            // address is the device MAC address
            // Get the BluetoothDevice object
            if (btAdapter == null || mBtDeviceAddress.isEmpty() || isChatConnected())
                return;

            BluetoothDevice device = btAdapter.getRemoteDevice(mBtDeviceAddress);
            if (device == null)
                return;
            // Attempt to connect to the device
            mChatService.connect(mBtDeviceAddress);
        } else {
            mChatService = ElmWifi.createSingleton(getApplicationContext(), mHandler, getApplicationContext().
                    getFilesDir().getAbsolutePath(), false);
                    //new ElmWifi(getApplicationContext(), mHandler, getApplicationContext().
                    //getFilesDir().getAbsolutePath(), false);
            mChatService.connect("");
        }
    }

    void initBus(String protocol){
        if (isChatConnected()) {
            String txa = mEcuDatabase.getTxAddressById(mCurrentEcuAddressId);
            String rxa = mEcuDatabase.getRxAddressById(mCurrentEcuAddressId);
            if (rxa == null || txa == null)
                return;
            if (protocol.equals("CAN")) {
                mChatService.initCan(rxa, txa);
            } else if (protocol.equals("KWP2000")){
                String hexAddr = Ecu.padLeft(Integer.toHexString(mCurrentEcuAddressId),
                        2, "0");
                mChatService.initKwp(hexAddr, true);
            }
        }
    }

    void onScanBus(){
        if(!isChatConnected()){
            Toast.makeText(this, "No ELM connection", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("SELECT BUS");
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "CAN",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        scanBus();
                        scanBusNew();
                    }
                });

        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "KWP",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        scanBusKWP();
                    }
                });
        alertDialog.show();
    }

    void scanBus(){
        if(!isChatConnected()){
            Toast.makeText(this, "No ELM connection", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mCurrentEcuInfoList == null || mCurrentEcuInfoList.isEmpty()) {
            Toast.makeText(this, "Select an ECU type to auto identify", Toast.LENGTH_SHORT).show();
            return;
        }

        mChatService.initElm();
        initBus("CAN");
        mChatService.setTimeOut(1000);
        /*
         * (old) ECUs gives their identifiers with 2180 command
         */
        sendCmd("10C0");
        sendCmd("2180");
    }

    void scanBusKWP(){
        if(!isChatConnected()){
            return;
        }

        if (mCurrentEcuInfoList == null || mCurrentEcuInfoList.isEmpty())
            return;

        mChatService.initElm();
        initBus("KWP2000");
        mChatService.setTimeOut(1000);
        /*
         * (old) ECUs gives their identifiers with 2180 command
         */
        sendCmd("10C0");
        sendCmd("2180");
    }

    void scanBusNew(){
        if(!isChatConnected()){
            return;
        }

        if (mChatService == null || mCurrentEcuInfoList == null || mCurrentEcuInfoList.isEmpty())
            return;

        mEcuIdentifierNew.reInit(mCurrentEcuAddressId);

        mChatService.initElm();
        initBus("CAN");
        mChatService.setTimeOut(1000);
        /*
         * (new) ECUs gives their identifiers with these commands
         */
        sendCmd("1003");
        sendCmd("22F1A0");
        sendCmd("22F18A");
        sendCmd("22F194");
        sendCmd("22F195");
    }

    void ecuTypeSelected(String type, String project){
        mSpecificEcuListView.setBackgroundColor(Color.BLACK);

        int ecuAddress = mEcuDatabase.getAddressByFunction(type);
        if (ecuAddress < 0) {
            mSpecificEcuListView.setAdapter(null);
            return;
        }
        mCurrentEcuAddressId = ecuAddress;
        ArrayList<EcuDatabase.EcuInfo> ecuArray = mEcuDatabase.getEcuInfo(ecuAddress);
        mCurrentEcuInfoList = ecuArray;
        if (ecuArray == null) {
            mSpecificEcuListView.setAdapter(null);
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
        mSpecificEcuListView.setAdapter(adapter);
    }

    void selectBtDevice(){
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            stopConnectionTimer();
            try {
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            } catch (android.content.ActivityNotFoundException e) {

            }
        }
    }

    void startScreen(String ecuFile, String ecuHREFName){
        stopConnectionTimer();

        if (mChatService != null)
            mChatService.disconnect();
        setConnectionStatus(STATE_DISCONNECTED);

        try {
            Intent serverIntent = new Intent(this, ScreenActivity.class);
            Bundle b = new Bundle();
            b.putString("ecuFile", ecuFile);
            b.putString("ecuRef", ecuHREFName);
            b.putString("deviceAddress", mBtDeviceAddress);
            b.putBoolean("licenseOk", mLicenseLock.isLicenseOk());
            b.putInt("linkMode", mLinkMode);
            serverIntent.putExtras(b);
            startActivityForResult(serverIntent, REQUEST_SCREEN);
        } catch (android.content.ActivityNotFoundException e) {

        }
    }

    void askStoragePermission(){
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

    void askLocationPermission(){
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                Toast.makeText(this, "You need location permission to connect to WiFi", Toast.LENGTH_SHORT).show();
            }else{
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_ACCESS_COARSE_LOCATION);
            }
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
                }
            }
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        stopConnectionTimer();
        if (mChatService != null)
            mChatService.disconnect();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        stopConnectionTimer();
        if (mChatService != null)
            mChatService.disconnect();
    }

    @Override
    public void onStart() {
        super.onStart();
        startConnectionTimer();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG,
                            MODE_PRIVATE);
                    SharedPreferences.Editor edit = defaultPrefs.edit();
                    edit.putString(PREF_DEVICE_ADDRESS, address);
                    edit.commit();
                    mBtDeviceAddress = address;
                    startConnectionTimer();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                }
                break;
            case REQUEST_SCREEN:
                setConnectionStatus(STATE_DISCONNECTED);
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
        mStatusView.setText(getResources().getString(R.string.INDEXING_DB));
        new LoadDbTask(mEcuDatabase).execute(ecuFile);
    }

    void updateEcuTypeListView(String ecuFile, String project){
        if (ecuFile == null || ecuFile.isEmpty()){
            mStatusView.setText(getResources().getString(R.string.DB_NOT_FOUND));
            return;
        }

        //mEcuDatabase.checkMissings();

        SharedPreferences defaultPrefs = getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        SharedPreferences.Editor edit = defaultPrefs.edit();
        edit.putString(PREF_ECUZIPFILE, ecuFile);
        edit.commit();

        mEcuFilePath = ecuFile;
        ArrayAdapter<String> adapter;
        ArrayList<String> adapterList = mEcuDatabase.getEcuByFunctionsAndType(project);
        Collections.sort(adapterList);
        if (adapterList.isEmpty())
            return;
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                adapterList);

        mEcuListView.setAdapter(adapter);
    }

    public class LoadDbTask extends AsyncTask<String, Void, String> {
        private final EcuDatabase db;
        private String error;

        public LoadDbTask(EcuDatabase data) {
            this.db = data;
        }

        @Override
        protected String doInBackground(String... params) {
            String ecuFile = params[0];
            error = "";
            try {
                String appDir = getApplicationContext().getFilesDir().getAbsolutePath();
                ecuFile = mEcuDatabase.loadDatabase(ecuFile, appDir);
            } catch (EcuDatabase.DatabaseException e){
                error = e.getMessage();
                return "";
            }
            return ecuFile;
        }

        @Override
        protected void onPostExecute(String ecuFile) {
            SharedPreferences defaultPrefs = getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
            mCurrentProject = defaultPrefs.getString(PREF_PROJECT, "");
            updateEcuTypeListView(ecuFile, mCurrentProject);
            mStatusView.setText("ECU-TWEAKER");
            if (!error.isEmpty()){
                mLogView.append("Database exception : " + error + "\n");
            }
        }
    }

    private void displayHelp(){
        Spanned message = Html.fromHtml(getResources().getString(R.string.DEMO_MESSAGE));
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(getResources().getString(R.string.INFORMATION));
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getResources().getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    private void chooseProject(){
        if (!mEcuDatabase.isLoaded())
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.CHOOSE_PROJECT));

        /*
         * Clean view
         */
        ArrayList<String> ecuNames = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                ecuNames);
        mSpecificEcuListView.setAdapter(adapter);

        final String[] projects = mEcuDatabase.getModels();

        Arrays.sort(projects);
        builder.setItems(projects, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mCurrentProject = mEcuDatabase.getProjectFromModel(projects[which]);
                SharedPreferences defaultPrefs = getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
                SharedPreferences.Editor edit = defaultPrefs.edit();
                edit.putString(PREF_PROJECT, mCurrentProject);
                edit.commit();
                updateEcuTypeListView(mEcuFilePath, mCurrentProject);
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

        String ecuResponse = results[1].replace(" ", "");
        mLogView.append("> " + results[0] + " : " + results[1] + "\n");

        /*
         * Old method auto identification
         */
        if (ecuResponse.length() > 39 && ecuResponse.substring(0,4).equals("6180")) {
            String supplier = new String(Ecu.hexStringToByteArray(ecuResponse.substring(16, 22)));
            String soft_version = ecuResponse.substring(32, 36);
            String version = ecuResponse.substring(36, 40);
            String diag_version_string = ecuResponse.substring(14, 16);
            int diag_version = Integer.parseInt(diag_version_string, 16);
            mViewDiagVersion.setText(diag_version_string);
            mViewSupplier.setText(supplier);
            mViewSoft.setText(version);
            mViewVersion.setText(soft_version);
            mLogView.append(getResources().getString(R.string.ECU_FOUND) + " : " +
                    getResources().getString(R.string.SUPPLIER_VERSION) + " " + supplier +
                    getResources().getString(R.string.DIAG_VERSION) + " : "
                    + diag_version_string + " " +getResources().getString(R.string.VERSION)
                    + " : " + version
                    + " " + getResources().getString(R.string.SOFT_VERSION) + " : "
                    + soft_version + "\n");

            EcuDatabase.EcuInfo ecuInfo = mEcuDatabase.identifyOldEcu(mCurrentEcuAddressId,
                    supplier, soft_version, version, diag_version);

            if (ecuInfo != null) {
                ArrayList<String> ecuNames = new ArrayList<>();
                ecuNames.add(ecuInfo.ecuName);
                Collections.sort(ecuNames);
                ArrayAdapter<String> adapter;
                adapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_list_item_1,
                        ecuNames);
                mSpecificEcuListView.setAdapter(adapter);
                if (!ecuInfo.exact_match) {
                    mSpecificEcuListView.setBackgroundColor(Color.RED);
                    mLogView.append(getResources().getString(R.string.ECU_PART_MATCH) + " "
                            + ecuInfo.ecuName + "\n");
                } else {
                    mSpecificEcuListView.setBackgroundColor(Color.GREEN);
                    mLogView.append(getResources().getString(R.string.ECU_MATCH) + " " + ecuInfo.ecuName + "\n");
                }
            }
        }

        /*
         * New method auto identification
         */
        if (ecuResponse.length() > 5) {
            if (ecuResponse.substring(0, 6).equals("62F1A0)")) {
                mEcuIdentifierNew.diag_version = ecuResponse.substring(6);
                mViewDiagVersion.setText(mEcuIdentifierNew.diag_version);
            }
            if (ecuResponse.substring(0, 6).equals("62F18A")) {
                mEcuIdentifierNew.supplier = ecuResponse.substring(6);
                mViewSupplier.setText(mEcuIdentifierNew.supplier);
            }
            if (ecuResponse.substring(0, 6).equals("62F194")) {
                mEcuIdentifierNew.version = ecuResponse.substring(6);
                mViewSoft.setText(mEcuIdentifierNew.version);
            }
            if (ecuResponse.substring(0, 6).equals("62F195")) {
                mEcuIdentifierNew.soft_version = ecuResponse.substring(6);
                mViewVersion.setText(mEcuIdentifierNew.soft_version);
            }
        }

        // If we get all ECU info, search in DB
        if (mEcuIdentifierNew.isFullyFilled()){
            mEcuIdentifierNew.reInit(-1);
            ArrayList<EcuDatabase.EcuInfo> ecuInfos = mEcuDatabase.identifyNewEcu(mEcuIdentifierNew);
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
            mSpecificEcuListView.setAdapter(adapter);
            if (isExact){
                mSpecificEcuListView.setBackgroundColor(Color.GREEN);
            } else {
                mSpecificEcuListView.setBackgroundColor(Color.RED);
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
                    if (BuildConfig.DEBUG)
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case STATE_CONNECTED:
                            activity.setConnectionStatus(STATE_CONNECTED);
                            break;
                        case STATE_CONNECTING:
                            activity.setConnectionStatus(STATE_CONNECTING);
                            break;
                        case STATE_NONE:
                        case STATE_DISCONNECTED:
                            activity.setConnectionStatus(STATE_DISCONNECTED);
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
                    activity.mLogView.append(activity.getResources().getString(R.string.BT_MANAGER_MESSAGE) + " : " + msg.getData().getString(TOAST) + "\n");
                    break;
                case MESSAGE_QUEUE_STATE:
                    int queue_len = msg.arg1;
                    break;
            }
        }
    }

    void setConnectionStatus(int c){
        //mScanButton.setEnabled(c == STATE_CONNECTED ? true : false);
        if (c == STATE_CONNECTED){
            mBtIconImage.setColorFilter(Color.GREEN);
            mBtIconImage.setImageResource(R.drawable.ic_link_ok);
        } else if (c == STATE_DISCONNECTED){
            mBtIconImage.setColorFilter(Color.RED);
            mBtIconImage.setImageResource(R.drawable.ic_link_nok);
        } else if (c == STATE_CONNECTING){
            mBtIconImage.setColorFilter(Color.GRAY);
            mBtIconImage.setImageResource(R.drawable.ic_link_ok);
        }
    }

    private void sendCmd(String cmd) {
        // Check that we're actually connected before trying anything
        if (!isChatConnected()) {
            return;
        }

        // Send command
        mChatService.write(cmd);
    }

    private boolean isChatConnected(){
        return mChatService != null && (mChatService.getState() == STATE_CONNECTED);
    }

    private boolean isChatConnecting(){
        return mChatService != null && (mChatService.getState() == STATE_CONNECTING);
    }
}
