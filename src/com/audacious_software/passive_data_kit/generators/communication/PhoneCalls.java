package com.audacious_software.passive_data_kit.generators.communication;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.utils.ViewPortHandler;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PhoneCalls extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-phone-calls";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String SAMPLE_INTERVAL = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.SAMPLE_INTERVAL";
    private static final long SAMPLE_INTERVAL_DEFAULT = 30000; // 300000;

    private static final String LAST_INCOMING_COUNT = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_INCOMING_COUNT";
    private static final String LAST_OUTGOING_COUNT = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_OUTGOING_COUNT";
    private static final String LAST_MISSED_COUNT = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_MISSED_COUNT";
    private static final String LAST_TOTAL_COUNT = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_TOTAL_COUNT";

    private static final String LAST_CALL_TIMESTAMP = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_CALL_TIMESTAMP";
    private static final String LAST_CALL_DURATION = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_CALL_DURATION";
    private static final String LAST_CALL_TYPE = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_CALL_TYPE";

    private static final String LAST_SAMPLE = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.LAST_SAMPLE";
    private static final long LAST_SAMPLE_DEFAULT = 0;

    private static final String CALL_DATE_KEY = "call_timestamp";
    private static final String CALL_DURATION_KEY = "duration";
    private static final String CALL_IS_NEW_KEY = "is_new";
    private static final String CALL_NUMBER_KEY = "number";
    private static final String CALL_POST_DIAL_DIGITS_KEY = "post_dial_digits";
    private static final String CALL_VIA_NUMBER_KEY = "via_number";
    private static final String CALL_PULLED_EXTERNALLY_KEY = "pulled_externally";
    private static final String CALL_COUNTRY_ISO_KEY = "country_code";
    private static final String CALL_GEOCODED_LOCATION_KEY = "geocoded_location";
    private static final String CALL_VIDEO_KEY = "included_video";
    private static final String CALL_DATA_USAGE_KEY = "data_usage";
    private static final String CALL_PRESENTATION_KEY = "presentation";
    private static final String CALL_IS_READ_KEY = "is_read";
    private static final String CALL_TYPE_KEY = "type";

    private static final String CALL_TYPE_REJECTED = "rejected";
    private static final String CALL_TYPE_BLOCKED = "blocked";
    private static final String CALL_TYPE_VOICEMAIL = "voicemail";
    private static final String CALL_TYPE_OUTGOING = "outgoing";
    private static final String CALL_TYPE_INCOMING = "incoming";
    private static final String CALL_TYPE_MISSED = "missed";
    private static final String CALL_TYPE_ANSWERED_EXTERNALLY = "answered_externally";
    private static final String CALL_TYPE_UNKNOWN = "unknown";

    private static final String CALL_PRESENTATION_ALLOWED = "allowed";
    private static final String CALL_PRESENTATION_RESTRICTED = "restricted";
    private static final String CALL_PRESENTATION_PAYPHONE = "payphone";
    private static final String CALL_PRESENTATION_UNKNOWN = "unknown";

    private static PhoneCalls sInstance = null;

    private Handler mHandler = null;

    public static PhoneCalls getInstance(Context context) {
        if (PhoneCalls.sInstance == null) {
            PhoneCalls.sInstance = new PhoneCalls(context.getApplicationContext());
        }

        return PhoneCalls.sInstance;
    }

    public PhoneCalls(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        PhoneCalls.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        Log.e("PDK", "START PHONE CALL GENERATOR");

        final PhoneCalls me = this;

        if (this.mHandler != null) {
            this.mHandler.getLooper().quitSafely();

            this.mHandler = null;
        }

        final Runnable checkLogs = new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                SharedPreferences.Editor e = prefs.edit();

                boolean approved = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED){
                        approved = true;
                    }
                } else {
                    approved = true;
                }

                Log.e("PDK", "TODO: Fetch Call Logs...");

                long now = System.currentTimeMillis();

                if (approved) {
                    long totalIncoming = prefs.getLong(PhoneCalls.LAST_INCOMING_COUNT, 0);
                    long totalOutgoing = prefs.getLong(PhoneCalls.LAST_OUTGOING_COUNT, 0);
                    long totalMissed = prefs.getLong(PhoneCalls.LAST_MISSED_COUNT, 0);
                    long total = prefs.getLong(PhoneCalls.LAST_TOTAL_COUNT, 0);

                    long lastSample = prefs.getLong(PhoneCalls.LAST_SAMPLE, PhoneCalls.LAST_SAMPLE_DEFAULT);

                    String where = CallLog.Calls.DATE + " > ?";
                    String[] args = {"" + lastSample};

                    long latestCallTimestamp = -1;
                    String latestCallType = null;
                    long latestCallDuration = -1;

                    Cursor c = me.mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, where, args, CallLog.Calls.DATE);

                    while (c.moveToNext()) {
                        Bundle bundle = new Bundle();

                        bundle.putLong(PhoneCalls.CALL_DATE_KEY, c.getLong(c.getColumnIndex(CallLog.Calls.DATE)));
                        bundle.putLong(PhoneCalls.CALL_DURATION_KEY, c.getLong(c.getColumnIndex(CallLog.Calls.DURATION)));
                        bundle.putString(PhoneCalls.CALL_NUMBER_KEY, c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)));
                        bundle.putBoolean(PhoneCalls.CALL_IS_NEW_KEY, (c.getInt(c.getColumnIndex(CallLog.Calls.NEW)) != 0));

                        int features = 0;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            features = c.getInt(c.getColumnIndex(CallLog.Calls.FEATURES));
                        }

                        int typeInt = c.getInt(c.getColumnIndex(CallLog.Calls.TYPE));
                        String type = PhoneCalls.CALL_TYPE_UNKNOWN;

                        switch(Build.VERSION.SDK_INT) {
                            case 25:
                                if (typeInt == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_ANSWERED_EXTERNALLY;
                                }

                                bundle.putBoolean(PhoneCalls.CALL_PULLED_EXTERNALLY_KEY, ((features & CallLog.Calls.FEATURES_PULLED_EXTERNALLY) == CallLog.Calls.FEATURES_PULLED_EXTERNALLY));
                            case 24:
                                if (typeInt == CallLog.Calls.REJECTED_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_REJECTED;
                                } else if (typeInt == CallLog.Calls.BLOCKED_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_BLOCKED;
                                }

                                bundle.putString(PhoneCalls.CALL_POST_DIAL_DIGITS_KEY, c.getString(c.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)));
                                bundle.putString(PhoneCalls.CALL_VIA_NUMBER_KEY, c.getString(c.getColumnIndex(CallLog.Calls.VIA_NUMBER)));
                            case 21:
                                if (typeInt == CallLog.Calls.VOICEMAIL_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_VOICEMAIL;
                                }

                                bundle.putString(PhoneCalls.CALL_COUNTRY_ISO_KEY, c.getString(c.getColumnIndex(CallLog.Calls.COUNTRY_ISO)));
                                bundle.putLong(PhoneCalls.CALL_DATA_USAGE_KEY, c.getLong(c.getColumnIndex(CallLog.Calls.DATA_USAGE)));
                                bundle.putString(PhoneCalls.CALL_GEOCODED_LOCATION_KEY, c.getString(c.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)));

                                bundle.putBoolean(PhoneCalls.CALL_VIDEO_KEY, ((features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO));

//                                bundle.putString(PhoneCalls.CALL_TRANSCRIPTION_KEY, c.getString(c.getColumnIndex(CallLog.Calls.TRANSCRIPTION)));
                            case 19:
                                switch (c.getInt(c.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION))) {
                                    case CallLog.Calls.PRESENTATION_ALLOWED:
                                        bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_ALLOWED);
                                        break;
                                    case CallLog.Calls.PRESENTATION_RESTRICTED:
                                        bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_RESTRICTED);
                                        break;
                                    case CallLog.Calls.PRESENTATION_PAYPHONE:
                                        bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_PAYPHONE);
                                        break;
                                    case CallLog.Calls.PRESENTATION_UNKNOWN:
                                        bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_UNKNOWN);
                                        break;
                                }
                            case 14:
                                bundle.putBoolean(PhoneCalls.CALL_IS_READ_KEY, (c.getInt(c.getColumnIndex(CallLog.Calls.IS_READ)) != 0));
                            case 1:
                                if (typeInt == CallLog.Calls.INCOMING_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_INCOMING;
                                    totalIncoming += 1;
                                } else if (typeInt == CallLog.Calls.OUTGOING_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_OUTGOING;
                                    totalOutgoing += 1;
                                } else if (typeInt == CallLog.Calls.MISSED_TYPE) {
                                    type = PhoneCalls.CALL_TYPE_MISSED;
                                    totalMissed += 1;
                                }
                        }

                        bundle.putString(PhoneCalls.CALL_TYPE_KEY, type);

                        if (bundle.getLong(PhoneCalls.CALL_DATE_KEY, 0) > latestCallTimestamp) {
                            latestCallTimestamp = bundle.getLong(PhoneCalls.CALL_DATE_KEY, 0);
                            latestCallDuration = bundle.getLong(PhoneCalls.CALL_DURATION_KEY, 0);
                            latestCallType = type;
                        }

                        Log.e("PDK", "------");
                        for (int i = 0; i < c.getColumnCount(); i++) {
                            Log.e("PDK", "CALL LOG: " + c.getColumnName(i) + " --> " + c.getString(i));
                        }

                        String[] sensitiveFields = {
                                PhoneCalls.CALL_NUMBER_KEY,
                                PhoneCalls.CALL_POST_DIAL_DIGITS_KEY,
                                PhoneCalls.CALL_VIA_NUMBER_KEY
                        };

                        for (String field : sensitiveFields) {
                            if (bundle.containsKey(field)) {
                                bundle.putString(field, new String(Hex.encodeHex(DigestUtils.sha256(bundle.getString(field)))));
                            }
                        }

                        Generators.getInstance(me.mContext).transmitData(PhoneCalls.GENERATOR_IDENTIFIER, bundle);
                    }

                    Log.e("PDK", "------");

                    total += c.getCount();

                    c.close();

                    if (latestCallType != null) {
                        e.putLong(PhoneCalls.LAST_CALL_TIMESTAMP, latestCallTimestamp);
                        e.putLong(PhoneCalls.LAST_CALL_DURATION, latestCallDuration);
                        e.putString(PhoneCalls.LAST_CALL_TYPE, latestCallType);
                    }

                    e.putLong(PhoneCalls.LAST_INCOMING_COUNT, totalIncoming);
                    e.putLong(PhoneCalls.LAST_OUTGOING_COUNT, totalOutgoing);
                    e.putLong(PhoneCalls.LAST_MISSED_COUNT, totalMissed);
                    e.putLong(PhoneCalls.LAST_TOTAL_COUNT, total);

                    e.putLong(PhoneCalls.LAST_SAMPLE, now);
                    e.apply();
                }

                long sampleInterval = prefs.getLong(PhoneCalls.SAMPLE_INTERVAL, PhoneCalls.SAMPLE_INTERVAL_DEFAULT);

                if (me.mHandler != null) {
                    me.mHandler.postDelayed(this, sampleInterval);
                }
            }
        };

        Runnable r = new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                me.mHandler = new Handler();

                Looper.loop();
            }
        };

        Thread t = new Thread(r);
        t.start();

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        me.mHandler.post(checkLogs);

        Generators.getInstance(this.mContext).registerCustomViewClass(PhoneCalls.GENERATOR_IDENTIFIER, PhoneCalls.class);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(PhoneCalls.ENABLED, PhoneCalls.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (PhoneCalls.sInstance == null) {
            return false;
        }

        return PhoneCalls.sInstance.mHandler != null;
    }

    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED){
                final Handler handler = new Handler(Looper.getMainLooper());

                actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_call_log_permission_required), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Intent intent = new Intent(context, RequestPermissionActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra(RequestPermissionActivity.PERMISSION, Manifest.permission.READ_CALL_LOG);

                                context.startActivity(intent);
                            }
                        });
                    }
                }));
            }
        }

        return actions;
    }

    public static void bindViewHolder(DataPointViewHolder holder, final Bundle dataPoint) {
       final Context context = holder.itemView.getContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        double timestamp = dataPoint.getBundle(Generator.PDK_METADATA).getDouble(Generator.TIMESTAMP);

        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        dateLabel.setText(Generator.formatTimestamp(context, timestamp));

        PieChart pieChart = (PieChart) holder.itemView.findViewById(R.id.chart_phone_calls);
        pieChart.getLegend().setEnabled(false);

        pieChart.setEntryLabelColor(android.R.color.transparent);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(false);

        long totalIncoming = prefs.getLong(PhoneCalls.LAST_INCOMING_COUNT, 0);
        long totalOutgoing = prefs.getLong(PhoneCalls.LAST_OUTGOING_COUNT, 0);
        long totalMissed = prefs.getLong(PhoneCalls.LAST_MISSED_COUNT, 0);
        long total = prefs.getLong(PhoneCalls.LAST_TOTAL_COUNT, 0);

        List<PieEntry> entries = new ArrayList<>();

        if (totalIncoming > 0) {
            entries.add(new PieEntry(totalIncoming, context.getString(R.string.generator_phone_calls_incoming_label)));
        }

        if (totalOutgoing > 0) {
            entries.add(new PieEntry(totalOutgoing, context.getString(R.string.generator_phone_calls_outgoing_label)));
        }

        if (totalMissed > 0) {
            entries.add(new PieEntry(totalMissed, context.getString(R.string.generator_phone_calls_missed_label)));
        }

        long other = total - (totalIncoming + totalOutgoing + totalMissed);

        if (other > 0) {
            entries.add(new PieEntry(other, context.getString(R.string.generator_phone_calls_other_label)));
        }

        PieDataSet set = new PieDataSet(entries, " ");

        int[] colors = {
                R.color.generator_phone_call_incoming,
                R.color.generator_phone_call_outgoing,
                R.color.generator_phone_call_missed,
                R.color.generator_phone_call_other
        };

        set.setColors(colors, context);

        PieData data = new PieData(set);
        data.setValueTextSize(14);
        data.setValueTypeface(Typeface.DEFAULT_BOLD);
        data.setValueTextColor(0xffffffff);

        data.setValueFormatter(new IValueFormatter() {
            @Override
            public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                return "" + ((Float) value).intValue();
            }
        });

        pieChart.setData(data);
        pieChart.invalidate();

        long latest = prefs.getLong(PhoneCalls.LAST_CALL_TIMESTAMP, 0);
        long duration = prefs.getLong(PhoneCalls.LAST_CALL_DURATION, 0);
        String direction = prefs.getString(PhoneCalls.LAST_CALL_TYPE, "");

        TextView latestField = (TextView) holder.itemView.findViewById(R.id.field_latest_call);
        TextView durationField = (TextView) holder.itemView.findViewById(R.id.field_duration);
        TextView directionField = (TextView) holder.itemView.findViewById(R.id.field_direction);

        Date lateDate = new Date(latest);
        String day = android.text.format.DateFormat.getMediumDateFormat(context).format(lateDate);
        String time = android.text.format.DateFormat.getTimeFormat(context).format(lateDate);

        latestField.setText(context.getString(R.string.format_full_timestamp_pdk, day, time));
        durationField.setText(context.getString(R.string.generator_phone_calls_duration_format, ((float) duration) / 60));
        directionField.setText(direction);

        dateLabel.setText(Generator.formatTimestamp(context, latest / 1000));
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_phone_calls, parent, false);
    }

    public static void broadcastLatestDataPoint(Context context) {
        Generators.getInstance(context).transmitData(PhoneCalls.GENERATOR_IDENTIFIER, new Bundle());
    }

}
