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
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.PhoneUtililties;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import androidx.core.content.ContextCompat;

import humanize.Humanize;

@SuppressWarnings("SimplifiableIfStatement")
public class TextMessages extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-text-messages";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.communication.TextMessages.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.communication.TextMessages.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (4 * 365L * 24L * 60L * 60L * 1000L);

    private static final String OMIT_SENSITIVE_FIELDS = "com.audacious_software.passive_data_kit.generators.communication.TextMessages.OMIT_SENSITIVE_FIELDS";
    private static final boolean OMIT_SENSITIVE_FIELDS_DEFAULT = false;

    public static final Uri SMS_INBOX_URI = Uri.parse("content://sms/inbox");
    public static final Uri SMS_SENT_URI = Uri.parse("content://sms/sent");

    public static final String SMS_DATE = "date";
    public static final String SMS_BODY = "body";
    public static final String SMS_NUMBER_NAME = "person";
    public static final String SMS_NUMBER = "address";
    public static final String SMS_LENGTH = "length";
    public static final String SMS_DIRECTION = "direction";
    public static final String SMS_ANNOTATIONS = "annotations";

    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_DIRECTION = "direction";
    private static final String HISTORY_LENGTH = "length";
    private static final String HISTORY_BODY = "body";
    private static final String HISTORY_NUMBER_NAME = "number_name";
    private static final String HISTORY_NUMBER = "number";
    private static final String HISTORY_DIRECTION_INCOMING = "incoming";
    private static final String HISTORY_DIRECTION_OUTGOING = "outgoing";
    private static final String HISTORY_ANNOTATIONS = "outgoing";

    private static TextMessages sInstance = null;

    private Handler mHandler = null;
    private Context mContext = null;

    private static final String DATABASE_PATH = "pdk-text-messages.sqlite";

    private SQLiteDatabase mDatabase = null;
    private long mSampleInterval = 60000;
    private ArrayList<BodyAnnotator> mBodyAnnotators = new ArrayList<>();

    private boolean mRunning = false;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return TextMessages.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static TextMessages getInstance(Context context) {
        if (TextMessages.sInstance == null) {
            TextMessages.sInstance = new TextMessages(context.getApplicationContext());
        }

        return TextMessages.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public TextMessages(Context context) {
        super(context);

        this.mContext = context.getApplicationContext();
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        TextMessages.getInstance(context).startGenerator();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void startGenerator() {
        final TextMessages me = this;

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
                if (me.mRunning) {
                    return;
                }

                me.mRunning = true;

                boolean approved = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED){
                        approved = true;
                    }
                } else {
                    approved = true;
                }

                if (approved) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                    boolean omitSensitiveFields = prefs.getBoolean(TextMessages.OMIT_SENSITIVE_FIELDS, TextMessages.OMIT_SENSITIVE_FIELDS_DEFAULT);

                    try {
                        long lastObserved = 0;

                        Cursor lastCursor = me.mDatabase.query(TextMessages.TABLE_HISTORY, null, null, null, null, null, TextMessages.HISTORY_OBSERVED + " DESC");

                        if (lastCursor.moveToNext()) {
                            lastObserved = lastCursor.getLong(lastCursor.getColumnIndex(TextMessages.HISTORY_OBSERVED));
                        }

                        long retentionPeriod = prefs.getLong(TextMessages.DATA_RETENTION_PERIOD, TextMessages.DATA_RETENTION_PERIOD_DEFAULT);

                        long now = System.currentTimeMillis();

                        if (lastObserved < (now - retentionPeriod)) {
                            lastObserved = now - retentionPeriod;
                        }

                        lastCursor.close();

                        ArrayList<ContentValues> toTransmit = new ArrayList<>();

                        String where = TextMessages.SMS_DATE + " > ?";
                        String[] args = {"" + lastObserved};

                        Cursor c = me.mContext.getContentResolver().query(TextMessages.SMS_INBOX_URI, null, where, args, TextMessages.SMS_DATE);

                        if (c != null) {
                            while (c.moveToNext()) {
                                ContentValues values = new ContentValues();
                                values.put(TextMessages.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(TextMessages.SMS_DATE)));

                                String body = c.getString(c.getColumnIndex(TextMessages.SMS_BODY));

                                if (body == null) {
                                    body = "";
                                }

                                values.put(TextMessages.HISTORY_LENGTH, body.length());
                                values.put(TextMessages.HISTORY_BODY, body);

                                String name = c.getString(c.getColumnIndex(TextMessages.SMS_NUMBER_NAME));
                                String number = c.getString(c.getColumnIndex(TextMessages.SMS_NUMBER));

                                if (name == null) {
                                    name = number;
                                }

                                values.put(TextMessages.HISTORY_NUMBER_NAME, name);
                                values.put(TextMessages.HISTORY_NUMBER, number);

                                values.put(TextMessages.HISTORY_DIRECTION, TextMessages.HISTORY_DIRECTION_INCOMING);

                                toTransmit.add(values);
                            }

                            c.close();
                        }

                        c = me.mContext.getContentResolver().query(TextMessages.SMS_SENT_URI, null, where, args, TextMessages.SMS_DATE);

                        if (c != null) {
                            while (c.moveToNext()) {
                                ContentValues values = new ContentValues();
                                values.put(TextMessages.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(TextMessages.SMS_DATE)));

                                String body = c.getString(c.getColumnIndex(TextMessages.SMS_BODY));

                                values.put(TextMessages.HISTORY_LENGTH, body.length());
                                values.put(TextMessages.HISTORY_BODY, body);

                                String name = c.getString(c.getColumnIndex(TextMessages.SMS_NUMBER_NAME));
                                String number = c.getString(c.getColumnIndex(TextMessages.SMS_NUMBER));

                                if (name == null) {
                                    name = number;
                                }

                                values.put(TextMessages.HISTORY_NUMBER_NAME, name);
                                values.put(TextMessages.HISTORY_NUMBER, number);

                                values.put(TextMessages.HISTORY_DIRECTION, TextMessages.HISTORY_DIRECTION_OUTGOING);

                                toTransmit.add(values);
                            }

                            c.close();
                        }

                        Collections.sort(toTransmit, new Comparator<ContentValues>() {
                            @Override
                            public int compare(ContentValues one, ContentValues two) {
                                Long oneTime = one.getAsLong(TextMessages.HISTORY_OBSERVED);
                                Long twoTime = two.getAsLong(TextMessages.HISTORY_OBSERVED);

                                return oneTime.compareTo(twoTime);
                            }
                        });

                        for (ContentValues values : toTransmit) {
                            Bundle bundle = new Bundle();

                            if (me.mBodyAnnotators.size() > 0 && values.getAsString(TextMessages.HISTORY_BODY) != null) {
                                ArrayList<Bundle> annotationBundles = new ArrayList<>();

                                for (TextMessages.BodyAnnotator annotator : me.mBodyAnnotators) {
                                    Bundle annotations = annotator.annotateBody(values.getAsString(TextMessages.HISTORY_BODY));

                                    if (annotations != null) {
                                        annotationBundles.add(annotations);
                                    }
                                }

                                if (annotationBundles.size() > 0) {
                                    bundle.putParcelableArrayList(TextMessages.SMS_ANNOTATIONS, annotationBundles);
                                }
                            }

                            String[] sensitiveFields = {
                                    TextMessages.HISTORY_NUMBER_NAME,
                                    TextMessages.HISTORY_NUMBER,
                                    TextMessages.HISTORY_BODY,
                            };

                            for (String field : sensitiveFields) {
                                if (values.containsKey(field)) {
                                    String value = values.getAsString(field);

                                    if (field.equals(TextMessages.HISTORY_NUMBER)) {
                                        value = PhoneUtililties.e164PhoneNumber(value);
                                    }

                                    try {
                                        values.put(field, new String(Hex.encodeHex(DigestUtils.sha256(value))));
                                    } catch (NullPointerException ex) {
                                        values.put(field, "null");
                                    }
                                }
                            }

                            bundle.putLong(TextMessages.SMS_DATE, values.getAsLong(TextMessages.HISTORY_OBSERVED));
                            bundle.putInt(TextMessages.SMS_LENGTH, values.getAsInteger(TextMessages.HISTORY_LENGTH));
                            bundle.putString(TextMessages.SMS_NUMBER_NAME, values.getAsString(TextMessages.HISTORY_NUMBER_NAME));
                            bundle.putString(TextMessages.SMS_NUMBER, values.getAsString(TextMessages.HISTORY_NUMBER));
                            bundle.putString(TextMessages.SMS_DIRECTION, values.getAsString(TextMessages.HISTORY_DIRECTION));
                            bundle.putString(TextMessages.SMS_BODY, values.getAsString(TextMessages.HISTORY_BODY));

                            if (omitSensitiveFields) {
                                bundle.remove(TextMessages.SMS_NUMBER_NAME);
                                bundle.remove(TextMessages.SMS_NUMBER);
                                bundle.remove(TextMessages.SMS_BODY);
                            }

                            Generators.getInstance(me.mContext).notifyGeneratorUpdated(TextMessages.GENERATOR_IDENTIFIER, values.getAsLong(TextMessages.HISTORY_OBSERVED), bundle);

                            me.mDatabase.insert(TextMessages.TABLE_HISTORY, null, values);
                        }
                    } catch (SecurityException ex) {
                        ex.printStackTrace();
                        Logger.getInstance(me.mContext).logThrowable(ex);
                    } catch (RuntimeException ex) {
                        ex.printStackTrace();
                        // CursorWindowAllocationException
                    }
                }

                me.mRunning = false;

                if (me.mHandler != null) {
                    me.mHandler.postDelayed(this, me.mSampleInterval);
                }
            }
        };

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, TextMessages.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_text_messages_create_history_table));
        }

        if (version != TextMessages.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, TextMessages.DATABASE_VERSION);
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

        Thread init = new Thread(new Runnable() {
            @Override
            public void run() {
                while (me.mHandler == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                me.mHandler.postDelayed(checkLogs, 1000);
            }
        });
        init.start();

        Generators.getInstance(this.mContext).registerCustomViewClass(TextMessages.GENERATOR_IDENTIFIER, TextMessages.class);

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(TextMessages.ENABLED, TextMessages.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (TextMessages.sInstance == null) {
            return false;
        }

        return TextMessages.sInstance.mHandler != null;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED){
                final Handler handler = new Handler(Looper.getMainLooper());

                actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_sms_log_permission_required_title), context.getString(R.string.diagnostic_sms_log_permission_required), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Intent intent = new Intent(context, RequestPermissionActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra(RequestPermissionActivity.PERMISSION, Manifest.permission.READ_SMS);

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
        int lastLength = 0;

        long totalIncoming = 0;
        long totalOutgoing = 0;
        long total = 0;

        TextMessages generator = TextMessages.getInstance(holder.itemView.getContext());
        String lastDirection = null;

        Cursor c = generator.mDatabase.query(TextMessages.TABLE_HISTORY, null, null, null, null, null, TextMessages.HISTORY_OBSERVED + " DESC");

        while (c.moveToNext()) {
            if (lastTimestamp == 0) {
                lastTimestamp = c.getLong(c.getColumnIndex(TextMessages.HISTORY_OBSERVED));
                lastDirection = c.getString(c.getColumnIndex(TextMessages.HISTORY_DIRECTION));
                lastLength = c.getInt(c.getColumnIndex(TextMessages.HISTORY_LENGTH));
            }

            total += 1;

            String direction = c.getString(c.getColumnIndex(TextMessages.HISTORY_DIRECTION));

            if (TextMessages.HISTORY_DIRECTION_INCOMING.equals(direction)) {
                totalIncoming += 1;
            } else if (TextMessages.HISTORY_DIRECTION_OUTGOING.equals(direction)) {
                totalOutgoing += 1;
            }
        }

        c.close();

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        if (total > 0) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long storage = generator.storageUsed();

            String storageDesc = context.getString(R.string.label_storage_unknown);

            if (storage >= 0) {
                storageDesc = Humanize.binaryPrefix(storage);
            }

            dateLabel.setText(context.getString(R.string.label_storage_date_card, Generator.formatTimestamp(context, lastTimestamp / 1000.0), storageDesc));

            PieChart pieChart = holder.itemView.findViewById(R.id.chart_text_messages);
            pieChart.getLegend().setEnabled(false);

            pieChart.setEntryLabelColor(android.R.color.transparent);
            pieChart.getDescription().setEnabled(false);
            pieChart.setDrawHoleEnabled(false);

            List<PieEntry> entries = new ArrayList<>();

            if (totalIncoming > 0) {
                entries.add(new PieEntry(totalIncoming, context.getString(R.string.generator_text_messages_incoming_label)));
            }

            if (totalOutgoing > 0) {
                entries.add(new PieEntry(totalOutgoing, context.getString(R.string.generator_text_messages_outgoing_label)));
            }

            PieDataSet set = new PieDataSet(entries, " ");

            int[] colors = {
                    R.color.generator_text_messages_incoming,
                    R.color.generator_text_messages_outgoing
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

            TextView latestField = holder.itemView.findViewById(R.id.field_latest_text_message);
            TextView lengthField = holder.itemView.findViewById(R.id.field_length);
            TextView directionField = holder.itemView.findViewById(R.id.field_direction);

            Date lateDate = new Date(lastTimestamp);
            String day = android.text.format.DateFormat.getMediumDateFormat(context).format(lateDate);
            String time = android.text.format.DateFormat.getTimeFormat(context).format(lateDate);

            latestField.setText(context.getString(R.string.format_full_timestamp_pdk, day, time));
            lengthField.setText(context.getResources().getQuantityString(R.plurals.generator_text_messages_length_format, lastLength, lastLength));
            directionField.setText(lastDirection);
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(TextMessages.DATA_RETENTION_PERIOD, TextMessages.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = TextMessages.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(TextMessages.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(TextMessages.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_text_messages, parent, false);
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        TextMessages me = TextMessages.getInstance(context);

        Cursor c = me.mDatabase.query(TextMessages.TABLE_HISTORY, null, null, null, null, null, TextMessages.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(TextMessages.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    @Override
    public String getIdentifier() {
        return TextMessages.GENERATOR_IDENTIFIER;
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, TextMessages.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        if (this.mDatabase != null) {
            return this.mDatabase.query(TextMessages.TABLE_HISTORY, cols, where, args, null, null, orderBy);
        } else {
            if (cols == null) {
                cols = new String[0];
            }
        }

        return new MatrixCursor(cols);
    }

    public void addBodyAnnotator(TextMessages.BodyAnnotator annotator) {
        this.mBodyAnnotators.add(annotator);
    }

    public void setOmitSensitiveFields(boolean doOmit) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(TextMessages.OMIT_SENSITIVE_FIELDS, doOmit);
        e.apply();
    }

    public static abstract class BodyAnnotator {
        public abstract Bundle annotateBody(String body);
    }
}
