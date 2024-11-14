package com.audacious_software.passive_data_kit.generators.diagnostics;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.GeneratorViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.communication.TextMessages;
import com.audacious_software.passive_data_kit.transmitters.Transmitter;
import com.audacious_software.passive_data_kit.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.AbstractSequentialList;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import androidx.core.content.ContextCompat;

import humanize.Humanize;

@SuppressWarnings("SimplifiableIfStatement")
@SuppressLint("NewApi")
public class SystemStatus extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-system-status";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.diagnostics.SystemStatus.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String INSTALLED_APPS_ENABLED = "com.audacious_software.passive_data_kit.generators.diagnostics.SystemStatus.INSTALLED_APPS_ENABLED";
    private static final boolean INSTALLED_APPS_ENABLED_DEFAULT = false;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.diagnostics.SystemStatus.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String ACTION_HEARTBEAT = "com.audacious_software.passive_data_kit.generators.diagnostics.SystemStatus.ACTION_HEARTBEAT";

    private static final String DATABASE_PATH = "pdk-system-status.sqlite";
    private static final int DATABASE_VERSION = 4;

    private static final String TABLE_HISTORY = "history";

    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_RUNTIME = "runtime";
    private static final String HISTORY_SYSTEM_RUNTIME = "system_runtime";
    private static final String HISTORY_STORAGE_USED_APP = "storage_app";
    private static final String HISTORY_STORAGE_USED_OTHER = "storage_other";
    private static final String HISTORY_STORAGE_AVAILABLE = "storage_available";
    private static final String HISTORY_STORAGE_TOTAL = "storage_total";
    private static final String HISTORY_STORAGE_PATH = "storage_path";
    private static final String HISTORY_LOCATION_GPS_ENABLED = "gps_enabled";
    private static final String HISTORY_LOCATION_NETWORK_ENABLED = "network_enabled";
    private static final String HISTORY_PENDING_TRANSMISSIONS = "pending_transmissions";
    private static final String HISTORY_INSTALLED_PACKAGES = "installed_packages";
    private static final String HISTORY_GRANTED_PERMISSIONS = "granted_permissions";
    private static final String HISTORY_MISSING_PERMISSIONS = "missing_permissions";
    private static final String HISTORY_HAS_APP_USAGE_PERMISSION = "has_app_usage_permission";
    private static final String HISTORY_IGNORES_BATTERY_OPTIMIZATION = "ignores_battery_optimization";
    private static final String HISTORY_REMOTE_OPTIONS = "remote_options";

    private static final String STATUS_ANNOTATIONS = "annotations";

    private static final double GIGABYTE = (1024 * 1024 * 1024);

    private static SystemStatus sInstance = null;

    private BroadcastReceiver mReceiver = null;

    private SQLiteDatabase mDatabase = null;

    private long mLastTimestamp = 0;
    private long mRefreshInterval = (5 * 60 * 1000);

    private List<SystemStatus.StatusAnnotator> mStatusAnnotators = new ArrayList<>();

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return SystemStatus.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static synchronized SystemStatus getInstance(Context context) {
        if (SystemStatus.sInstance == null) {
            SystemStatus.sInstance = new SystemStatus(context.getApplicationContext());
        }

        return SystemStatus.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public SystemStatus(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        SystemStatus.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final SystemStatus me = this;

        final long runtimeStart = System.currentTimeMillis();

        Generators.getInstance(this.mContext).registerCustomViewClass(SystemStatus.GENERATOR_IDENTIFIER, SystemStatus.class);

        File path = new File(PassiveDataKit.getGeneratorsStorage(this.mContext), SystemStatus.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_system_status_create_history_table));
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_system_status_history_table_add_system_runtime));
            case 2:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_system_status_history_table_add_gps_enabled));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_system_status_history_table_add_network_enabled));
            case 3:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_diagnostics_system_status_history_table_add_pending_transmissions));
        }

        if (version != SystemStatus.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, SystemStatus.DATABASE_VERSION);
        }

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                final long now = System.currentTimeMillis();

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        File path = PassiveDataKit.getGeneratorsStorage(context);

                        me.mLastTimestamp = now;

                        PassiveDataKit.getInstance(context).start();

                        StatFs fsInfo = new StatFs(path.getAbsolutePath());

                        String storagePath = path.getAbsolutePath();

                        long bytesTotal = fsInfo.getTotalBytes();
                        long bytesAvailable = fsInfo.getBlockSizeLong() * fsInfo.getAvailableBlocksLong();

                        long bytesAppUsed = SystemStatus.getFileSize(context.getFilesDir());
                        bytesAppUsed += SystemStatus.getFileSize(context.getExternalFilesDir(null));
                        bytesAppUsed += SystemStatus.getFileSize(context.getCacheDir());
                        bytesAppUsed += SystemStatus.getFileSize(context.getExternalCacheDir());

                        long bytesOtherUsed = bytesTotal - bytesAvailable - bytesAppUsed;

                        long systemRuntime = SystemClock.elapsedRealtime();

                        long pendingTransmissions = 0;

                        for (Transmitter transmitter : Generators.getInstance(context).activeTransmitters()) {
                            pendingTransmissions += transmitter.pendingTransmissions();
                        }

                        ContentValues values = new ContentValues();
                        values.put(SystemStatus.HISTORY_OBSERVED, now);
                        values.put(SystemStatus.HISTORY_RUNTIME, now - runtimeStart);
                        values.put(SystemStatus.HISTORY_SYSTEM_RUNTIME, systemRuntime);
                        values.put(SystemStatus.HISTORY_STORAGE_PATH, storagePath);
                        values.put(SystemStatus.HISTORY_STORAGE_TOTAL, bytesTotal);
                        values.put(SystemStatus.HISTORY_STORAGE_AVAILABLE, bytesAvailable);
                        values.put(SystemStatus.HISTORY_STORAGE_USED_APP, bytesAppUsed);
                        values.put(SystemStatus.HISTORY_STORAGE_USED_OTHER, bytesOtherUsed);
                        values.put(SystemStatus.HISTORY_PENDING_TRANSMISSIONS, pendingTransmissions);

                        Bundle update = new Bundle();
                        update.putLong(SystemStatus.HISTORY_OBSERVED, now);
                        update.putLong(SystemStatus.HISTORY_RUNTIME, now - runtimeStart);
                        update.putLong(SystemStatus.HISTORY_SYSTEM_RUNTIME, systemRuntime);
                        update.putString(SystemStatus.HISTORY_STORAGE_PATH, storagePath);
                        update.putLong(SystemStatus.HISTORY_STORAGE_TOTAL, bytesTotal);
                        update.putLong(SystemStatus.HISTORY_STORAGE_AVAILABLE, bytesAvailable);
                        update.putLong(SystemStatus.HISTORY_STORAGE_USED_APP, bytesAppUsed);
                        update.putLong(SystemStatus.HISTORY_STORAGE_USED_OTHER, bytesOtherUsed);
                        update.putLong(SystemStatus.HISTORY_PENDING_TRANSMISSIONS, pendingTransmissions);

                        if (ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            LocationManager locations = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

                            try {
                                boolean gpsEnabled = locations.isProviderEnabled(LocationManager.GPS_PROVIDER);

                                values.put(SystemStatus.HISTORY_LOCATION_GPS_ENABLED, gpsEnabled);
                                update.putBoolean(SystemStatus.HISTORY_LOCATION_GPS_ENABLED, gpsEnabled);
                            } catch (Exception ex) {

                            }

                            try {
                                boolean networkEnabled = locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                                values.put(SystemStatus.HISTORY_LOCATION_NETWORK_ENABLED, networkEnabled);
                                update.putBoolean(SystemStatus.HISTORY_LOCATION_NETWORK_ENABLED, networkEnabled);

                            } catch (Exception ex) {

                            }
                        }

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        PackageManager packages = context.getPackageManager();

                        if (prefs.getBoolean(SystemStatus.INSTALLED_APPS_ENABLED, SystemStatus.INSTALLED_APPS_ENABLED_DEFAULT)) {
                            ArrayList<String> installed = new ArrayList<>();

                            List<ApplicationInfo> appsList = packages.getInstalledApplications(PackageManager.GET_META_DATA);

                            for (ApplicationInfo info : appsList) {
                                String packageName = info.packageName;

                                if (installed.contains(packageName) == false) {
                                    installed.add(packageName);
                                }
                            }

                            update.putStringArrayList(SystemStatus.HISTORY_INSTALLED_PACKAGES, installed);
                        }

                        ArrayList<String> granted = new ArrayList<>();
                        ArrayList<String> missing = new ArrayList<>();

                        try {
                            PackageInfo pi = packages.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);

                            for (int i = 0; i < pi.requestedPermissions.length; i++) {
                                if ((pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                                    granted.add(pi.requestedPermissions[i]);
                                } else {
                                    missing.add(pi.requestedPermissions[i]);
                                }
                            }
                        } catch (Exception e) {

                        }

                        update.putStringArrayList(SystemStatus.HISTORY_GRANTED_PERMISSIONS, granted);
                        update.putStringArrayList(SystemStatus.HISTORY_MISSING_PERMISSIONS, missing);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            boolean appUsagePermission = false;

                            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                            int mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.getPackageName());

                            if (mode == AppOpsManager.MODE_ALLOWED) {
                                appUsagePermission = true;
                            }

                            update.putBoolean(SystemStatus.HISTORY_HAS_APP_USAGE_PERMISSION, appUsagePermission);
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            boolean ignoresBatteryOptimization = false;

                            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

                            if (power.isIgnoringBatteryOptimizations(context.getPackageName())) {
                                ignoresBatteryOptimization = true;
                            }

                            update.putBoolean(SystemStatus.HISTORY_IGNORES_BATTERY_OPTIMIZATION, ignoresBatteryOptimization);
                        }

                        me.mDatabase.insert(SystemStatus.TABLE_HISTORY, null, values);

                        JSONObject options = PassiveDataKit.getInstance(context).remoteOptions();

                        update.putString(SystemStatus.HISTORY_REMOTE_OPTIONS, options.toString());

                        if (me.mStatusAnnotators.size() > 0) {
                            ArrayList<Bundle> annotationBundles = new ArrayList<>();

                            for (SystemStatus.StatusAnnotator annotator : me.mStatusAnnotators) {
                                Bundle annotations = annotator.annotateStatus(update);

                                if (annotations != null) {
                                    annotationBundles.add(annotations);
                                }
                            }

                            if (annotationBundles.size() > 0) {
                                update.putParcelableArrayList(SystemStatus.STATUS_ANNOTATIONS, annotationBundles);
                            }
                        }

                        Generators.getInstance(context).notifyGeneratorUpdated(SystemStatus.GENERATOR_IDENTIFIER, update);
                    }
                };

                Thread t = new Thread(r);
                t.start();

                AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent(SystemStatus.ACTION_HEARTBEAT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED)) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, pi);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if (alarms.canScheduleExactAlarms()) {
                        alarms.setExact(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, pi);
                    } else {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, pi);
                    }
                } else {
                    alarms.set(AlarmManager.RTC_WAKEUP, now + me.mRefreshInterval, pi);
                }
            }
        };

        this.mReceiver.onReceive(this.mContext, null);

        IntentFilter filter = new IntentFilter(SystemStatus.ACTION_HEARTBEAT);

        ContextCompat.registerReceiver(this.mContext, this.mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        this.flushCachedData();
    }


    public void setEnableInstalledPackages(boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(SystemStatus.INSTALLED_APPS_ENABLED, enabled);
        e.apply();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(SystemStatus.ENABLED, SystemStatus.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (SystemStatus.sInstance == null) {
            return false;
        }

        return SystemStatus.sInstance.mReceiver != null;
    }

    @SuppressWarnings({"unused"})
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarms.canScheduleExactAlarms() == false) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_alarm_permission_required_title), context.getString(R.string.diagnostic_alarm_permission_required), new Runnable() {
                @Override
                public void run() {
                    Intent fetchPermission = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);

                    context.startActivity(fetchPermission);
                }
            }));
        }

        return actions;
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_diagnostics_system_status);
    }

    @SuppressWarnings("unused")
    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(SystemStatus.getGeneratorTitle(holder.itemView.getContext()));
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        SystemStatus generator = SystemStatus.getInstance(context);

        long now = System.currentTimeMillis();
        long start = now - (24 * 60 * 60 * 1000);

        String where = SystemStatus.HISTORY_OBSERVED + " >= ?";
        String[] args = { "" + start };

        Cursor c = generator.mDatabase.query(SystemStatus.TABLE_HISTORY, null, where, args, null, null, SystemStatus.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long storage = generator.storageUsed();

            String storageDesc = context.getString(R.string.label_storage_unknown);

            if (storage >= 0) {
                storageDesc = Humanize.binaryPrefix(storage);
            }

            long timestamp = c.getLong(c.getColumnIndex(SystemStatus.HISTORY_OBSERVED)) / 1000;

            dateLabel.setText(context.getString(R.string.label_storage_date_card, Generator.formatTimestamp(context, timestamp), storageDesc));

            c.moveToPrevious();

            final LineChart chart = holder.itemView.findViewById(R.id.system_status_chart);
            chart.setViewPortOffsets(0,0,0,0);
            chart.setHighlightPerDragEnabled(false);
            chart.setHighlightPerTapEnabled(false);
            chart.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black));
            chart.setPinchZoom(false);

            final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);

            final XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
            xAxis.setTextSize(10f);
            xAxis.setDrawAxisLine(true);
            xAxis.setDrawGridLines(true);
            xAxis.setCenterAxisLabels(true);
            xAxis.setDrawLabels(true);
            xAxis.setTextColor(ContextCompat.getColor(context, android.R.color.white));
            xAxis.setGranularityEnabled(true);
            xAxis.setGranularity(1);
            xAxis.setAxisMinimum(start);
            xAxis.setAxisMaximum(now);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                   Date date = new Date((long) value);

                    return timeFormat.format(date);
                }
            });

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
            leftAxis.setDrawGridLines(true);
            leftAxis.setDrawAxisLine(true);
            leftAxis.setGranularityEnabled(true);
            leftAxis.setTextColor(ContextCompat.getColor(context, android.R.color.white));
            leftAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return "" + value + " GB";
                }
            });

            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(false);

            chart.getLegend().setEnabled(false);
            chart.getDescription().setEnabled(false);

            int observedIndex = c.getColumnIndex(SystemStatus.HISTORY_OBSERVED);
            int availableIndex = c.getColumnIndex(SystemStatus.HISTORY_STORAGE_AVAILABLE);
            int appUsedIndex = c.getColumnIndex(SystemStatus.HISTORY_STORAGE_USED_APP);
            // int othersUsedIndex = c.getColumnIndex(SystemStatus.HISTORY_STORAGE_USED_OTHER);

            ArrayList<Entry> availableValues = new ArrayList<>();
            ArrayList<Entry> appValues = new ArrayList<>();
            // ArrayList<Entry> otherValues = new ArrayList<>();

            long runtime = -1;

            while (c.moveToNext()) {
                long when = c.getLong(observedIndex);

                double available = (double) c.getLong(availableIndex);
                double app = (double) c.getLong(appUsedIndex);
                // double other = (double) c.getLong(othersUsedIndex);

                availableValues.add(0, new Entry(when, (float) (available / SystemStatus.GIGABYTE)));
                appValues.add(0, new Entry(when, (float) (app / SystemStatus.GIGABYTE)));
                // otherValues.add(0, new Entry(when, (float) (other / SystemStatus.GIGABYTE)));

                if (runtime == -1) {
                    runtime = c.getLong(c.getColumnIndex(SystemStatus.HISTORY_RUNTIME));
                }
            }

            LineData sets = new LineData();

            LineDataSet set = new LineDataSet(availableValues, "available");
            set.setAxisDependency(YAxis.AxisDependency.LEFT);
            set.setLineWidth(1.0f);
            set.setDrawCircles(true);
            set.setFillAlpha(192);
            set.setDrawFilled(false);
            set.setDrawValues(true);
            set.setCircleColor(ContextCompat.getColor(context, R.color.generator_system_status_free));
            set.setCircleRadius(1.5f);
            set.setCircleHoleRadius(0.0f);
            set.setDrawCircleHole(false);
            set.setDrawValues(false);
            set.setColor(ContextCompat.getColor(context, R.color.generator_system_status_free));
            set.setMode(LineDataSet.Mode.LINEAR);

            sets.addDataSet(set);

            set = new LineDataSet(appValues, "app");
            set.setAxisDependency(YAxis.AxisDependency.LEFT);
            set.setLineWidth(1.0f);
            set.setDrawCircles(true);
            set.setCircleColor(ContextCompat.getColor(context, R.color.generator_system_status_app));
            set.setCircleRadius(1.5f);
            set.setCircleHoleRadius(0.0f);
            set.setFillAlpha(192);
            set.setDrawFilled(false);
            set.setDrawValues(true);
            set.setColor(ContextCompat.getColor(context, R.color.generator_system_status_app));
            set.setDrawCircleHole(false);
            set.setDrawValues(false);
            set.setMode(LineDataSet.Mode.LINEAR);

            sets.addDataSet(set);

            chart.setData(sets);

            TextView runtimeLabel = holder.itemView.findViewById(R.id.system_status_runtime);
            runtimeLabel.setText(context.getString(R.string.generator_system_status_runtime, SystemStatus.formatRuntime(context, runtime)));
       } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.close();
    }

    @SuppressWarnings("StringConcatenationInLoop")
    private static String formatRuntime(Context context, long runtime) {
        long days = runtime / (24 * 60 * 60 * 1000);
        runtime -= (24 * 60 * 60 * 1000) * days;

        long hours = runtime / (60 * 60 * 1000);
        runtime -= (60 * 60 * 1000) * hours;

        long minutes = runtime / (60 * 1000);
        runtime -= (60 * 1000) * minutes;

        long seconds = runtime / (1000);
        runtime -= (1000) * seconds;

        String hourString = "" + hours;

        if (hourString.length() == 1) {
            hourString = "0" + hourString;
        }

        String minuteString = "" + minutes;

        if (minuteString.length() == 1) {
            minuteString = "0" + minuteString;
        }

        String secondString = "" + seconds;

        if (secondString.length() == 1) {
            secondString = "0" + secondString;
        }

        String msString = "" + runtime;

        while (msString.length() < 3) {
            msString = "0" + msString;
        }

        return context.getString(R.string.generator_system_status_runtime_formatted, days, hourString, minuteString, secondString, msString);
    }

    private static long getFileSize(final File file) {
        if (file == null||!file.exists()) {
            return 0;
        }

        if (!file.isDirectory()) {
            return file.length();
        }

        final List<File> dirs = new LinkedList<>();

        dirs.add(file);

        long result=0;

        while(!dirs.isEmpty()) {
            final File dir = dirs.remove(0);

            if (!dir.exists()) {
                continue;
            }

            final File[] listFiles = dir.listFiles();

            if (listFiles==null||listFiles.length==0) {
                continue;
            }

            for (final File child : listFiles) {
                result += child.length();

                if (child.isDirectory()) {
                    dirs.add(child);
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_diagnostics_system_status, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        SystemStatus me = SystemStatus.getInstance(context);

        if (me.mLastTimestamp == 0) {
            Cursor c = me.mDatabase.query(SystemStatus.TABLE_HISTORY, null, null, null, null, null, SystemStatus.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                me.mLastTimestamp = c.getLong(c.getColumnIndex(SystemStatus.HISTORY_OBSERVED));
            }

            c.close();
        }

        return me.mLastTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(SystemStatus.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    protected void flushCachedData() {
        final SystemStatus me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                long retentionPeriod = prefs.getLong(SystemStatus.DATA_RETENTION_PERIOD, SystemStatus.DATA_RETENTION_PERIOD_DEFAULT);

                long start = System.currentTimeMillis() - retentionPeriod;

                String where = SystemStatus.HISTORY_OBSERVED + " < ?";
                String[] args = { "" + start };

                me.mDatabase.delete(SystemStatus.TABLE_HISTORY, where, args);
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(SystemStatus.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return SystemStatus.GENERATOR_IDENTIFIER;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(SystemStatus.ENABLED, true);
        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, SystemStatus.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public void addStatusAnnotator(SystemStatus.StatusAnnotator annotator) {
        this.mStatusAnnotators.add(annotator);
    }

    public static abstract class StatusAnnotator {
        public abstract Bundle annotateStatus(Bundle reading);
    }

}
