package com.audacious_software.passive_data_kit.generators.communication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.PhoneUtililties;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.R;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.core.content.ContextCompat;

import humanize.Humanize;

@SuppressWarnings("SimplifiableIfStatement")
@SuppressLint("InlinedApi")
public class PhoneCalls extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-phone-calls";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String OMIT_SENSITIVE_FIELDS = "com.audacious_software.passive_data_kit.generators.communication.PhoneCalls.OMIT_SENSITIVE_FIELDS";
    private static final boolean OMIT_SENSITIVE_FIELDS_DEFAULT = false;

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

    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_DURATION = "duration";
    private static final String HISTORY_NUMBER = "number";
    private static final String HISTORY_IS_NEW = "is_new";
    private static final String HISTORY_PULLED_EXTERNALLY = "pulled_externally";
    private static final String HISTORY_POST_DIAL_DIGITS = "post_dial_digits";
    private static final String HISTORY_COUNTRY_ISO = "country_iso";
    private static final String HISTORY_DATA_USAGE = "data_usage";
    private static final String HISTORY_GEOCODED_LOCATION = "geocoded_location";
    private static final String HISTORY_VIDEO = "is_video";
    private static final String HISTORY_VIA_NUMBER = "via_number";
    private static final String HISTORY_PRESENTATION = "presentation";
    private static final String HISTORY_IS_READ = "is_read";
    private static final String HISTORY_CALL_TYPE = "call_type";

    private static PhoneCalls sInstance = null;

    private Handler mHandler = null;
    private Context mContext = null;

    private static final String DATABASE_PATH = "pdk-phone-calls.sqlite";
    private static final int DATABASE_VERSION = 3;

    private SQLiteDatabase mDatabase = null;
    private long mSampleInterval = 60000;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return PhoneCalls.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static PhoneCalls getInstance(Context context) {
        if (PhoneCalls.sInstance == null) {
            PhoneCalls.sInstance = new PhoneCalls(context.getApplicationContext());
        }

        return PhoneCalls.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public PhoneCalls(Context context) {
        super(context);

        this.mContext = context.getApplicationContext();
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        PhoneCalls.getInstance(context).startGenerator();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void startGenerator() {
        final PhoneCalls me = this;

        if (this.mHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                this.mHandler.getLooper().quitSafely();
            } else {
                this.mHandler.getLooper().quit();
            }

            this.mHandler = null;
        }

        final Runnable checkLogs = new Runnable() {
            @Override
            public void run() {
                boolean approved = false;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                boolean omitSensitiveFields = prefs.getBoolean(PhoneCalls.OMIT_SENSITIVE_FIELDS, PhoneCalls.OMIT_SENSITIVE_FIELDS_DEFAULT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED){
                        approved = true;
                    }
                } else {
                    approved = true;
                }

                if (approved) {
                    long lastObserved = 0;

                    Cursor lastCursor = me.mDatabase.query(PhoneCalls.TABLE_HISTORY, null, null, null, null, null, PhoneCalls.HISTORY_OBSERVED + " DESC");

                    if (lastCursor.moveToNext()) {
                        lastObserved = lastCursor.getLong(lastCursor.getColumnIndex(PhoneCalls.HISTORY_OBSERVED));
                    }

                    lastCursor.close();

                    String where = CallLog.Calls.DATE + " > ?";
                    String[] args = {"" + lastObserved};

                    try {
                        @SuppressLint("MissingPermission") Cursor c = me.mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, where, args, CallLog.Calls.DATE);

                        if (c != null) {
                            while (c.moveToNext()) {
                                ContentValues values = new ContentValues();
                                values.put(PhoneCalls.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(CallLog.Calls.DATE)));
                                values.put(PhoneCalls.HISTORY_DURATION, c.getLong(c.getColumnIndex(CallLog.Calls.DURATION)));
                                values.put(PhoneCalls.HISTORY_NUMBER, PhoneUtililties.e164PhoneNumber(c.getString(c.getColumnIndex(CallLog.Calls.NUMBER))));
                                values.put(PhoneCalls.HISTORY_IS_NEW, (c.getInt(c.getColumnIndex(CallLog.Calls.NEW)) != 0));

                                Bundle bundle = new Bundle();
                                bundle.putLong(PhoneCalls.CALL_DATE_KEY, c.getLong(c.getColumnIndex(CallLog.Calls.DATE)));
                                bundle.putLong(PhoneCalls.CALL_DURATION_KEY, c.getLong(c.getColumnIndex(CallLog.Calls.DURATION)));
                                bundle.putString(PhoneCalls.CALL_NUMBER_KEY, PhoneUtililties.e164PhoneNumber(c.getString(c.getColumnIndex(CallLog.Calls.NUMBER))));
                                bundle.putBoolean(PhoneCalls.CALL_IS_NEW_KEY, (c.getInt(c.getColumnIndex(CallLog.Calls.NEW)) != 0));

                                int features = 0;

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    features = c.getInt(c.getColumnIndex(CallLog.Calls.FEATURES));
                                }

                                int typeInt = c.getInt(c.getColumnIndex(CallLog.Calls.TYPE));

                                String type = PhoneCalls.CALL_TYPE_UNKNOWN;

                                int sdkInt = Build.VERSION.SDK_INT;

                                if (sdkInt > 25) {
                                    sdkInt = 25;
                                } else if (sdkInt < 24 && sdkInt > 21) {
                                    sdkInt = 21;
                                } else if (sdkInt == 20) {
                                    sdkInt = 19;
                                } else if (sdkInt < 19 && sdkInt > 14) {
                                    sdkInt = 14;
                                } else if (sdkInt < 14 && sdkInt > 1) {
                                    sdkInt = 1;
                                }

                                switch (sdkInt) {
                                    case 25:
                                        if (typeInt == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_ANSWERED_EXTERNALLY;
                                        }

                                        bundle.putBoolean(PhoneCalls.CALL_PULLED_EXTERNALLY_KEY, ((features & CallLog.Calls.FEATURES_PULLED_EXTERNALLY) == CallLog.Calls.FEATURES_PULLED_EXTERNALLY));

                                        values.put(PhoneCalls.HISTORY_PULLED_EXTERNALLY, ((features & CallLog.Calls.FEATURES_PULLED_EXTERNALLY) == CallLog.Calls.FEATURES_PULLED_EXTERNALLY));
                                    case 24:
                                        if (typeInt == CallLog.Calls.REJECTED_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_REJECTED;
                                        } else if (typeInt == CallLog.Calls.BLOCKED_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_BLOCKED;
                                        }

                                        bundle.putString(PhoneCalls.CALL_POST_DIAL_DIGITS_KEY, c.getString(c.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)));
                                        bundle.putString(PhoneCalls.CALL_VIA_NUMBER_KEY, c.getString(c.getColumnIndex(CallLog.Calls.VIA_NUMBER)));

                                        values.put(PhoneCalls.HISTORY_POST_DIAL_DIGITS, c.getString(c.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)));
                                        values.put(PhoneCalls.HISTORY_VIA_NUMBER, c.getString(c.getColumnIndex(CallLog.Calls.VIA_NUMBER)));
                                    case 21:
                                        if (typeInt == CallLog.Calls.VOICEMAIL_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_VOICEMAIL;
                                        }

                                        bundle.putString(PhoneCalls.CALL_COUNTRY_ISO_KEY, c.getString(c.getColumnIndex(CallLog.Calls.COUNTRY_ISO)));
                                        bundle.putLong(PhoneCalls.CALL_DATA_USAGE_KEY, c.getLong(c.getColumnIndex(CallLog.Calls.DATA_USAGE)));
                                        bundle.putString(PhoneCalls.CALL_GEOCODED_LOCATION_KEY, c.getString(c.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)));
                                        bundle.putBoolean(PhoneCalls.CALL_VIDEO_KEY, ((features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO));

                                        values.put(PhoneCalls.HISTORY_COUNTRY_ISO, c.getString(c.getColumnIndex(CallLog.Calls.COUNTRY_ISO)));
                                        values.put(PhoneCalls.HISTORY_DATA_USAGE, c.getLong(c.getColumnIndex(CallLog.Calls.DATA_USAGE)));
                                        values.put(PhoneCalls.HISTORY_GEOCODED_LOCATION, c.getString(c.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)));
                                        values.put(PhoneCalls.HISTORY_VIDEO, ((features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO));

//                                bundle.putString(PhoneCalls.CALL_TRANSCRIPTION_KEY, c.getString(c.getColumnIndex(CallLog.Calls.TRANSCRIPTION)));
                                    case 19:
                                        switch (c.getInt(c.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION))) {
                                            case CallLog.Calls.PRESENTATION_ALLOWED:
                                                bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_ALLOWED);
                                                values.put(PhoneCalls.HISTORY_PRESENTATION, PhoneCalls.CALL_PRESENTATION_ALLOWED);
                                                break;
                                            case CallLog.Calls.PRESENTATION_RESTRICTED:
                                                bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_RESTRICTED);
                                                values.put(PhoneCalls.HISTORY_PRESENTATION, PhoneCalls.CALL_PRESENTATION_RESTRICTED);
                                                break;
                                            case CallLog.Calls.PRESENTATION_PAYPHONE:
                                                bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_PAYPHONE);
                                                values.put(PhoneCalls.HISTORY_PRESENTATION, PhoneCalls.CALL_PRESENTATION_PAYPHONE);
                                                break;
                                            case CallLog.Calls.PRESENTATION_UNKNOWN:
                                                bundle.putString(PhoneCalls.CALL_PRESENTATION_KEY, PhoneCalls.CALL_PRESENTATION_UNKNOWN);
                                                values.put(PhoneCalls.HISTORY_PRESENTATION, PhoneCalls.CALL_PRESENTATION_UNKNOWN);
                                                break;
                                        }
                                    case 14:
                                        bundle.putBoolean(PhoneCalls.CALL_IS_READ_KEY, (c.getInt(c.getColumnIndex(CallLog.Calls.IS_READ)) != 0));
                                        values.put(PhoneCalls.HISTORY_IS_READ, (c.getInt(c.getColumnIndex(CallLog.Calls.IS_READ)) != 0));
                                    case 1:
                                        if (typeInt == CallLog.Calls.INCOMING_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_INCOMING;
                                        } else if (typeInt == CallLog.Calls.OUTGOING_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_OUTGOING;
                                        } else if (typeInt == CallLog.Calls.MISSED_TYPE) {
                                            type = PhoneCalls.CALL_TYPE_MISSED;
                                        }
                                }

                                bundle.putString(PhoneCalls.CALL_TYPE_KEY, type);
                                values.put(PhoneCalls.HISTORY_CALL_TYPE, type);

                                String[] sensitiveFields = {
                                        PhoneCalls.CALL_NUMBER_KEY,
                                        PhoneCalls.CALL_POST_DIAL_DIGITS_KEY,
                                        PhoneCalls.CALL_VIA_NUMBER_KEY,
                                };

                                if (omitSensitiveFields) {
                                    for (String field : sensitiveFields) {
                                        bundle.remove(field);
                                    }

                                    bundle.remove(PhoneCalls.CALL_GEOCODED_LOCATION_KEY);
                                } else {
                                    for (String field : sensitiveFields) {
                                        if (bundle.containsKey(field)) {
                                            bundle.putString(field, new String(Hex.encodeHex(DigestUtils.sha256(bundle.getString(field)))));
                                        }
                                    }
                                }

                                String[] valueSensitiveFields = {
                                        PhoneCalls.HISTORY_NUMBER,
                                        PhoneCalls.HISTORY_POST_DIAL_DIGITS,
                                        PhoneCalls.HISTORY_VIA_NUMBER,
                                };

                                for (String field : valueSensitiveFields) {
                                    if (values.containsKey(field)) {
                                        values.put(field, new String(Hex.encodeHex(DigestUtils.sha256(values.getAsString(field)))));
                                    }
                                }

                                me.mDatabase.insert(PhoneCalls.TABLE_HISTORY, null, values);

                                Generators.getInstance(me.mContext).notifyGeneratorUpdated(PhoneCalls.GENERATOR_IDENTIFIER, bundle);
                            }

                            c.close();
                        }
                    } catch (RuntimeException ex) {

                    }
                }

                if (me.mHandler != null) {
                    me.mHandler.postDelayed(this, me.mSampleInterval);
                }
            }
        };

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, PhoneCalls.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_phone_calls_create_history_table));
            case 1:
                this.mDatabase.delete(PhoneCalls.TABLE_HISTORY, null, null);
            case 2:
                this.mDatabase.delete(PhoneCalls.TABLE_HISTORY, null, null);
        }

        if (version != PhoneCalls.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, PhoneCalls.DATABASE_VERSION);
        }

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

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(PhoneCalls.ENABLED, PhoneCalls.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (PhoneCalls.sInstance == null) {
            return false;
        }

        return PhoneCalls.sInstance.mHandler != null;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED){
                final Handler handler = new Handler(Looper.getMainLooper());

                actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_call_log_permission_required_title), context.getString(R.string.diagnostic_call_log_permission_required), new Runnable() {

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

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        long lastTimestamp = 0;
        long lastDuration = 0;
        String callType = null;

        long totalIncoming = 0;
        long totalOutgoing = 0;
        long totalMissed = 0;
        long total = 0;

        PhoneCalls generator = PhoneCalls.getInstance(holder.itemView.getContext());

        Cursor c = generator.mDatabase.query(PhoneCalls.TABLE_HISTORY, null, null, null, null, null, PhoneCalls.HISTORY_OBSERVED + " DESC");

        while (c.moveToNext()) {
            if (lastTimestamp == 0) {
                lastTimestamp = c.getLong(c.getColumnIndex(PhoneCalls.HISTORY_OBSERVED));
                lastDuration = c.getLong(c.getColumnIndex(PhoneCalls.HISTORY_DURATION));
            }

            total += 1;

            String type = c.getString(c.getColumnIndex(PhoneCalls.HISTORY_CALL_TYPE));

            if (PhoneCalls.CALL_TYPE_INCOMING.equals(type)) {
                totalIncoming += 1;
            } else if (PhoneCalls.CALL_TYPE_OUTGOING.equals(type)) {
                totalOutgoing += 1;
            } else if (PhoneCalls.CALL_TYPE_MISSED.equals(type)) {
                totalMissed += 1;
            }

            if (callType == null) {
                callType = type;
            }
        }

        c.close();

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        if (total > 0) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            dateLabel.setText(Generator.formatTimestamp(context, lastTimestamp));

            PieChart pieChart = holder.itemView.findViewById(R.id.chart_phone_calls);
            pieChart.getLegend().setEnabled(false);

            pieChart.setEntryLabelColor(android.R.color.transparent);
            pieChart.getDescription().setEnabled(false);
            pieChart.setDrawHoleEnabled(false);

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

            data.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return "" + ((Float) value).intValue();
                }
            });

            pieChart.setData(data);
            pieChart.invalidate();

            TextView latestField = holder.itemView.findViewById(R.id.field_latest_call);
            TextView durationField = holder.itemView.findViewById(R.id.field_duration);
            TextView directionField = holder.itemView.findViewById(R.id.field_direction);

            Date lateDate = new Date(lastTimestamp);
            String day = android.text.format.DateFormat.getMediumDateFormat(context).format(lateDate);
            String time = android.text.format.DateFormat.getTimeFormat(context).format(lateDate);

            latestField.setText(context.getString(R.string.format_full_timestamp_pdk, day, time));
            durationField.setText(context.getString(R.string.generator_phone_calls_duration_format, ((float) lastDuration) / 60));
            directionField.setText(callType);

            long storage = generator.storageUsed();

            String storageDesc = context.getString(R.string.label_storage_unknown);

            if (storage >= 0) {
                storageDesc = Humanize.binaryPrefix(storage);
            }

            dateLabel.setText(context.getString(R.string.label_storage_date_card, Generator.formatTimestamp(context, lastTimestamp / 1000.0), storageDesc));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(PhoneCalls.DATA_RETENTION_PERIOD, PhoneCalls.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = PhoneCalls.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        try {
            this.mDatabase.delete(PhoneCalls.TABLE_HISTORY, where, args);
        } catch (SQLiteCantOpenDatabaseException ex) {
            // Try again later
        }
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(PhoneCalls.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return PhoneCalls.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_phone_calls, parent, false);
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        PhoneCalls me = PhoneCalls.getInstance(context);

        Cursor c = me.mDatabase.query(PhoneCalls.TABLE_HISTORY, null, null, null, null, null, PhoneCalls.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(PhoneCalls.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, PhoneCalls.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        if (this.mDatabase != null) {
            return this.mDatabase.query(PhoneCalls.TABLE_HISTORY, cols, where, args, null, null, orderBy);
        } else {
            if (cols == null) {
                cols = new String[0];
            }
        }

        return new MatrixCursor(cols);
    }

    public void setOmitSensitiveFields(boolean doOmit) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(PhoneCalls.OMIT_SENSITIVE_FIELDS, doOmit);
        e.apply();
    }
}
