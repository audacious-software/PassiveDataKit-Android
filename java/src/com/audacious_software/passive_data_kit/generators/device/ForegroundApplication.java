package com.audacious_software.passive_data_kit.generators.device;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.collection.CircularArray;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.Toolbox;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.GeneratorViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.rvalerio.fgchecker.AppChecker;

import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import humanize.Humanize;

@SuppressWarnings({"PointlessBooleanExpression", "SimplifiableIfStatement"})
@SuppressLint("Range")
public class ForegroundApplication extends Generator{
    private static final String GENERATOR_IDENTIFIER = "pdk-foreground-application";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String SAMPLE_INTERVAL = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.SAMPLE_INTERVAL";
    private static final long SAMPLE_INTERVAL_DEFAULT = 15000;

    private static final int DATABASE_VERSION = 7;

    private static final String TABLE_HISTORY = "history";
    public static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_APPLICATION = "application";
    private static final String HISTORY_CATEGORY = "category";
    private static final String HISTORY_DURATION = "duration";
    private static final String HISTORY_SCREEN_ACTIVE = "screen_active";
    private static final String HISTORY_IS_HOME = "is_home";

    private static final String HISTORY_DISPLAY_STATE = "display_state";
    private static final String HISTORY_DISPLAY_STATE_OFF = "off";
    private static final String HISTORY_DISPLAY_STATE_ON = "on";
    private static final String HISTORY_DISPLAY_STATE_ON_SUSPEND = "on-suspend";
    private static final String HISTORY_DISPLAY_STATE_DOZE = "doze";
    private static final String HISTORY_DISPLAY_STATE_DOZE_SUSPEND = "doze-suspend";
    private static final String HISTORY_DISPLAY_STATE_VR = "virtual-reality";
    private static final String HISTORY_DISPLAY_STATE_UNKNOWN = "unknown";

    private static final String DISABLED_APPS = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.DISABLED_APPS";
    private static final String ENABLED_APPS = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.ENABLED_APPS";
    private static final String DISABLED_CATEGORIES = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.DISABLED_CATEGORIES";
    private static final String ENABLED_CATEGORIES = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.ENABLED_CATEGORIES";

    private static final String OBSCURE_SEED = "com.audacious_software.passive_data_kit.generators.device.ForegroundApplication.OBSCURE_SEED";

    private static final String CATEGORY_UNKNOWN = "unknown";
    private static final String CATEGORY_ACCESSIBILITY = "accessibility";
    private static final String CATEGORY_AUDIO = "audio";
    private static final String CATEGORY_GAME = "game";
    private static final String CATEGORY_IMAGE = "image";
    private static final String CATEGORY_MAPS = "maps";
    private static final String CATEGORY_NEWS = "news";
    private static final String CATEGORY_PRODUCTIVITY = "productivity";
    private static final String CATEGORY_SOCIAL = "social";
    private static final String CATEGORY_VIDEO = "video";

    private static ForegroundApplication sInstance = null;

    private static final String DATABASE_PATH = "pdk-foreground-application.sqlite";

    private SQLiteDatabase mDatabase = null;

    private AppChecker mAppChecker = null;
    private long mLastTimestamp = 0;
    private long mEarliestTimestamp = 0;

    private final HashMap<String, Long> mUsageDurations = new HashMap<>();
    private final HashMap<String, Integer> mUsageDaysCache = new HashMap<>();
    private final HashMap<String, String> mCategoryCache = new HashMap<>();
    private final HashMap<String, String> mIdentifierCache = new HashMap<>();

    public static class ForegroundApplicationUsage {
        public long start;
        public long duration;
        public String packageName;
    }

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return ForegroundApplication.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static synchronized ForegroundApplication getInstance(Context context) {
        if (ForegroundApplication.sInstance == null) {
            ForegroundApplication.sInstance = new ForegroundApplication(context.getApplicationContext());
        }

        return ForegroundApplication.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public ForegroundApplication(Context context) {
        super(context);

        synchronized (context.getApplicationContext()) {
            File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

            path = new File(path, ForegroundApplication.DATABASE_PATH);

            this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

            int version = this.getDatabaseVersion(this.mDatabase);

            switch (version) {
                case 0:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_create_history_table));
                case 1:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_duration));
                case 2:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_screen_active));
                case 3:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_display_state));
                case 4:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_is_home));
                case 5:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_create_substitutes_table));
                case 6:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_category));
            }

            if (version != ForegroundApplication.DATABASE_VERSION) {
                this.setDatabaseVersion(this.mDatabase, ForegroundApplication.DATABASE_VERSION);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        ForegroundApplication.getInstance(context).startGenerator();
    }

    public void setSampleInterval(long interval) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putLong(ForegroundApplication.SAMPLE_INTERVAL, interval);
        e.apply();
    }

    private void logAppAppearance(String process, long when, long duration) {
        String category = this.fetchCategory(process);

        if (this.isAppEnabled(process) == false || this.isCategoryEnabled(category) == false) {
            process = this.obscureIdentifier(process);
            category = this.obscureIdentifier(category);
        }

        final ForegroundApplication me = this;

        WindowManager window = (WindowManager) this.mContext.getSystemService(Context.WINDOW_SERVICE);
        final Display display = window.getDefaultDisplay();

        String finalProcess = process;
        String finalCategory = category;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                synchronized (me.mUsageDurations) {
                    boolean screenActive = true;

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        if (display.getState() != Display.STATE_ON) {
                            screenActive = false;
                        }
                    }

                    boolean isHome = false;

                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.addCategory(Intent.CATEGORY_DEFAULT);

                    PackageManager manager = mContext.getPackageManager();
                    List<ResolveInfo> startMatches = manager.queryIntentActivities(startMain, 0);

                    for (ResolveInfo info : startMatches) {
                        if (info.activityInfo.packageName != null && info.activityInfo.packageName.equals(finalProcess)) {
                            isHome = true;
                        }
                    }

                    ContentValues values = new ContentValues();
                    values.put(ForegroundApplication.HISTORY_OBSERVED, when);
                    values.put(ForegroundApplication.HISTORY_APPLICATION, finalProcess);
                    values.put(ForegroundApplication.HISTORY_CATEGORY, finalCategory);
                    values.put(ForegroundApplication.HISTORY_DURATION, duration);
                    values.put(ForegroundApplication.HISTORY_SCREEN_ACTIVE, screenActive);

                    values.put(ForegroundApplication.HISTORY_IS_HOME, isHome);

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        int state = display.getState();

                        switch (state) {
                            case Display.STATE_OFF:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_OFF);
                                break;
                            case Display.STATE_ON:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_ON);
                                break;
                            case Display.STATE_ON_SUSPEND:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_ON_SUSPEND);
                                break;
                            case Display.STATE_DOZE:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_DOZE);
                                break;
                            case Display.STATE_DOZE_SUSPEND:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_DOZE_SUSPEND);
                                break;
                            case Display.STATE_VR:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_VR);
                                break;
                            case Display.STATE_UNKNOWN:
                                values.put(ForegroundApplication.HISTORY_DISPLAY_STATE, ForegroundApplication.HISTORY_DISPLAY_STATE_UNKNOWN);
                                break;
                        }
                    }

                    synchronized(me) {
                        if (me.mDatabase.isOpen()) {
                            me.mDatabase.insert(ForegroundApplication.TABLE_HISTORY, null, values);
                        }
                    }

                    Bundle update = new Bundle();
                    update.putLong(ForegroundApplication.HISTORY_OBSERVED, when);
                    update.putString(ForegroundApplication.HISTORY_APPLICATION, finalProcess);
                    update.putString(ForegroundApplication.HISTORY_CATEGORY, finalCategory);
                    update.putLong(ForegroundApplication.HISTORY_DURATION, duration);
                    update.putBoolean(ForegroundApplication.HISTORY_SCREEN_ACTIVE, screenActive);
                    update.putBoolean(ForegroundApplication.HISTORY_IS_HOME, isHome);

                    if (values.containsKey(ForegroundApplication.HISTORY_DISPLAY_STATE)) {
                        update.putString(ForegroundApplication.HISTORY_DISPLAY_STATE, values.getAsString(ForegroundApplication.HISTORY_DISPLAY_STATE));
                    }

                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(ForegroundApplication.GENERATOR_IDENTIFIER, update);

                    ArrayList<String> toDelete = new ArrayList<>();

                    for (String key : me.mUsageDurations.keySet()) {
                        if (key.startsWith(finalProcess)) {
                            toDelete.add(key);
                        }
                    }

                    for (String key : toDelete) {
                        me.mUsageDurations.remove(key);
                    }
                }
            }
        };

        try {
            Thread t = new Thread(r);
            t.start();
        } catch (OutOfMemoryError e) {
            // Try again later...
        }
    }

    private String fetchCategory(String process) {
        String category = ForegroundApplication.CATEGORY_UNKNOWN;

        if (this.mCategoryCache.containsKey(process)) {
            category = this.mCategoryCache.get(process);
        } else {
            PackageManager packages = this.mContext.getPackageManager();

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ApplicationInfo appInfo = packages.getApplicationInfo(process, PackageManager.GET_META_DATA);

                    switch (appInfo.category) {
                        case ApplicationInfo.CATEGORY_ACCESSIBILITY:
                            category = ForegroundApplication.CATEGORY_ACCESSIBILITY;
                            break;
                        case ApplicationInfo.CATEGORY_AUDIO:
                            category = ForegroundApplication.CATEGORY_AUDIO;
                            break;
                        case ApplicationInfo.CATEGORY_GAME:
                            category = ForegroundApplication.CATEGORY_GAME;
                            break;
                        case ApplicationInfo.CATEGORY_IMAGE:
                            category = ForegroundApplication.CATEGORY_IMAGE;
                            break;
                        case ApplicationInfo.CATEGORY_MAPS:
                            category = ForegroundApplication.CATEGORY_MAPS;
                            break;
                        case ApplicationInfo.CATEGORY_NEWS:
                            category = ForegroundApplication.CATEGORY_NEWS;
                            break;
                        case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                            category = ForegroundApplication.CATEGORY_PRODUCTIVITY;
                            break;
                        case ApplicationInfo.CATEGORY_SOCIAL:
                            category = ForegroundApplication.CATEGORY_SOCIAL;
                            break;
                        case ApplicationInfo.CATEGORY_VIDEO:
                            category = ForegroundApplication.CATEGORY_VIDEO;
                            break;
                        default:
                            category = ForegroundApplication.CATEGORY_UNKNOWN;
                            break;
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                category = ForegroundApplication.CATEGORY_UNKNOWN;
            }

            this.mCategoryCache.put(process, category);
        }

        return category;
    }

    private void startGenerator() {
        final ForegroundApplication me = this;

        if (this.mAppChecker != null) {
            this.mAppChecker.stop();

            this.mAppChecker = null;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        final long sampleInterval = prefs.getLong(ForegroundApplication.SAMPLE_INTERVAL, ForegroundApplication.SAMPLE_INTERVAL_DEFAULT);

        this.mAppChecker = new AppChecker();
        this.mAppChecker.whenAny(new AppChecker.Listener() {
            @Override
            public void onForeground(final String process) {
                if (process == null) {
                    return;
                }

                final long now = System.currentTimeMillis();

                me.logAppAppearance(process, now, sampleInterval);
            }
        });

        this.mAppChecker.timeout((int) sampleInterval);
        this.mAppChecker.start(this.mContext);

        Generators.getInstance(this.mContext).registerCustomViewClass(ForegroundApplication.GENERATOR_IDENTIFIER, ForegroundApplication.class);

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(ForegroundApplication.ENABLED, ForegroundApplication.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (ForegroundApplication.sInstance == null) {
            return false;
        }

        return ForegroundApplication.sInstance.mAppChecker != null;
    }

    @SuppressWarnings("unused")
    @SuppressLint("InlinedApi")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (ForegroundApplication.hasPermissions(context) == false) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_usage_stats_permission_required_title), context.getString(R.string.diagnostic_usage_stats_permission_required), new Runnable() {
                @Override
                public void run() {
                    ForegroundApplication.fetchPermissions(context);
                }
            }));
        }

        return actions;
    }

    public static void fetchPermissions(final Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static boolean hasPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.getPackageName());

            if (mode != AppOpsManager.MODE_ALLOWED) {
                return false;
            }
        }

        return true;
    }

    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_foreground_application);
    }

    @SuppressWarnings("unused")
    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(ForegroundApplication.getGeneratorTitle(holder.itemView.getContext()));
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        long start = System.currentTimeMillis();

        final Context context = holder.itemView.getContext();

        long lastTimestamp = 0;

        ForegroundApplication generator = ForegroundApplication.getInstance(holder.itemView.getContext());

        Cursor c = generator.mDatabase.query(ForegroundApplication.TABLE_HISTORY, null, null, null, null, null, ForegroundApplication.HISTORY_OBSERVED + " DESC", "1");

        if (c.moveToNext()) {
            if (lastTimestamp == 0) {
                lastTimestamp = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));
            }
        }

        c.close();

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        long now = System.currentTimeMillis();
        long yesterday = now - (24 * 60 * 60 * 1000);

        HashMap<String,Double> appDurations = new HashMap<>();
        HashMap<String,Long> appWhens = new HashMap<>();

        ArrayList<String> latest = new ArrayList<>();

        String where = ForegroundApplication.HISTORY_OBSERVED + " > ? AND " + ForegroundApplication.HISTORY_SCREEN_ACTIVE + " = ?";
        String[] args = { "" + yesterday, "1" };

        c = generator.mDatabase.query(ForegroundApplication.TABLE_HISTORY, null, where, args, null, null, ForegroundApplication.HISTORY_OBSERVED);

        while (c.moveToNext()) {
            String application = c.getString(c.getColumnIndex(ForegroundApplication.HISTORY_APPLICATION));

            if (application != null) {
                latest.remove(application);

                latest.add(0, application);

                if (appDurations.containsKey(application) == false) {
                    appDurations.put(application, 0.0);
                }

                double appDuration = appDurations.get(application);
                double duration = c.getDouble(c.getColumnIndex(ForegroundApplication.HISTORY_DURATION));

                long lastObserved = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));

                appDuration += duration;

                appDurations.put(application, appDuration);
                appWhens.put(application, lastObserved);
            }
        }

        c.close();

        double largestUsage = 0.0;

        ArrayList<HashMap<String,Double>> largest = new ArrayList<>();

        for (String key : appDurations.keySet()) {
            HashMap<String, Double> app = new HashMap<>();

            double duration = appDurations.get(key);

            app.put(key, duration);

            if (duration > largestUsage) {
                largestUsage = duration;
            }

            largest.add(app);
        }

        Collections.sort(largest, new Comparator<HashMap<String, Double>>() {
            @Override
            public int compare(HashMap<String, Double> mapOne, HashMap<String, Double> mapTwo) {
                String keyOne = mapOne.keySet().iterator().next();
                String keyTwo = mapTwo.keySet().iterator().next();

                Double valueOne = mapOne.get(keyOne);
                Double valueTwo = mapTwo.get(keyTwo);

                return valueTwo.compareTo(valueOne);
            }
        });

        int[] appRowIds = { R.id.application_one, R.id.application_two, R.id.application_three, R.id.application_four };
        int[] whenAppRowIds = { R.id.application_recent_one, R.id.application_recent_two, R.id.application_recent_three, R.id.application_recent_four };

        while (largest.size() > appRowIds.length) {
            largest.remove(largest.size() - 1);
        }

        PackageManager packageManager = context.getPackageManager();

        if (largest.size() > 0) {
            for (int appRowId : appRowIds) {
                View row = cardContent.findViewById(appRowId);

                row.setVisibility(View.GONE);
            }

            for (int i = 0; i < appRowIds.length && i < largest.size(); i++) {
                HashMap<String, Double> appDef = largest.get(i);
                int appRowId = appRowIds[i];

                View row = cardContent.findViewById(appRowId);
                row.setVisibility(View.VISIBLE);

                TextView appName = row.findViewById(R.id.app_name);
                ImageView appIcon = row.findViewById(R.id.application_icon);

                View usedDuration = row.findViewById(R.id.app_used_duration);
                View remainderDuration = row.findViewById(R.id.app_remaining_duration);

                for (String key : appDef.keySet()) {
                    double duration = appDef.get(key);

                    double minutes = duration / (1000 * 60);

                    try {
                        String name = packageManager.getApplicationLabel(packageManager.getApplicationInfo(key, PackageManager.GET_META_DATA)).toString();

                        appName.setText(context.getString(R.string.generator_foreground_application_app_name_duration, name, minutes));
                        Drawable icon = packageManager.getApplicationIcon(key);
                        appIcon.setImageDrawable(icon);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();

                        appName.setText(context.getString(R.string.generator_foreground_application_app_name_duration, key, minutes));
                        appIcon.setImageDrawable(null);
                    }

                    double remainder = largestUsage - duration;

                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) usedDuration.getLayoutParams();
                    params.weight = (float) duration;
                    usedDuration.setLayoutParams(params);

                    params = (LinearLayout.LayoutParams) remainderDuration.getLayoutParams();
                    params.weight = (float) remainder;
                    remainderDuration.setLayoutParams(params);
                }
            }

            for (int i = 0; i < whenAppRowIds.length && i < latest.size(); i++) {
                int appRowId = whenAppRowIds[i];

                String appPackage = latest.get(i);

                while (appPackage == null && i < latest.size() - 1) {
                    i += 1;

                    appPackage = latest.get(i);
                }

                if (appPackage != null) {
                    View row = cardContent.findViewById(appRowId);
                    row.setVisibility(View.VISIBLE);

                    TextView appName = row.findViewById(R.id.app_name);
                    ImageView appIcon = row.findViewById(R.id.application_icon);

                    try {
                        String name = packageManager.getApplicationLabel(packageManager.getApplicationInfo(appPackage, PackageManager.GET_META_DATA)).toString();

                        appName.setText(name);
                        Drawable icon = packageManager.getApplicationIcon(appPackage);
                        appIcon.setImageDrawable(icon);
                    } catch (PackageManager.NameNotFoundException e) {
                        appName.setText(appPackage);
                        appIcon.setImageDrawable(null);
                    }

                    TextView appWhen = row.findViewById(R.id.app_last_used);
                    appWhen.setText(Generator.formatTimestamp(context, appWhens.get(appPackage) / 1000.0));
                }
            }

            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

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

        long retentionPeriod = prefs.getLong(ForegroundApplication.DATA_RETENTION_PERIOD, ForegroundApplication.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = ForegroundApplication.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(ForegroundApplication.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(ForegroundApplication.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_foreground_application, parent, false);
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        ForegroundApplication me = ForegroundApplication.getInstance(context);

        if (me.mLastTimestamp == 0) {
            Cursor c = me.mDatabase.query(ForegroundApplication.TABLE_HISTORY, null, null, null, null, null, ForegroundApplication.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                me.mLastTimestamp = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));
            }

            c.close();
        }

        return me.mLastTimestamp;
    }

    @Override
    public String getIdentifier() {
        return ForegroundApplication.GENERATOR_IDENTIFIER;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ForegroundApplication.ENABLED, true);
        e.apply();

        try {
            if (config.has("sample-interval")) {
                this.setSampleInterval(config.getLong("sample-interval"));

                config.remove("sample-interval");
            }

            if (config.has("included-categories")) {
                JSONArray categories = config.getJSONArray("included-categories");

                for (int i = 0; i < categories.length(); i++) {
                    String category = categories.getString(i);

                    this.enableCategory(category);
                }

                config.remove("included-categories");
            }

            if (config.has("excluded-categories")) {
                JSONArray categories = config.getJSONArray("excluded-categories");

                for (int i = 0; i < categories.length(); i++) {
                    String category = categories.getString(i);

                    this.disableCategory(category);
                }

                config.remove("excluded-categories");
            }

            if (config.has("included-apps")) {
                JSONArray apps = config.getJSONArray("included-apps");

                for (int i = 0; i < apps.length(); i++) {
                    String app = apps.getString(i);

                    this.enableApp(app);
                }

                config.remove("included-apps");
            }

            if (config.has("excluded-apps")) {
                JSONArray apps = config.getJSONArray("excluded-apps");

                for (int i = 0; i < apps.length(); i++) {
                    String app = apps.getString(i);

                    this.disableApp(app);
                }

                config.remove("excluded-apps");
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    private void setAppEnabled(String packageName, boolean enabled) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        HashSet<String> disabledApps = new HashSet<>();
        disabledApps.addAll(prefs.getStringSet(ForegroundApplication.DISABLED_APPS, new HashSet<>()));

        HashSet<String> enabledApps = new HashSet<>();
        enabledApps.addAll(prefs.getStringSet(ForegroundApplication.ENABLED_APPS, new HashSet<>()));

        if (enabled) {
            if (disabledApps.contains(packageName)) {
                disabledApps.remove(packageName);
            }

            enabledApps.add(packageName);
        } else {
            if (enabledApps.contains(packageName)) {
                enabledApps.remove(packageName);
            }

            disabledApps.add(packageName);
        }

        e.putStringSet(ForegroundApplication.DISABLED_APPS, disabledApps);
        e.putStringSet(ForegroundApplication.ENABLED_APPS, enabledApps);

        e.commit();
    }

    private void setCategoryEnabled(String category, boolean enabled) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        HashSet<String> disabledCategories = new HashSet<>();
        disabledCategories.addAll(prefs.getStringSet(ForegroundApplication.DISABLED_CATEGORIES, new HashSet<>()));

        HashSet<String> enabledCategories = new HashSet<>();
        enabledCategories.addAll(prefs.getStringSet(ForegroundApplication.ENABLED_CATEGORIES, new HashSet<>()));

        if (enabled) {
            if (disabledCategories.contains(category)) {
                disabledCategories.remove(category);
            }

            enabledCategories.add(category);
        } else {
            if (enabledCategories.contains(category)) {
                enabledCategories.remove(category);
            }

            disabledCategories.add(category);
        }

        e.putStringSet(ForegroundApplication.DISABLED_CATEGORIES, disabledCategories);
        e.putStringSet(ForegroundApplication.ENABLED_CATEGORIES, enabledCategories);

        e.commit();
    }

    public void disableApp(String packageName) {
        this.setAppEnabled(packageName, false);
    }

    public void enableApp(String packageName) {
        this.setAppEnabled(packageName, true);
    }

    public boolean isAppEnabled(String process) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        Set<String> disabledApps = prefs.getStringSet(ForegroundApplication.DISABLED_APPS, new HashSet<>());

        if (disabledApps.contains(process)) {
            return false;
        }

        if (disabledApps.contains("*")) {
            Set<String> enabledApps = prefs.getStringSet(ForegroundApplication.ENABLED_APPS, new HashSet<>());

            return enabledApps.contains(process);
        }

        String category = this.mCategoryCache.get(process);

        if (category == null) {
            category = this.fetchCategory(process);
        }

        return this.isCategoryEnabled(category);
    }

    public boolean isCategoryEnabled(String category) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        Set<String> disabledCategories = prefs.getStringSet(ForegroundApplication.DISABLED_CATEGORIES, new HashSet<>());

        if (disabledCategories.contains(category)) {
            return false;
        }

        if (disabledCategories.contains("*")) {
            Set<String> enabledCategories = prefs.getStringSet(ForegroundApplication.ENABLED_CATEGORIES, new HashSet<>());

            return enabledCategories.contains(category);
        }

        return true;
    }

    public void disableCategory(String category) {
        this.setCategoryEnabled(category, false);
    }

    public void enableCategory(String category) {
        this.setCategoryEnabled(category, true);
    }

    public String obscureIdentifier(String identifier) {
        if (this.mIdentifierCache.containsKey(identifier)) {
            return this.mIdentifierCache.get(identifier);
        }

        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        String obscureSeed = prefs.getString(ForegroundApplication.OBSCURE_SEED, null);

        if (obscureSeed == null) {
            obscureSeed = RandomStringUtils.randomAlphanumeric(64);

            SharedPreferences.Editor e = prefs.edit();
            e.putString(ForegroundApplication.OBSCURE_SEED, obscureSeed);
            e.apply();
        }

        String obscured = Toolbox.hash(identifier + obscureSeed);

        this.mIdentifierCache.put(identifier, obscured);

        return obscured;
    }

    public List<ForegroundApplicationUsage> fetchUsagesBetween(long start, long end, boolean screenActive) {
        ArrayList<ForegroundApplication.ForegroundApplicationUsage> usages = new ArrayList<>();

        if (this.mDatabase == null) {
            return usages;
        }

        int isActive = 0;

        if (screenActive) {
            isActive = 1;
        }

        String where = ForegroundApplication.HISTORY_OBSERVED + " >= ? AND " + ForegroundApplication.HISTORY_OBSERVED + " < ? AND " + ForegroundApplication.HISTORY_SCREEN_ACTIVE + " = ?";
        String[] args = { "" + start, "" + end, "" + isActive };

        Cursor c = this.mDatabase.query(ForegroundApplication.TABLE_HISTORY, null, where, args, null, null, ForegroundApplication.HISTORY_OBSERVED);

        while (c.moveToNext()) {
            ForegroundApplicationUsage usage = new ForegroundApplicationUsage();

            usage.duration = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_DURATION));
            usage.start = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));
            usage.packageName = c.getString(c.getColumnIndex(ForegroundApplication.HISTORY_APPLICATION));

            usages.add(usage);
        }

        c.close();

        return usages;
    }

    public int fetchUsageDaysBetween(long start, long end, boolean screenActive) {
        if (this.mDatabase == null) {
            return 0;
        }

        int isActive = 0;

        if (screenActive) {
            isActive = 1;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        start = cal.getTimeInMillis();

        HashSet<String> days = new HashSet<>();
        DateFormat format = android.text.format.DateFormat.getDateFormat(this.mContext);

        String key = "ALL-" + format.format(new Date(start)) + "--" + format.format(new Date(end));

        if (this.mUsageDaysCache.size() > 4096) {
            this.mUsageDaysCache.clear();
        }

        if (this.mUsageDaysCache.containsKey(key)) {
            return this.mUsageDaysCache.get(key);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        final long sampleInterval = prefs.getLong(ForegroundApplication.SAMPLE_INTERVAL, ForegroundApplication.SAMPLE_INTERVAL_DEFAULT);

        String where = ForegroundApplication.HISTORY_OBSERVED + " >= ? AND " + ForegroundApplication.HISTORY_OBSERVED + " < ? AND " + ForegroundApplication.HISTORY_SCREEN_ACTIVE + " = ?";
        String[] columns = { ForegroundApplication.HISTORY_OBSERVED };

        String[] args = {"" + start, "" + end, "" + isActive};

        Cursor c = this.mDatabase.query(ForegroundApplication.TABLE_HISTORY, columns, where, args, null, null, ForegroundApplication.HISTORY_OBSERVED);

        long lastObserved = 0;

        while (c.moveToNext()) {
            long observed = c.getLong(0);

            if ((observed - lastObserved) > sampleInterval) {
                days.add(format.format(new Date(observed)));
                lastObserved = observed;
            }
        }

        c.close();

        int count = days.size();

        if (count > 0) {
            this.mUsageDaysCache.put(key, count);
        }

        return count;
    }

    public long earliestTimestamp() {
        if (this.mEarliestTimestamp == 0) {
            Cursor c = this.queryHistory(null, null, null, ForegroundApplication.HISTORY_OBSERVED);

            if (c.moveToNext()) {
                this.mEarliestTimestamp = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));
            }
        }

        return this.mEarliestTimestamp;
    }

    public long fetchUsageBetween(String packageName, long start, long end, boolean screenActive) {
        String key = packageName + "-"  + start + "-" + end + "-" + screenActive;

        if (this.mUsageDurations.containsKey(key)) {
            return this.mUsageDurations.get(key);
        }

        long duration = 0;

        if (this.mDatabase == null) {
            return duration;
        }

        int isActive = 0;

        if (screenActive) {
            isActive = 1;
        }

        String where = ForegroundApplication.HISTORY_OBSERVED + " >= ? AND " + ForegroundApplication.HISTORY_OBSERVED + " < ? AND " + ForegroundApplication.HISTORY_SCREEN_ACTIVE + " = ? AND " + ForegroundApplication.HISTORY_APPLICATION + " = ?";
        String[] args = { "" + start, "" + end, "" + isActive, packageName };

        if (packageName == null) {
            where = ForegroundApplication.HISTORY_OBSERVED + " >= ? AND " + ForegroundApplication.HISTORY_OBSERVED + " < ? AND " + ForegroundApplication.HISTORY_SCREEN_ACTIVE + " = ?";
            args = new String[3];
            args[0] = "" + start;
            args[1] = "" + end;
            args[2] = "" + isActive;
        }

        String[] columns = { ForegroundApplication.HISTORY_DURATION, ForegroundApplication.HISTORY_OBSERVED };

        Cursor c = this.mDatabase.query(ForegroundApplication.TABLE_HISTORY, columns, where, args, null, null, ForegroundApplication.HISTORY_OBSERVED);

        long observed = 0;

        int durationIndex = c.getColumnIndex(ForegroundApplication.HISTORY_DURATION);
        int observedIndex = c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED);

        // Added check for last observation date to guard against any potential double counting of duplicate samples...

        while (c.moveToNext()) {
            long observation = c.getLong(observedIndex);

            if (observation > observed) {
                duration += c.getLong(durationIndex);
                observed = observation;
            }
        }

        c.close();

        synchronized (this.mUsageDurations) {
            this.mUsageDurations.put(key, duration);
        }

        return duration;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        if (this.mDatabase != null) {
            return this.mDatabase.query(ForegroundApplication.TABLE_HISTORY, cols, where, args, null, null, orderBy);
        } else {
            if (cols == null) {
                cols = new String[0];
            }
        }

        return new MatrixCursor(cols);
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, ForegroundApplication.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }
}
