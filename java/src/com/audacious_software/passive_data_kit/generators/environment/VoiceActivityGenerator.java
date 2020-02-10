package com.audacious_software.passive_data_kit.generators.environment;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.device.ScreenState;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VoiceActivityGenerator extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-voice-activity";

    private static final String ACTION_HEARTBEAT = "com.audacious_software.passive_data_kit.generators.environment.VoiceActivityGenerator.ACTION_HEARTBEAT";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.environment.VoiceActivityGenerator.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.environment.VoiceActivityGenerator.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);


    private static final String DATABASE_PATH = "pdk-voice-activity.sqlite";
    private static final int DATABASE_VERSION = 3;

    private static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_LEVEL = "level";
    private static final String HISTORY_VOICES_PRESENT = "voices_present";

    public static final int SMOOTHING_MEDIAN = 0;
    public static final int SMOOTHING_MEAN = 1;

    private static final String HISTORY_SAMPLING_RATE = "sampling_rate";
    private static final String HISTORY_SMOOTH_WINDOW = "smooth_window";
    private static final String HISTORY_EVALUATION_INTERVAL = "evaluation_interval";
    private static final String HISTORY_EVALUATION_COUNT = "evaluation_count";
    private static final String HISTORY_SMOOTH_MODE = "smooth_mode";

    private static final String HISTORY_SMOOTH_MODE_MEAN = "mean";
    private static final String HISTORY_SMOOTH_MODE_MEDIAN = "median";
    private static final String HISTORY_SMOOTH_MODE_UNKNOWN = "unknown";

    private static VoiceActivityGenerator sInstance = null;

    private SQLiteDatabase mDatabase = null;

    private long mLatestTimestamp = -1;

    private BroadcastReceiver mReceiver = null;

    private long mLastTimestamp = 0;
    private long mRefreshInterval = (5 * 60 * 1000);
    private int mScanCount = 5;

    private int mSampleRate = 8000;

    private int mSmoothMode = VoiceActivityGenerator.SMOOTHING_MEDIAN;
    private int mSmoothWindow = 13;

    private PendingIntent mHeartbeatIntent = null;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return VoiceActivityGenerator.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static synchronized VoiceActivityGenerator getInstance(Context context) {
        if (VoiceActivityGenerator.sInstance == null) {
            VoiceActivityGenerator.sInstance = new VoiceActivityGenerator(context.getApplicationContext());
        }

        return VoiceActivityGenerator.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public VoiceActivityGenerator(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        VoiceActivityGenerator.getInstance(context).startGenerator();
    }

    private double voicePresence() {
        if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            double detected = 0;
            double runs = 0;

            for (int i = 0; i < this.mScanCount; i++) {
                VadUtil vadUtil = new VadUtil();

                int bufferSize = 2 * AudioRecord.getMinBufferSize(this.mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

                if (bufferSize > 0) {
                    AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            this.mSampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize);

                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                    byte[] buffer = new byte[bufferSize];
                    boolean run = true;
                    int read;
                    long total = 0;

                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord.startRecording();

                        boolean completed = false;

                        try {
                            while (run) {
                                read = audioRecord.read(buffer, 0, buffer.length);

                                bytes.write(buffer, 0, read);

                                total += read;

                                if (total > this.mSampleRate * 3) { // Record 3 seconds...
                                    run = false;
                                }
                            }

                            completed = true;
                        } catch (IndexOutOfBoundsException ex) {
                            // Try again next time.
                        } finally {
                            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                                audioRecord.stop();
                            }

                            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                                audioRecord.release();
                            }
                        }

                        if (completed) {
                            byte[] samples = bytes.toByteArray();

                            if (vadUtil.detectVoice(samples, samples.length / 2, this.mSampleRate, this.mSmoothMode, this.mSmoothWindow) != 0) {
                                detected += 1;
                            }

                            runs += 1;
                        }
                    }
                }
            }

            return detected / runs;
        }

        return -1;
    }

    private void stopGenerator() {
        if (this.mHeartbeatIntent != null) {
            AlarmManager alarms = (AlarmManager) this.mContext.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(this.mContext, 0, new Intent(VoiceActivityGenerator.ACTION_HEARTBEAT), PendingIntent.FLAG_UPDATE_CURRENT);

            alarms.cancel(this.mHeartbeatIntent);

            this.mHeartbeatIntent = null;
            this.mReceiver = null;
        }
    }

    private void startGenerator() {
        if (VoiceActivityGenerator.isRunning(this.mContext)) {
            return;
        }

        final VoiceActivityGenerator me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(VoiceActivityGenerator.GENERATOR_IDENTIFIER, VoiceActivityGenerator.class);

        File path = new File(PassiveDataKit.getGeneratorsStorage(this.mContext), VoiceActivityGenerator.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_create_history_table));
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_history_table_add_level));
            case 2:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_history_table_add_sampling_rate));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_history_table_add_smooth_window));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_history_table_add_smoothing_mode));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_history_table_add_evaluation_interval));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_voice_activity_history_table_add_evaluation_count));
        }

        if (version != VoiceActivityGenerator.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, VoiceActivityGenerator.DATABASE_VERSION);
        }

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                final long now = System.currentTimeMillis();

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        me.mLastTimestamp = now;

                        PassiveDataKit.getInstance(context).start();

                        double voicePresence = me.voicePresence();

                        if (voicePresence >= 0) {
                            Log.e("PDK", "VOICES: " + voicePresence);

                            ContentValues values = new ContentValues();
                            values.put(VoiceActivityGenerator.HISTORY_OBSERVED, now);
                            values.put(VoiceActivityGenerator.HISTORY_LEVEL, voicePresence);
                            values.put(VoiceActivityGenerator.HISTORY_VOICES_PRESENT, (voicePresence > 0));
                            values.put(VoiceActivityGenerator.HISTORY_SAMPLING_RATE, me.mSampleRate);
                            values.put(VoiceActivityGenerator.HISTORY_SMOOTH_WINDOW, me.mSmoothWindow);
                            values.put(VoiceActivityGenerator.HISTORY_EVALUATION_INTERVAL, me.mRefreshInterval);
                            values.put(VoiceActivityGenerator.HISTORY_EVALUATION_COUNT, me.mScanCount);

                            if (me.mSmoothMode == VoiceActivityGenerator.SMOOTHING_MEAN) {
                                values.put(VoiceActivityGenerator.HISTORY_SMOOTH_MODE, VoiceActivityGenerator.HISTORY_SMOOTH_MODE_MEAN);
                            } else if (me.mSmoothMode == VoiceActivityGenerator.SMOOTHING_MEDIAN) {
                                values.put(VoiceActivityGenerator.HISTORY_SMOOTH_MODE, VoiceActivityGenerator.HISTORY_SMOOTH_MODE_MEDIAN);
                            } else {
                                values.put(VoiceActivityGenerator.HISTORY_SMOOTH_MODE, VoiceActivityGenerator.HISTORY_SMOOTH_MODE_UNKNOWN);
                            }

                            Bundle update = new Bundle();
                            update.putLong(VoiceActivityGenerator.HISTORY_OBSERVED, now);
                            update.putFloat(VoiceActivityGenerator.HISTORY_LEVEL, (float) voicePresence);
                            update.putBoolean(VoiceActivityGenerator.HISTORY_VOICES_PRESENT, (voicePresence > 0));

                            update.putLong(VoiceActivityGenerator.HISTORY_SAMPLING_RATE, me.mSampleRate);
                            update.putLong(VoiceActivityGenerator.HISTORY_SMOOTH_WINDOW, me.mSmoothWindow);
                            update.putLong(VoiceActivityGenerator.HISTORY_EVALUATION_INTERVAL, me.mRefreshInterval);
                            update.putLong(VoiceActivityGenerator.HISTORY_EVALUATION_COUNT, me.mScanCount);

                            if (me.mSmoothMode == VoiceActivityGenerator.SMOOTHING_MEAN) {
                                update.putString(VoiceActivityGenerator.HISTORY_SMOOTH_MODE, VoiceActivityGenerator.HISTORY_SMOOTH_MODE_MEAN);
                            } else if (me.mSmoothMode == VoiceActivityGenerator.SMOOTHING_MEDIAN) {
                                update.putString(VoiceActivityGenerator.HISTORY_SMOOTH_MODE, VoiceActivityGenerator.HISTORY_SMOOTH_MODE_MEDIAN);
                            } else {
                                update.putString(VoiceActivityGenerator.HISTORY_SMOOTH_MODE, VoiceActivityGenerator.HISTORY_SMOOTH_MODE_UNKNOWN);
                            }

                            me.mDatabase.insert(VoiceActivityGenerator.TABLE_HISTORY, null, values);

                            Generators.getInstance(context).notifyGeneratorUpdated(VoiceActivityGenerator.GENERATOR_IDENTIFIER, update);
                        } else {
                            // Permission not granted.
                        }
                    }
                };

                Thread t = new Thread(r);
                t.start();

                AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                me.mHeartbeatIntent = PendingIntent.getBroadcast(context, 0, new Intent(VoiceActivityGenerator.ACTION_HEARTBEAT), PendingIntent.FLAG_UPDATE_CURRENT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, me.mHeartbeatIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarms.setExact(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, me.mHeartbeatIntent);
                } else {
                    alarms.set(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, me.mHeartbeatIntent);
                }
            }
        };

        this.mReceiver.onReceive(this.mContext, null);

        IntentFilter filter = new IntentFilter(VoiceActivityGenerator.ACTION_HEARTBEAT);
        this.mContext.registerReceiver(this.mReceiver, filter);

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(VoiceActivityGenerator.ENABLED, VoiceActivityGenerator.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        VoiceActivityGenerator voices = VoiceActivityGenerator.getInstance(context);

        return (voices.mReceiver != null);
    }

    @SuppressWarnings({"unused"})
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_voice_detection);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        VoiceActivityGenerator me = VoiceActivityGenerator.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(VoiceActivityGenerator.TABLE_HISTORY, null, null, null, null, null, VoiceActivityGenerator.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(VoiceActivityGenerator.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(VoiceActivityGenerator.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(VoiceActivityGenerator.DATA_RETENTION_PERIOD, VoiceActivityGenerator.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = ScreenState.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(VoiceActivityGenerator.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(VoiceActivityGenerator.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return VoiceActivityGenerator.GENERATOR_IDENTIFIER;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(VoiceActivityGenerator.ENABLED, true);
        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, VoiceActivityGenerator.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public void setRefreshInterval(long interval) {
        this.mRefreshInterval = interval;

        if (VoiceActivityGenerator.isRunning(this.mContext)) {
            this.stopGenerator();
            this.startGenerator();
        }
    }

    public void setScansPerRefresh(int count) {
        this.mScanCount = count;

        if (VoiceActivityGenerator.isRunning(this.mContext)) {
            this.stopGenerator();
            this.startGenerator();
        }
    }

    public void setSmoothingWindowSize(int samples) {
        this.mSmoothWindow = samples;

        if (VoiceActivityGenerator.isRunning(this.mContext)) {
            this.stopGenerator();
            this.startGenerator();
        }
    }

    public void setSmoothingMode(int mode) {
        this.mSmoothMode = mode;

        if (VoiceActivityGenerator.isRunning(this.mContext)) {
            this.stopGenerator();
            this.startGenerator();
        }
    }

    public static class VadUtil {
        static {
            System.loadLibrary("pdk-lib");
        }

        public native int detectVoice(byte[] samples, int sampleCount, int mSampleRate, int smoothMode, int windowSamples);
    }
}
