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

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
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
/*        final Context context = holder.itemView.getContext();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long zeroStart = cal.getTimeInMillis();
        cal.add(Calendar.DATE, -1);

        LinearLayout zeroTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_zero_value);

        AppEvents.populateTimeline(context, zeroTimeline, zeroStart);

        long oneStart = cal.getTimeInMillis();
        cal.add(Calendar.DATE, -1);

        LinearLayout oneTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_one_value);

        AppEvents.populateTimeline(context, oneTimeline, oneStart);

        long twoStart = cal.getTimeInMillis();

        LinearLayout twoTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_two_value);

        AppEvents.populateTimeline(context, twoTimeline, twoStart);

        AppEvents generator = AppEvents.getInstance(context);

        Cursor c = generator.mDatabase.query(AppEvents.TABLE_HISTORY, null, null, null, null, null, AppEvents.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(AppEvents.HISTORY_OBSERVED)) / 1000;

            dateLabel.setText(Generator.formatTimestamp(context, timestamp));

            cal = Calendar.getInstance();
            DateFormat format = android.text.format.DateFormat.getDateFormat(context);

            TextView zeroDayLabel = (TextView) holder.itemView.findViewById(R.id.day_zero_label);
            zeroDayLabel.setText(format.format(cal.getTime()));

            cal.add(Calendar.DATE, -1);

            TextView oneDayLabel = (TextView) holder.itemView.findViewById(R.id.day_one_label);
            oneDayLabel.setText(format.format(cal.getTime()));

            cal.add(Calendar.DATE, -1);

            TextView twoDayLabel = (TextView) holder.itemView.findViewById(R.id.day_two_label);
            twoDayLabel.setText(format.format(cal.getTime()));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.close();
        */
    }

    /*
    private static void populateTimeline(Context context, LinearLayout timeline, long start) {
        timeline.removeAllViews();

        AppEvents generator = AppEvents.getInstance(context);

        long end = start + (24 * 60 * 60 * 1000);

        String where = AppEvents.HISTORY_OBSERVED + " >= ? AND " + AppEvents.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start, "" + end };

        Cursor c = generator.mDatabase.query(AppEvents.TABLE_HISTORY, null, where, args, null, null, AppEvents.HISTORY_OBSERVED);

        ArrayList<String> activeStates = new ArrayList<>();
        ArrayList<Long> activeTimestamps = new ArrayList<>();

        while (c.moveToNext()) {
            long timestamp = c.getLong(c.getColumnIndex(AppEvents.HISTORY_OBSERVED));

            activeTimestamps.add(timestamp);

            String state = c.getString(c.getColumnIndex(AppEvents.HISTORY_STATE));
            activeStates.add(state);
        }

        c.close();

        String lastState = AppEvents.STATE_UNKNOWN;

        String lastWhere = AppEvents.HISTORY_OBSERVED + " < ?";
        String[] lastArgs = { "" + start };

        c = generator.mDatabase.query(AppEvents.TABLE_HISTORY, null, lastWhere, lastArgs, null, null, AppEvents.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            lastState = c.getString(c.getColumnIndex(AppEvents.HISTORY_STATE));
        }

        if (activeStates.size() > 0) {
            long firstTimestamp = activeTimestamps.get(0);
            long firstState = activeTimestamps.get(0);

            View startView = new View(context);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                if (AppEvents.STATE_UNKNOWN.equals(lastState)) {

                } else if (AppEvents.STATE_ON.equals(lastState)) {
                    startView.setBackgroundColor(0xff4CAF50);
                } else if (AppEvents.STATE_OFF.equals(lastState)) {
                    startView.setBackgroundColor(0xff263238);
                } else if (AppEvents.STATE_DOZE.equals(lastState)) {
                    startView.setBackgroundColor(0xff1b5e20);
                } else if (AppEvents.STATE_DOZE_SUSPEND.equals(lastState)) {
                    startView.setBackgroundColor(0xff1b5e20);
                }
            } else {
                if (AppEvents.STATE_UNKNOWN.equals(lastState)) {

                } else if (AppEvents.STATE_ON.equals(lastState)) {
                    startView.setBackgroundColor(0xff4CAF50);
                } else if (AppEvents.STATE_OFF.equals(lastState)) {
                    startView.setBackgroundColor(0xff263238);
                }
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, firstTimestamp - start);
            startView.setLayoutParams(params);

            timeline.addView(startView);

            long now = System.currentTimeMillis();

            if (activeStates.size() == 1) {
                View v = new View(context);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    if (AppEvents.STATE_ON.equals(firstState)) {
                        v.setBackgroundColor(0xff4CAF50);
                    } else if (AppEvents.STATE_OFF.equals(firstState)) {
                        v.setBackgroundColor(0xff263238);
                    } else if (AppEvents.STATE_DOZE.equals(firstState)) {
                        v.setBackgroundColor(0xff3f51b5);
                    } else if (AppEvents.STATE_DOZE_SUSPEND.equals(firstState)) {
                        v.setBackgroundColor(0xff3f51b5);
                    }
                } else {
                    if (AppEvents.STATE_ON.equals(firstState)) {
                        v.setBackgroundColor(0xff4CAF50);
                    } else if (AppEvents.STATE_OFF.equals(firstState)) {
                        v.setBackgroundColor(0xff263238);
                    }
                }

                if (end > System.currentTimeMillis()) {
                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, now - firstTimestamp);
                    v.setLayoutParams(params);
                } else {
                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, end - firstTimestamp);
                    v.setLayoutParams(params);
                }

                timeline.addView(v);
            } else {
                for (int i = 1; i < activeStates.size(); i++) {
                    long currentTimestamp = activeTimestamps.get(i);

                    long priorTimestamp = activeTimestamps.get(i - 1);
                    String priorState = activeStates.get(i - 1);

                    View v = new View(context);

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        if (AppEvents.STATE_ON.equals(priorState)) {
                            v.setBackgroundColor(0xff4CAF50);
                        } else if (AppEvents.STATE_OFF.equals(priorState)) {
                            v.setBackgroundColor(0xff263238);
                        } else if (AppEvents.STATE_DOZE.equals(priorState)) {
                            v.setBackgroundColor(0xff3f51b5);
                        } else if (AppEvents.STATE_DOZE_SUSPEND.equals(priorState)) {
                            v.setBackgroundColor(0xff3f51b5);
                        }
                    } else {
                        if (AppEvents.STATE_ON.equals(priorState)) {
                            v.setBackgroundColor(0xff4CAF50);
                        } else if (AppEvents.STATE_OFF.equals(priorState)) {
                            v.setBackgroundColor(0xff263238);
                        }
                    }

                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, currentTimestamp - priorTimestamp);
                    v.setLayoutParams(params);

                    timeline.addView(v);
                }

                long finalTimestamp = activeTimestamps.get(activeTimestamps.size() - 1);
                String finalState = activeStates.get(activeStates.size() - 1);

                View v = new View(context);

                if (AppEvents.STATE_ON.equals(finalState)) {
                    v.setBackgroundColor(0xff4CAF50);
                } else if (AppEvents.STATE_OFF.equals(finalState)) {
                    v.setBackgroundColor(0xff263238);
                } else if (AppEvents.STATE_DOZE.equals(finalState)) {
                    v.setBackgroundColor(0xff3f51b5);
                } else if (AppEvents.STATE_DOZE_SUSPEND.equals(finalState)) {
                    v.setBackgroundColor(0xff3f51b5);
                }

                if (end > now) {
                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, now - finalTimestamp);
                    v.setLayoutParams(params);
                } else {
                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, end - finalTimestamp);
                    v.setLayoutParams(params);
                }

                timeline.addView(v);
            }

            if (end > now) {
                View v = new View(context);

                params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, end - now);
                v.setLayoutParams(params);

                timeline.addView(v);
            }
        } else {

        }
    }

*/
    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_generic, parent, false);
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
