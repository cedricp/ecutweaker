package org.quark.dr.canapp;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsoluteLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.quark.dr.ecu.Ecu;
import org.quark.dr.ecu.Layout;

import java.io.DataInput;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "org.quark.dr.canapp";
    private TextToSpeech mTts;
    private ScrollView m_scrollView;
    private RelativeLayout m_layoutView;
    private Ecu m_ecu;
    private Layout m_layout;
    private HashMap<String, EditText> m_edittextviews;
    private HashMap<String, EditText> m_displayviews;
    private HashMap<String, Spinner> m_spinnerviews;

    public static float convertDpToPixel(float dp, Context context){
        return dp / 10;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_scrollView = this.findViewById(R.id.scrollView);
        m_layoutView = this.findViewById(R.id.mainLayout);

        InputStream ecu_stream = getClass().getClassLoader().getResourceAsStream("test.json");

        m_ecu = new Ecu(ecu_stream);
        InputStream layout_stream = getClass().getClassLoader().getResourceAsStream("test.json.layout");
        m_layout = new Layout(layout_stream);
        drawScreen( "Misc Values and Timings");
    }

    void drawScreen(String screenName)
    {
        m_displayviews = new HashMap<>();
        m_edittextviews = new HashMap<>();
        m_spinnerviews = new HashMap<>();

        Layout.ScreenData screenData = m_layout.getScreen(screenName);

        m_layoutView.getLayoutParams().width = (int)convertDpToPixel(screenData.m_width, this);
        m_layoutView.getLayoutParams().height = (int)convertDpToPixel(screenData.m_height, this);
        m_layoutView.setBackgroundColor(screenData.m_color.get());

        Set<String> labels = screenData.getLabels();
        for (String label : labels) {
            Layout.LabelData labeldata = screenData.getLabelData(label);
            TextView textView = new TextView(this);
            textView.setX(convertDpToPixel(labeldata.rect.x, this));
            textView.setY(convertDpToPixel(labeldata.rect.y, this));
            textView.setWidth((int) convertDpToPixel(labeldata.rect.w, this));
            textView.setHeight((int) convertDpToPixel(labeldata.rect.h, this));
            textView.setText(labeldata.text);
            textView.setBackgroundColor(labeldata.color.get());
            textView.setTextColor(labeldata.font.color.get());
            textView.setTextSize(labeldata.font.size);
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
                textView.setX(convertDpToPixel(displaydata.rect.x, this));
                textView.setY(convertDpToPixel(displaydata.rect.y, this));
                textView.setWidth((int) convertDpToPixel(displaydata.width, this));
                textView.setHeight((int) convertDpToPixel(displaydata.rect.h, this));
                textView.setText(displaydata.text);
                textView.setTextColor(displaydata.font.color.get());
                textView.setBackgroundColor(displaydata.color.get());
                textView.setTextSize(displaydata.font.size);
                m_layoutView.addView(textView);
            }

            EditText textEdit = new EditText(this);
            textEdit.setX(convertDpToPixel(displaydata.rect.x + displaydata.width, this));
            textEdit.setY(convertDpToPixel(displaydata.rect.y, this));
            textEdit.setWidth((int)convertDpToPixel(displaydata.rect.w - displaydata.width, this));
            textEdit.setHeight((int)convertDpToPixel(displaydata.rect.h, this));
            textEdit.setTextSize(displaydata.font.size);
            textEdit.setEnabled(false);
            textEdit.setText("NO DATA");
            textEdit.setPadding(0,0,0,4);
            textEdit.setBackgroundColor(displaydata.color.get());
            textEdit.setTextColor(displaydata.font.color.get());
            m_displayviews.put(displaydata.text, textEdit);
            m_layoutView.addView(textEdit);
        }

        Set<String> inputs = screenData.getInputs();
        for (String input : inputs){
            Layout.InputData inputdata = screenData.getInputData(input);
            if (inputdata.width > 0) {
                TextView textView = new TextView(this);
                textView.setX(convertDpToPixel(inputdata.rect.x, this));
                textView.setY(convertDpToPixel(inputdata.rect.y, this));
                textView.setWidth((int) convertDpToPixel(inputdata.width, this));
                textView.setHeight((int) convertDpToPixel(inputdata.rect.h, this));
                textView.setText(inputdata.text);
                textView.setTextColor(inputdata.font.color.get());
                textView.setBackgroundColor(inputdata.color.get());
                textView.setBreakStrategy(1);
                m_layoutView.addView(textView);
            }

            if (m_ecu.getData(inputdata.text).items.size() == 0) {
                EditText textEdit = new EditText(this);
                textEdit.setX(convertDpToPixel(inputdata.rect.x + inputdata.width, this));
                textEdit.setY(convertDpToPixel(inputdata.rect.y, this));
                textEdit.setWidth((int) convertDpToPixel(inputdata.rect.w - inputdata.width, this));
                textEdit.setHeight((int) convertDpToPixel(inputdata.rect.h, this));
                textEdit.setTextSize(inputdata.font.size);
                textEdit.setPadding(0, 0, 0, 4);
                textEdit.setTextColor(inputdata.font.color.get());
                textEdit.setBackgroundColor(inputdata.color.get());
                textEdit.setSingleLine();
                m_edittextviews.put(inputdata.text, textEdit);
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
                spinner.setX(convertDpToPixel(inputdata.rect.x + inputdata.width, this));
                spinner.setY(convertDpToPixel(inputdata.rect.y, this));
                ViewGroup.LayoutParams params = spinner.getLayoutParams();
                if (params != null) {
                    params.width = ((int) convertDpToPixel(inputdata.rect.w - inputdata.width, this));
                    params.height = ((int) convertDpToPixel(inputdata.rect.h, this));
                }
                spinner.setBackgroundColor(inputdata.color.get());
                dataAdapter.setSpinnerTextSize(inputdata.font.size);
                dataAdapter.setSpinnerTextColor(inputdata.font.color.get());
                spinner.setAdapter(dataAdapter);
                m_spinnerviews.put(inputdata.text, spinner);
                m_layoutView.addView(spinner);
            }
        }

        Set<String> buttons = screenData.getButtons();
        for (String button : buttons) {
            Layout.ButtonData buttondata = screenData.getButtonData(button);
            Button buttonView = new Button(this);
            buttonView.setX(convertDpToPixel(buttondata.rect.x, this));
            buttonView.setY(convertDpToPixel(buttondata.rect.y, this));
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    (int) convertDpToPixel(buttondata.rect.w, this),
                    (int) convertDpToPixel(buttondata.rect.h, this));
            buttonView.setLayoutParams(params);
            buttonView.setPadding(0, 0, 0, 0);
            buttonView.setText(buttondata.text);
            buttonView.setTextSize(buttondata.font.size);
            buttonView.setOnClickListener(buttonClickListener);
            m_layoutView.addView(buttonView);
        }

        m_scrollView.requestLayout();
    }

    private View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button clickedButton = (Button)v;
            System.out.println("Clicked on " + clickedButton.getText());
        }
    };

    @Override
    protected void onDestroy(){
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
