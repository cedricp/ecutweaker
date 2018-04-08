package org.quark.dr.canapp;

import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.util.HashMap;
import java.util.Locale;


public class MainActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {
    private static final String TAG = "org.quark.dr.canapp";
    private static CanAdapter mCanAdapter;
    private TextToSpeech mTts;
    private AudioManager mAudioManager;
    private int mOldVolume;

    @Override
    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            if (mTts.isLanguageAvailable(Locale.FRANCE) == TextToSpeech.LANG_AVAILABLE)
                mTts.setLanguage(Locale.FRANCE);
        }
    }

    @Override
    public void onUtteranceCompleted(String utteranceId)
    {
        if(utteranceId.equals("FINISHED PLAYING"))
        {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOldVolume, 0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTts = new TextToSpeech(this, this);
        mAudioManager= (AudioManager)getSystemService(this.AUDIO_SERVICE);
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
        HashMap<String, String> params = new HashMap<String, String>();
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_NOTIFICATION));
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "FINISHED PLAYING");

        mTts.speak("Bonjour, peti test de la fonction vocale", TextToSpeech.QUEUE_ADD, null);

        mOldVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOldVolume / 3, 0);
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
