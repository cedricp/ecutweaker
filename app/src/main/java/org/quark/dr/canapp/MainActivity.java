package org.quark.dr.canapp;

import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import java.util.HashMap;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "org.quark.dr.canapp";
    private static CanAdapter mCanAdapter;
    private TextToSpeech mTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTts = new TextToSpeech(this,  new TextToSpeech.OnInitListener(){
            @Override
            public void onInit(int initStatus) {
                if (initStatus == TextToSpeech.SUCCESS) {
                    if (mTts.isLanguageAvailable(Locale.FRANCE) == TextToSpeech.LANG_AVAILABLE)
                        mTts.setLanguage(Locale.FRANCE);
                }
            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCanAdapter = new CanAdapter(this);
    }

    void saySomething(){
        Log.i(TAG, "SPEAK");
        HashMap<String, String> params = new HashMap<String, String>();
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_SYSTEM));

        mTts.speak("Bonjour, peti test de la fonction vocale", TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    protected void onDestroy(){
        mCanAdapter.shutdown();
        mTts.shutdown();
        mTts.stop();
        super.onDestroy();
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
}
