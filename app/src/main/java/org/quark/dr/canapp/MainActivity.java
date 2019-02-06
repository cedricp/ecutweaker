package org.quark.dr.canapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import org.quark.dr.ecu.Ecu;
import org.quark.dr.ecu.EcuDatabase;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "EcuTweaker";
    final static int PERMISSIONS_ACCESS_EXTERNAL_STORAGE = 0;
    // Intent request codes
    private static final int    REQUEST_CONNECT_DEVICE = 1;
    private static final int    REQUEST_ENABLE_BT      = 3;

    private static final String DEFAULT_PREF_TAG = "default";

    private EcuDatabase m_ecuDatabase;
    private CheckBox m_databaseTextView;
    private Button m_btButton;
    private ListView m_ecuListView, m_deviceListView;
    private ArrayList<EcuDatabase.EcuInfo> m_currentEcuInfoList;
    private String m_ecuFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        m_databaseTextView = findViewById(R.id.databaseCheckBox);
        m_btButton = findViewById(R.id.btButton);
        m_ecuListView = findViewById(R.id.ecuListView);
        m_deviceListView = findViewById(R.id.deviceView);
        m_databaseTextView.setEnabled(false);

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
                ecuTypeSelected(info);
            }
        });

        m_deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
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

        m_ecuDatabase = new EcuDatabase();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){
            parseDatabase();
        } else {
            askPermission();
        }

        //startScreen("/sdcard/ecu.zip", "UCH_84P2_85_V3.json");
    }

    void ecuTypeSelected(String type){
        int ecuAddress = m_ecuDatabase.getAddressByFunction(type);
        if (ecuAddress < 0) {
            m_deviceListView.setAdapter(null);
            return;
        }

        ArrayList<EcuDatabase.EcuInfo> ecuArray = m_ecuDatabase.getEcuInfo(ecuAddress);
        m_currentEcuInfoList = ecuArray;
        if (ecuArray == null) {
            m_deviceListView.setAdapter(null);
            return;
        }
        ArrayList<String> ecuNames = new ArrayList<>();
        for(EcuDatabase.EcuInfo info : ecuArray){
            ecuNames.add(info.ecuName);
        }
        ArrayAdapter<String> adapter;

        adapter=new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                ecuNames);
        m_deviceListView.setAdapter(adapter);
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
        try {
            Intent serverIntent = new Intent(this, ScreenActivity.class);
            Bundle b = new Bundle();
            b.putString("ecuFile", ecuFile);
            b.putString("ecuRef", ecuHREFName);
            serverIntent.putExtras(b);
            startActivity(serverIntent);
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
                return;
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Log.d(TAG, "onActivityResult " + address);
                    SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
                    SharedPreferences.Editor edit = defaultPrefs.edit();
                    edit.putString("btAdapterAddress", address);
                    edit.commit();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                }
        }
    }

    void parseDatabase(){
        String ecuFile = "";
        SharedPreferences defaultPrefs = this.getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        if (defaultPrefs.contains("ecuZipFile")) {
            ecuFile = defaultPrefs.getString("ecuZipFile", "");
            System.out.println(">>> Default ecu OK : "+ecuFile);
        }

        new LoadDbTask(m_ecuDatabase).execute(ecuFile);
    }

    void updateListView(String ecuFile){
        m_databaseTextView.setChecked(true);
        SharedPreferences defaultPrefs = getSharedPreferences(DEFAULT_PREF_TAG, MODE_PRIVATE);
        SharedPreferences.Editor edit = defaultPrefs.edit();
        edit.putString("ecuZipFile", ecuFile);
        edit.commit();

        m_ecuFilePath = ecuFile;

        ArrayAdapter<String> adapter;
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                m_ecuDatabase.getEcuByFunctions());
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
                ecuFile = m_ecuDatabase.loadDatabase(ecuFile);
            } catch (EcuDatabase.DatabaseException e){
                Log.e(TAG, "Database exception : " + e.getMessage());
                return "";
            }

            return ecuFile;
        }

        @Override
        protected void onPostExecute(String ecuFile) {
            updateListView(ecuFile);
        }
    }

}
