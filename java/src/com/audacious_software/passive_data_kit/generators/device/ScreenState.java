package com.audacious_software.passive_data_kit.generators.device;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.GeneratorViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import humanize.Humanize;

@SuppressWarnings("SimplifiableIfStatement")
public class ScreenState extends Generator{
    private static final String GENERATOR_IDENTIFIER = "pdk-screen-state";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.ScreenState.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.ScreenState.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    @SuppressWarnings("WeakerAccess")
    public static final String STATE_DOZE = "doze";

    @SuppressWarnings("WeakerAccess")
    public static final String STATE_DOZE_SUSPEND = "doze_suspend";

    @SuppressWarnings("WeakerAccess")
    public static final String STATE_ON = "on";

    @SuppressWarnings("WeakerAccess")
    public static final String STATE_OFF = "off";

    @SuppressWarnings("WeakerAccess")
    public static final String STATE_UNKNOWN = "unknown";

    private static final String DATABASE_PATH = "pdk-screen-state.sqlite";
    private static final int DATABASE_VERSION = 2;

    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_OBSERVED = "observed";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_STATE = "state";
    private static final String TABLE_HISTORY = "history";

    private static ScreenState sInstance = null;

    private BroadcastReceiver mReceiver = null;

    private SQLiteDatabase mDatabase = null;

    private long mLatestTimestamp = -1;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return ScreenState.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static synchronized ScreenState getInstance(Context context) {
        if (ScreenState.sInstance == null) {
            ScreenState.sInstance = new ScreenState(context.getApplicationContext());
        }

        return ScreenState.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public ScreenState(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        ScreenState.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final ScreenState me = this;

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                Thread update = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long now = System.currentTimeMillis();

                        me.mLatestTimestamp = now;

                        ContentValues values = new ContentValues();
                        values.put(ScreenState.HISTORY_OBSERVED, now);

                        Bundle update = new Bundle();
                        update.putLong(ScreenState.HISTORY_OBSERVED, now);

                        WindowManager window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                        Display display = window.getDefaultDisplay();

                        final String action = intent.getAction();

                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                            switch (display.getState()) {
                                case Display.STATE_DOZE:
                                    values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_DOZE);
                                    break;
                                case Display.STATE_DOZE_SUSPEND:
                                    values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_DOZE_SUSPEND);
                                    break;
                                case Display.STATE_ON:
                                    values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_ON);
                                    break;
                                case Display.STATE_OFF:
                                    values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_OFF);
                                    break;
                                case Display.STATE_UNKNOWN:
                                    values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_UNKNOWN);
                                    break;
                            }
                        } else {
                            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                                values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_OFF);
                            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                                values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_ON);
                            } else {
                                values.put(ScreenState.HISTORY_STATE, ScreenState.STATE_UNKNOWN);
                            }
                        }

                        if (me.mDatabase != null) {
                            me.mDatabase.insert(ScreenState.TABLE_HISTORY, null, values);
                        }

                        update.putString(ScreenState.HISTORY_STATE, values.getAsString(ScreenState.HISTORY_STATE));

                        Generators.getInstance(context).notifyGeneratorUpdated(ScreenState.GENERATOR_IDENTIFIER, update);
                    }
                }, "pdk-screen-state-record");

                update.run();
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        this.mContext.registerReceiver(this.mReceiver, filter);

        Generators.getInstance(this.mContext).registerCustomViewClass(ScreenState.GENERATOR_IDENTIFIER, ScreenState.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, ScreenState.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_screen_state_create_history_table));
            case 1:
                this.mDatabase.execSQL("DROP TABLE history");
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_screen_state_create_history_table));
        }

        if (version != ScreenState.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, ScreenState.DATABASE_VERSION);
        }

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(ScreenState.ENABLED, ScreenState.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (ScreenState.sInstance == null) {
            return false;
        }

        return ScreenState.sInstance.mReceiver != null;
    }

    @SuppressWarnings({"unused"})
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_screen_state);
    }

    @SuppressWarnings("unused")
    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(ForegroundApplication.getGeneratorTitle(holder.itemView.getContext()));
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long zeroStart = cal.getTimeInMillis();
        cal.add(Calendar.DATE, -1);

        LinearLayout zeroTimeline = holder.itemView.findViewById(R.id.day_zero_value);

        ScreenState.populateTimeline(context, zeroTimeline, zeroStart);

        long oneStart = cal.getTimeInMillis();
        cal.add(Calendar.DATE, -1);

        LinearLayout oneTimeline = holder.itemView.findViewById(R.id.day_one_value);

        ScreenState.populateTimeline(context, oneTimeline, oneStart);

        long twoStart = cal.getTimeInMillis();

        LinearLayout twoTimeline = holder.itemView.findViewById(R.id.day_two_value);

        ScreenState.populateTimeline(context, twoTimeline, twoStart);

        ScreenState generator = ScreenState.getInstance(context);

        Cursor c = generator.mDatabase.query(ScreenState.TABLE_HISTORY, null, null, null, null, null, ScreenState.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(ScreenState.HISTORY_OBSERVED)) / 1000;

            long storage = generator.storageUsed();

            String storageDesc = context.getString(R.string.label_storage_unknown);

            if (storage >= 0) {
                storageDesc = Humanize.binaryPrefix(storage);
            }

            dateLabel.setText(context.getString(R.string.label_storage_date_card, Generator.formatTimestamp(context, timestamp), storageDesc));

            cal = Calendar.getInstance();
            DateFormat format = android.text.format.DateFormat.getDateFormat(context);

            TextView zeroDayLabel = holder.itemView.findViewById(R.id.day_zero_label);
            zeroDayLabel.setText(format.format(cal.getTime()));

            cal.add(Calendar.DATE, -1);

            TextView oneDayLabel = holder.itemView.findViewById(R.id.day_one_label);
            oneDayLabel.setText(format.format(cal.getTime()));

            cal.add(Calendar.DATE, -1);

            TextView twoDayLabel = holder.itemView.findViewById(R.id.day_two_label);
            twoDayLabel.setText(format.format(cal.getTime()));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.close();
    }

    private static void populateTimeline(Context context, LinearLayout timeline, long start) {
        timeline.removeAllViews();

        ScreenState generator = ScreenState.getInstance(context);

        long end = start + (24 * 60 * 60 * 1000);

        String where = ScreenState.HISTORY_OBSERVED + " >= ? AND " + ScreenState.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start, "" + end };

        Cursor c = generator.mDatabase.query(ScreenState.TABLE_HISTORY, null, where, args, null, null, ScreenState.HISTORY_OBSERVED);

        ArrayList<String> activeStates = new ArrayList<>();
        ArrayList<Long> activeTimestamps = new ArrayList<>();

        while (c.moveToNext()) {
            long timestamp = c.getLong(c.getColumnIndex(ScreenState.HISTORY_OBSERVED));

            activeTimestamps.add(timestamp);

            String state = c.getString(c.getColumnIndex(ScreenState.HISTORY_STATE));
            activeStates.add(state);
        }

        c.close();

        String lastState = ScreenState.STATE_UNKNOWN;

        String lastWhere = ScreenState.HISTORY_OBSERVED + " < ?";
        String[] lastArgs = { "" + start };

        c = generator.mDatabase.query(ScreenState.TABLE_HISTORY, null, lastWhere, lastArgs, null, null, ScreenState.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            lastState = c.getString(c.getColumnIndex(ScreenState.HISTORY_STATE));
        }

        c.close();

        if (activeStates.size() > 0) {
            long firstTimestamp = activeTimestamps.get(0);
            String firstState = activeStates.get(0);

            View startView = new View(context);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                if (ScreenState.STATE_ON.equals(lastState)) {
                    startView.setBackgroundColor(0xff4CAF50);
                } else if (ScreenState.STATE_OFF.equals(lastState)) {
                    startView.setBackgroundColor(0xff263238);
                } else if (ScreenState.STATE_DOZE.equals(lastState)) {
                    startView.setBackgroundColor(0xff1b5e20);
                } else if (ScreenState.STATE_DOZE_SUSPEND.equals(lastState)) {
                    startView.setBackgroundColor(0xff1b5e20);
                }
            } else {
                if (ScreenState.STATE_ON.equals(lastState)) {
                    startView.setBackgroundColor(0xff4CAF50);
                } else if (ScreenState.STATE_OFF.equals(lastState)) {
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
                    if (ScreenState.STATE_ON.equals(firstState)) {
                        v.setBackgroundColor(0xff4CAF50);
                    } else if (ScreenState.STATE_OFF.equals(firstState)) {
                        v.setBackgroundColor(0xff263238);
                    } else if (ScreenState.STATE_DOZE.equals(firstState)) {
                        v.setBackgroundColor(0xff3f51b5);
                    } else if (ScreenState.STATE_DOZE_SUSPEND.equals(firstState)) {
                        v.setBackgroundColor(0xff3f51b5);
                    }
                } else {
                    if (ScreenState.STATE_ON.equals(firstState)) {
                        v.setBackgroundColor(0xff4CAF50);
                    } else if (ScreenState.STATE_OFF.equals(firstState)) {
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
                        if (ScreenState.STATE_ON.equals(priorState)) {
                            v.setBackgroundColor(0xff4CAF50);
                        } else if (ScreenState.STATE_OFF.equals(priorState)) {
                            v.setBackgroundColor(0xff263238);
                        } else if (ScreenState.STATE_DOZE.equals(priorState)) {
                            v.setBackgroundColor(0xff3f51b5);
                        } else if (ScreenState.STATE_DOZE_SUSPEND.equals(priorState)) {
                            v.setBackgroundColor(0xff3f51b5);
                        }
                    } else {
                        if (ScreenState.STATE_ON.equals(priorState)) {
                            v.setBackgroundColor(0xff4CAF50);
                        } else if (ScreenState.STATE_OFF.equals(priorState)) {
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

                if (ScreenState.STATE_ON.equals(finalState)) {
                    v.setBackgroundColor(0xff4CAF50);
                } else if (ScreenState.STATE_OFF.equals(finalState)) {
                    v.setBackgroundColor(0xff263238);
                } else if (ScreenState.STATE_DOZE.equals(finalState)) {
                    v.setBackgroundColor(0xff3f51b5);
                } else if (ScreenState.STATE_DOZE_SUSPEND.equals(finalState)) {
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
        }
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_screen_state, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        ScreenState me = ScreenState.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(ScreenState.TABLE_HISTORY, null, null, null, null, null, ScreenState.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(ScreenState.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(ScreenState.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(ScreenState.DATA_RETENTION_PERIOD, ScreenState.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = ScreenState.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(ScreenState.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(ScreenState.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return ScreenState.GENERATOR_IDENTIFIER;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ScreenState.ENABLED, true);
        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, ScreenState.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }
}
