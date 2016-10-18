package com.audacious_software.passive_data_kit.generators.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class ScreenState extends Generator{
    private static final String GENERATOR_IDENTIFIER = "pdk-screen-state";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.ScreenState.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String SCREEN_STATE_KEY = "screen_state";
    private static final String STATE_DOZE = "doze";
    private static final String STATE_DOZE_SUSPEND = "doze_suspend";
    private static final String STATE_ON = "on";
    private static final String STATE_OFF = "off";
    private static final String STATE_UNKNOWN = "unknown";
    private static final String SCREEN_HISTORY_KEY = "com.audacious_software.passive_data_kit.generators.device.ScreenState.SCREEN_HISTORY_KEY";;
    private static final String SCREEN_HISTORY_TIMESTAMP = "ts";
    private static final String SCREEN_HISTORY_STATE = "state";

    private static ScreenState sInstance = null;

    private BroadcastReceiver mReceiver = null;

    public static ScreenState getInstance(Context context) {
        if (ScreenState.sInstance == null) {
            ScreenState.sInstance = new ScreenState(context.getApplicationContext());
        }

        return ScreenState.sInstance;
    }

    public ScreenState(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        ScreenState.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = new Bundle();

                WindowManager window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                Display display = window.getDefaultDisplay();

                switch(display.getState()) {
                    case Display.STATE_DOZE:
                        bundle.putString(ScreenState.SCREEN_STATE_KEY, ScreenState.STATE_DOZE);
                        break;
                    case Display.STATE_DOZE_SUSPEND:
                        bundle.putString(ScreenState.SCREEN_STATE_KEY, ScreenState.STATE_DOZE_SUSPEND);
                        break;
                    case Display.STATE_ON:
                        bundle.putString(ScreenState.SCREEN_STATE_KEY, ScreenState.STATE_ON);
                        break;
                    case Display.STATE_OFF:
                        bundle.putString(ScreenState.SCREEN_STATE_KEY, ScreenState.STATE_OFF);
                        break;
                    case Display.STATE_UNKNOWN:
                        bundle.putString(ScreenState.SCREEN_STATE_KEY, ScreenState.STATE_UNKNOWN);
                        break;
                }

                Log.e("BB", "SCREEN STATE: " + bundle.getString(ScreenState.SCREEN_STATE_KEY));

                Generators.getInstance(context).transmitData(ScreenState.GENERATOR_IDENTIFIER, bundle);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

                try {
                    JSONArray history = new JSONArray(prefs.getString(ScreenState.SCREEN_HISTORY_KEY, "[]"));

                    JSONObject latest = new JSONObject();
                    latest.put(ScreenState.SCREEN_HISTORY_TIMESTAMP, System.currentTimeMillis());
                    latest.put(ScreenState.SCREEN_HISTORY_STATE, display.getState());

                    history.put(latest);

                    SharedPreferences.Editor e = prefs.edit();

                    e.putString(ScreenState.SCREEN_HISTORY_KEY, history.toString());
                    e.apply();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        this.mContext.registerReceiver(this.mReceiver, filter);

        Generators.getInstance(this.mContext).registerCustomViewClass(ScreenState.GENERATOR_IDENTIFIER, ScreenState.class);

        this.mReceiver.onReceive(this.mContext, null);

        Log.e("BB", "LISTENING FOR SCEEEN STATE CHANGES");
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(ScreenState.ENABLED, ScreenState.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (ScreenState.sInstance == null) {
            return false;
        }

        return ScreenState.sInstance.mReceiver != null;
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindViewHolder(DataPointViewHolder holder, final Bundle dataPoint) {
        final Context context = holder.itemView.getContext();

        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            JSONArray history = new JSONArray(prefs.getString(ScreenState.SCREEN_HISTORY_KEY, "[]"));

            Log.e("PDK", "SCREEN HISTORY: " + history.toString(2));

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long zeroStart = cal.getTimeInMillis();
            cal.add(Calendar.DATE, -1);

            LinearLayout zeroTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_zero_value);

            ScreenState.populateTimeline(context, zeroTimeline, zeroStart, history);

            long oneStart = cal.getTimeInMillis();
            cal.add(Calendar.DATE, -1);

            LinearLayout oneTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_one_value);

            ScreenState.populateTimeline(context, oneTimeline, oneStart, history);

            long twoStart = cal.getTimeInMillis();

            LinearLayout twoTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_two_value);

            ScreenState.populateTimeline(context, twoTimeline, twoStart, history);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        double timestamp = dataPoint.getBundle(Generator.PDK_METADATA).getDouble(Generator.TIMESTAMP);

        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        dateLabel.setText(Generator.formatTimestamp(context, timestamp));

        Calendar cal = Calendar.getInstance();
        DateFormat format = android.text.format.DateFormat.getDateFormat(context);

        TextView zeroDayLabel = (TextView) holder.itemView.findViewById(R.id.day_zero_label);
        zeroDayLabel.setText(format.format(cal.getTime()));

        cal.add(Calendar.DATE, -1);

        TextView oneDayLabel = (TextView) holder.itemView.findViewById(R.id.day_one_label);
        oneDayLabel.setText(format.format(cal.getTime()));

        cal.add(Calendar.DATE, -1);

        TextView twoDayLabel = (TextView) holder.itemView.findViewById(R.id.day_two_label);
        twoDayLabel.setText(format.format(cal.getTime()));
    }

    private static void populateTimeline(Context context, LinearLayout timeline, long start, JSONArray history) {
        timeline.removeAllViews();

        long end = start + (24 * 60 * 60 * 1000);

        long now = System.currentTimeMillis();

        int lastState = -1;

        ArrayList<Integer> activeStates = new ArrayList<>();
        ArrayList<Long> activeTimestamps = new ArrayList<>();

        for (int i = 0; i < history.length(); i++) {
            try {
                JSONObject point = history.getJSONObject(i);

                long timestamp = point.getLong(ScreenState.SCREEN_HISTORY_TIMESTAMP);
                int state = point.getInt(ScreenState.SCREEN_HISTORY_STATE);

                if (timestamp < start) {
                    lastState = state;
                } else if (timestamp < end) {
                    activeStates.add(state);
                    activeTimestamps.add(timestamp);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (activeStates.size() > 0) {
            long firstTimestamp = activeTimestamps.get(0);
            long firstState = activeTimestamps.get(0);

            View startView = new View(context);

            if (lastState == -1) {

            } else if (lastState != Display.STATE_OFF) {
                startView.setBackgroundColor(0xff4CAF50);
            } else {
                startView.setBackgroundColor(0xff263238);
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, firstTimestamp - start);
            startView.setLayoutParams(params);

            timeline.addView(startView);

            if (activeStates.size() == 1) {
                View v = new View(context);

                if (firstState != Display.STATE_OFF) {
                    v.setBackgroundColor(0xff4CAF50);
                } else {
                    v.setBackgroundColor(0xff263238);
                }

                if (end > now) {
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
                    long priorState = activeStates.get(i - 1);

                    View v = new View(context);

                    if (priorState != Display.STATE_OFF) {
                        v.setBackgroundColor(0xff4CAF50);
                    } else {
                        v.setBackgroundColor(0xff263238);
                    }

                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, currentTimestamp - priorTimestamp);
                    v.setLayoutParams(params);

                    timeline.addView(v);
                }

                long finalTimestamp = activeTimestamps.get(activeTimestamps.size() - 1);
                long finalState = activeStates.get(activeStates.size() - 1);

                View v = new View(context);

                if (finalState != Display.STATE_OFF) {
                    v.setBackgroundColor(0xff4CAF50);
                } else {
                    v.setBackgroundColor(0xff263238);
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

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_screen_state, parent, false);
    }

    public static void broadcastLatestDataPoint(Context context) {
        Generators.getInstance(context).transmitData(ScreenState.GENERATOR_IDENTIFIER, new Bundle());
    }
}
