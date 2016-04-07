package com.audacious_software.passive_data_kit.generators;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.pdk.passivedatakit.R;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

@SuppressWarnings("unused")
public abstract class Generator
{
    public static final String PDK_METADATA = "passive-data-metadata";
    public static final java.lang.String IDENTIFIER = "generator-id";
    public static final String TIMESTAMP = "timestamp";
    public static final String GENERATOR = "generator";
    public static final String SOURCE = "source";
    public static final String MEDIA_ATTACHMENT_KEY = "attachment";
    public static final String MEDIA_CONTENT_TYPE_KEY = "attachment-type";
    public static final String MEDIA_ATTACHMENT_GUID_KEY = "attachment-guid";

    protected Context mContext = null;

    public Generator(Context context)
    {
        this.mContext = context.getApplicationContext();
    }

    public static void start(Context context)
    {
        // Do nothing - override in subclasses...
    }

    public static void stop(Context context)
    {
        // Do nothing - override in subclasses.
    }

    public static boolean isEnabled(Context context)
    {
        return false;
    }

    public static boolean isRunning(Context context)
    {
        return false;
    }

    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_generic, parent, false);
    }

    public static void bindViewHolder(DataPointViewHolder holder, Bundle dataPoint) {
        String identifier = dataPoint.getBundle(Generator.PDK_METADATA).getString(Generator.IDENTIFIER);

        TextView generatorLabel = (TextView) holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(identifier);
    }

    public static String formatTimestamp(Context context, double timestamp) {
        timestamp *= 1000;

        Calendar tsCalendar = Calendar.getInstance();
        tsCalendar.setTimeInMillis((long) timestamp);

        Calendar now = Calendar.getInstance();

        Date tsDate = tsCalendar.getTime();

        String time = android.text.format.DateFormat.getTimeFormat(context).format(tsDate);

        if (tsCalendar.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)) {
            return time;
        }

        String date = android.text.format.DateFormat.getMediumDateFormat(context).format(tsDate);

        return context.getString(R.string.format_full_timestamp_pdk, date, time);
    }
}
