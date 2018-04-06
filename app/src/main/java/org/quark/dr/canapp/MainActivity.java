package org.quark.dr.canapp;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Locale;


public class MainActivity extends AppCompatActivity implements OnInitListener {
    private static final String TAG = "org.quark.dr.canapp";
    private static CanAdapter mCanAdapter;
    private TextToSpeech tts;

    @Override
    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            if (tts.isLanguageAvailable(Locale.FRANCE) == TextToSpeech.LANG_AVAILABLE)
                tts.setLanguage(Locale.FRANCE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tts = new TextToSpeech(this, this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mCanAdapter = new CanAdapter(this);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saySomething();
            }
        });
    }

    void saySomething(){
        Log.i(TAG, "SPEAK");

        tts.setLanguage(Locale.FRANCE);
        tts.speak("Bonjour, peti test de la fonction vocale", TextToSpeech.QUEUE_ADD, null);
        tts.speak("Nivo d'uile critik, faire nivo", TextToSpeech.QUEUE_ADD, null);
    }

    @Override
    protected void onDestroy(){
        mCanAdapter.shutdown();
        tts.shutdown();
        tts.stop();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){

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
}
