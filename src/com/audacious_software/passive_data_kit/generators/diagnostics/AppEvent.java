package com.audacious_software.passive_data_kit.generators.diagnostics;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppEvent extends Generator{
    private static final String GENERATOR_IDENTIFIER = "pdk-app-event";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATABASE_PATH = "pdk-app-event.sqlite";
    private static final int DATABASE_VERSION = 1;

    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_EVENT_NAME = "event_name";
    public static final String HISTORY_EVENT_DETAILS = "event_details";
    public static final String TABLE_HISTORY = "history";

    private static AppEvent sInstance = null;

    private SQLiteDatabase mDatabase = null;

    public static AppEvent getInstance(Context context) {
        if (AppEvent.sInstance == null) {
            AppEvent.sInstance = new AppEvent(context.getApplicationContext());
        }

        return AppEvent.sInstance;
    }

    public AppEvent(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        AppEvent.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final AppEvent me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(AppEvent.GENERATOR_IDENTIFIER, AppEvent.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, AppEvent.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_app_events_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, AppEvent.DATABASE_VERSION);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(AppEvent.ENABLED, AppEvent.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        return (AppEvent.sInstance != null);
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        AppEvent generator = AppEvent.getInstance(context);

        Cursor c = generator.mDatabase.query(AppEvent.TABLE_HISTORY, null, null, null, null, null, AppEvent.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED)) / 1000;

            dateLabel.setText(Generator.formatTimestamp(context, timestamp));

            TextView eventCount = (TextView) holder.itemView.findViewById(R.id.card_app_event_count);

            eventCount.setText("TODO: EVENT VIEW - " + c.getCount());
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.close();
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_app_event, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        AppEvent me = AppEvent.getInstance(context);

        Cursor c = me.mDatabase.query(AppEvent.TABLE_HISTORY, null, null, null, null, null, AppEvent.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(AppEvent.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    public boolean logEvent(String eventName, Map<String, ? extends Object> eventDetails) {
        try {
            long now = System.currentTimeMillis();

            ContentValues values = new ContentValues();
            values.put(AppEvent.HISTORY_OBSERVED, now);
            values.put(AppEvent.HISTORY_EVENT_NAME, eventName);

            Bundle detailsBundle = new Bundle();
            JSONObject detailsJson = new JSONObject();

            for (String key : eventDetails.keySet()) {
                Object value = eventDetails.get(key);

                if (value instanceof Double) {
                    Double doubleValue = ((Double) value);

                    detailsBundle.putDouble(key, doubleValue);
                    detailsJson.put(key, doubleValue.doubleValue());
                } else if (value instanceof  Float) {
                    Float floatValue = ((Float) value);

                    detailsBundle.putDouble(key, floatValue.doubleValue());
                    detailsJson.put(key, floatValue.doubleValue());
                } else if (value instanceof  Long) {
                    Long longValue = ((Long) value);

                    detailsBundle.putLong(key, longValue.longValue());
                    detailsJson.put(key, longValue.longValue());
                } else if (value instanceof  Integer) {
                    Integer intValue = ((Integer) value);

                    detailsBundle.putLong(key, intValue.longValue());
                    detailsJson.put(key, intValue.longValue());
                } else if (value instanceof String) {
                    detailsBundle.putString(key, value.toString());
                    detailsJson.put(key, value.toString());
                } else if (value instanceof Boolean) {
                    detailsBundle.putBoolean(key, ((Boolean) value).booleanValue());
                    detailsJson.put(key, ((Boolean) value).booleanValue());
                } else {
                    detailsBundle.putString(key, "Unknown Class: " + value.getClass().getCanonicalName());
                    detailsJson.put(key, "Unknown Class: " + value.getClass().getCanonicalName());
                }
            }

            values.put(AppEvent.HISTORY_EVENT_DETAILS, detailsJson.toString(2));

            this.mDatabase.insert(AppEvent.TABLE_HISTORY, null, values);

            Bundle update = new Bundle();
            update.putLong(AppEvent.HISTORY_OBSERVED, now);
            update.putString(AppEvent.HISTORY_EVENT_NAME, values.getAsString(AppEvent.HISTORY_EVENT_NAME));
            update.putBundle(AppEvent.HISTORY_EVENT_DETAILS, detailsBundle);

            Generators.getInstance(this.mContext).notifyGeneratorUpdated(AppEvent.GENERATOR_IDENTIFIER, update);

            return true;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    public void logThrowable(Throwable t) {

    }
}
