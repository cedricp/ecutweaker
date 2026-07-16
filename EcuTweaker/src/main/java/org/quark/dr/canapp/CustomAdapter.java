package org.quark.dr.canapp;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class CustomAdapter extends ArrayAdapter<String> {
    Context context;
    int color = Color.BLACK;
    String[] items;
    private int textSize = 20;

    public CustomAdapter(final Context context, final int textViewResourceId, final String[] objects) {
        super(context, textViewResourceId, objects);
        this.items = objects;
        this.context = context;
    }

    @Override
    public View getDropDownView(int position, View convertView,
                                @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(
                    android.R.layout.simple_spinner_item, parent, false);
        }

        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText(items[position]);
        //tv.setTextSize(textSize);
        return view;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(
                    android.R.layout.simple_spinner_item, parent, false);
        }

        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText(items[position]);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        return view;
    }

    public void setSpinnerTextSize(int size) {
        textSize = size;
    }

    public void setSpinnerTextColor(int color) {
        this.color = color;
    }

}
