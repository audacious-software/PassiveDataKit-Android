package com.audacious_software.passive_data_kit.generators.environment;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.diagnostics.SystemStatus;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BluetoothDevices extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-bluetooth-devices";
    private static final String DATABASE_PATH = "pdk-bluetooth-devices.sqlite";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.environment.BluetoothDevices.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.environment.BluetoothDevices.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String LAST_SCAN_STARTED = "com.audacious_software.passive_data_kit.generators.environment.BluetoothDevices.LAST_SCAN_STARTED";

    private static final String PAIR_STATUS_PAIRED = "paired";
    private static final String PAIR_STATUS_PAIRING = "pairing";
    private static final String PAIR_STATUS_NOT_PAIRED = "none";

    private static final String DEVICE_TYPE_CLASSIC = "BR/EDR";
    private static final String DEVICE_TYPE_LE= "LE";
    private static final String DEVICE_TYPE_DUAL = "BR/EDR/LE";
    private static final String DEVICE_TYPE_UNKNOWN = "unknown";

    public static String CLASS_AUDIO_VIDEO_CAMCORDER = "Audio/Video: Camcorder";
    public static String CLASS_AUDIO_VIDEO_CAR_AUDIO = "Audio/Video: Car Audio";
    public static String CLASS_AUDIO_VIDEO_HANDSFREE = "Audio/Video: Handsfree";
    public static String CLASS_AUDIO_VIDEO_HEADPHONES = "Audio/Video: Headphones";
    public static String CLASS_AUDIO_VIDEO_HIFI_AUDIO = "Audio/Video: HiFi Audio";
    public static String CLASS_AUDIO_VIDEO_LOUDSPEAKER = "Audio/Video: Loudspeaker";
    public static String CLASS_AUDIO_VIDEO_MICROPHONE = "Audio/Video: Microphone";
    public static String CLASS_AUDIO_VIDEO_PORTABLE_AUDIO = "Audio/Video: Portable Audio";
    public static String CLASS_AUDIO_VIDEO_SET_TOP_BOX = "Audio/Video: Set Top Box";
    public static String CLASS_AUDIO_VIDEO_UNCATEGORIZED = "Audio/Video: Uncategorized";
    public static String CLASS_AUDIO_VIDEO_VCR = "Audio/Video: VCR";
    public static String CLASS_AUDIO_VIDEO_VIDEO_CAMERA = "Audio/Video: Video Camera";
    public static String CLASS_AUDIO_VIDEO_VIDEO_CONFERENCING = "Audio/Video: Video Conferencing";
    public static String CLASS_AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER = "Audio/Video: Display/Loudspeaker";
    public static String CLASS_AUDIO_VIDEO_VIDEO_GAMING_TOY = "Audio/Video: Gaming Toy";
    public static String CLASS_AUDIO_VIDEO_VIDEO_MONITOR = "Audio/Video: Video Monitor";
    public static String CLASS_AUDIO_VIDEO_WEARABLE_HEADSET = "Audio/Video: Wearable Headset";
    public static String CLASS_COMPUTER_DESKTOP = "Computer: Desktop";
    public static String CLASS_COMPUTER_HANDHELD_PC_PDA = "Computer: Handheld PC/PDA";
    public static String CLASS_COMPUTER_LAPTOP = "Computer: Laptop";
    public static String CLASS_COMPUTER_PALM_SIZE_PC_PDA = "Computer; Palm-Size PC/PDA";
    public static String CLASS_COMPUTER_SERVER = "Computer: Server";
    public static String CLASS_COMPUTER_UNCATEGORIZED = "Computer: Uncategorized";
    public static String CLASS_COMPUTER_WEARABLE = "Computer: Wearable";
    public static String CLASS_HEALTH_BLOOD_PRESSURE = "Health: Blood Pressure";
    public static String CLASS_HEALTH_DATA_DISPLAY = "Health: Data Display";
    public static String CLASS_HEALTH_GLUCOSE = "Health: Glucose";
    public static String CLASS_HEALTH_PULSE_OXIMETER = "Health: Pulse Oximeter";
    public static String CLASS_HEALTH_PULSE_RATE = "Health: Pulse Rate";
    public static String CLASS_HEALTH_THERMOMETER = "Health: Thermometer";
    public static String CLASS_HEALTH_UNCATEGORIZED = "Health: Uncategorized";
    public static String CLASS_HEALTH_WEIGHING = "Health: Weighing";
    public static String CLASS_PHONE_CELLULAR = "Phone: Cellular";
    public static String CLASS_PHONE_CORDLESS = "Phone: Cordless";
    public static String CLASS_PHONE_ISDN = "Phone: ISDN Modem";
    public static String CLASS_PHONE_MODEM_OR_GATEWAY = "Phone: Modem/Gateway";
    public static String CLASS_PHONE_SMART = "Phone: Smartphone";
    public static String CLASS_PHONE_UNCATEGORIZED = "Phone: Uncategorized";
    public static String CLASS_TOY_CONTROLLER = "Toy: Controller";
    public static String CLASS_TOY_DOLL_ACTION_FIGURE = "Toy: Doll/Action Figure";
    public static String CLASS_TOY_GAME = "Toy: Game";
    public static String CLASS_TOY_ROBOT = "Toy: Robot";
    public static String CLASS_TOY_UNCATEGORIZED = "Toy: Uncategorized";
    public static String CLASS_TOY_VEHICLE = "Toy: Vehicle";
    public static String CLASS_WEARABLE_GLASSES = "Wearable: Glasses";
    public static String CLASS_WEARABLE_HELMET = "Wearable: Helmet";
    public static String CLASS_WEARABLE_JACKET = "Wearable: Jacket";
    public static String CLASS_WEARABLE_PAGER = "Wearable: Pager";
    public static String CLASS_WEARABLE_UNCATEGORIZED = "Wearable: Uncategorized";
    public static String CLASS_WEARABLE_WRIST_WATCH = "Wearable: Wrist Watch";
    public static String CLASS_UNKNOWN = "Unknown";

    private static BluetoothDevices sInstance = null;

    private long mUpdateInterval = (15 * 60 * 1000);
    private long mScanDuration = (30 * 1000);

    private long mThrottleInterval = (5 * 60 * 1000);

    private SQLiteDatabase mDatabase = null;
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_NAME = "name";
    public static final String HISTORY_ADDRESS = "address";
    public static final String HISTORY_DEVICE_CLASS = "device_class";
    public static final String HISTORY_PAIR_STATUS = "pair_status";
    public static final String HISTORY_DEVICE_TYPE = "device_type";

    private long mSunrise = 0;
    private long mSunset = 0;

    private long mLatestTimestamp = -1;

    private Handler mHandler = null;
    private BroadcastReceiver mReceiver = null;

    public static String generatorIdentifier() {
        return BluetoothDevices.GENERATOR_IDENTIFIER;
    }

    public static synchronized BluetoothDevices getInstance(Context context) {
        if (BluetoothDevices.sInstance == null) {
            BluetoothDevices.sInstance = new BluetoothDevices(context.getApplicationContext());
        }

        return BluetoothDevices.sInstance;
    }

    private BluetoothDevices(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        BluetoothDevices.getInstance(context).startGenerator();
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return null;
    }

    private void startGenerator() {
        final BluetoothDevices me = this;

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, BluetoothDevices.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_bluetooth_devices_create_history_table));
        }

        if (version != BluetoothDevices.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, BluetoothDevices.DATABASE_VERSION);
        }

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    BluetoothClass deviceClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);

                    ContentValues values = new ContentValues();
                    values.put(BluetoothDevices.HISTORY_OBSERVED, System.currentTimeMillis());

                    String name = device.getName();

                    if (name == null) {
                        name = "";
                    }

                    values.put(BluetoothDevices.HISTORY_NAME, name);
                    values.put(BluetoothDevices.HISTORY_ADDRESS, device.getAddress());

                    switch (device.getBondState()) {
                        case BluetoothDevice.BOND_BONDED:
                            values.put(BluetoothDevices.HISTORY_PAIR_STATUS, BluetoothDevices.PAIR_STATUS_PAIRED);
                            break;
                        case BluetoothDevice.BOND_BONDING:
                            values.put(BluetoothDevices.HISTORY_PAIR_STATUS, BluetoothDevices.PAIR_STATUS_PAIRING);
                            break;
                        case BluetoothDevice.BOND_NONE:
                            values.put(BluetoothDevices.HISTORY_PAIR_STATUS, BluetoothDevices.PAIR_STATUS_NOT_PAIRED);
                            break;
                    }

                    switch (device.getType()) {
                        case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                            values.put(BluetoothDevices.HISTORY_DEVICE_TYPE, BluetoothDevices.DEVICE_TYPE_CLASSIC);
                            break;
                        case BluetoothDevice.DEVICE_TYPE_LE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_TYPE, BluetoothDevices.DEVICE_TYPE_LE);
                            break;
                        case BluetoothDevice.DEVICE_TYPE_DUAL:
                            values.put(BluetoothDevices.HISTORY_DEVICE_TYPE, BluetoothDevices.DEVICE_TYPE_DUAL);
                            break;
                        case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                            values.put(BluetoothDevices.HISTORY_DEVICE_TYPE, BluetoothDevices.DEVICE_TYPE_UNKNOWN);
                            break;
                    }

                    switch (deviceClass.getDeviceClass()) {
                        case BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_CAMCORDER);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_CAR_AUDIO);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_HANDSFREE);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_HEADPHONES);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_HIFI_AUDIO);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_LOUDSPEAKER);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_MICROPHONE);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_PORTABLE_AUDIO);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_SET_TOP_BOX);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_UNCATEGORIZED);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_VCR:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_VCR);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_VIDEO_CAMERA);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_VIDEO_CONFERENCING);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_VIDEO_GAMING_TOY);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_VIDEO_MONITOR);
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_AUDIO_VIDEO_WEARABLE_HEADSET);
                            break;
                        case BluetoothClass.Device.COMPUTER_DESKTOP:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_DESKTOP);
                            break;
                        case BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_HANDHELD_PC_PDA);
                            break;
                        case BluetoothClass.Device.COMPUTER_LAPTOP:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_LAPTOP);
                            break;
                        case BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_PALM_SIZE_PC_PDA);
                            break;
                        case BluetoothClass.Device.COMPUTER_SERVER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_SERVER);
                            break;
                        case BluetoothClass.Device.COMPUTER_UNCATEGORIZED:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_UNCATEGORIZED);
                            break;
                        case BluetoothClass.Device.COMPUTER_WEARABLE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_COMPUTER_WEARABLE);
                            break;
                        case BluetoothClass.Device.HEALTH_BLOOD_PRESSURE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_BLOOD_PRESSURE);
                            break;
                        case BluetoothClass.Device.HEALTH_DATA_DISPLAY:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_DATA_DISPLAY);
                            break;
                        case BluetoothClass.Device.HEALTH_GLUCOSE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_GLUCOSE);
                            break;
                        case BluetoothClass.Device.HEALTH_PULSE_OXIMETER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_PULSE_OXIMETER);
                            break;
                        case BluetoothClass.Device.HEALTH_PULSE_RATE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_PULSE_RATE);
                            break;
                        case BluetoothClass.Device.HEALTH_THERMOMETER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_THERMOMETER);
                            break;
                        case BluetoothClass.Device.HEALTH_UNCATEGORIZED:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_UNCATEGORIZED);
                            break;
                        case BluetoothClass.Device.HEALTH_WEIGHING:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_HEALTH_WEIGHING);
                            break;
                        case BluetoothClass.Device.PHONE_CELLULAR:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_PHONE_CELLULAR);
                            break;
                        case BluetoothClass.Device.PHONE_CORDLESS:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_PHONE_CORDLESS);
                            break;
                        case BluetoothClass.Device.PHONE_ISDN:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_PHONE_ISDN);
                            break;
                        case BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_PHONE_MODEM_OR_GATEWAY);
                            break;
                        case BluetoothClass.Device.PHONE_SMART:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_PHONE_SMART);
                            break;
                        case BluetoothClass.Device.PHONE_UNCATEGORIZED:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_PHONE_UNCATEGORIZED);
                            break;
                        case BluetoothClass.Device.TOY_CONTROLLER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_TOY_CONTROLLER);
                            break;
                        case BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_TOY_DOLL_ACTION_FIGURE);
                            break;
                        case BluetoothClass.Device.TOY_GAME:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_TOY_GAME);
                            break;
                        case BluetoothClass.Device.TOY_ROBOT:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_TOY_ROBOT);
                            break;
                        case BluetoothClass.Device.TOY_UNCATEGORIZED:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_TOY_UNCATEGORIZED);
                            break;
                        case BluetoothClass.Device.TOY_VEHICLE:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_TOY_VEHICLE);
                            break;
                        case BluetoothClass.Device.WEARABLE_GLASSES:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_WEARABLE_GLASSES);
                            break;
                        case BluetoothClass.Device.WEARABLE_HELMET:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_WEARABLE_HELMET);
                            break;
                        case BluetoothClass.Device.WEARABLE_JACKET:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_WEARABLE_JACKET);
                            break;
                        case BluetoothClass.Device.WEARABLE_PAGER:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_WEARABLE_PAGER);
                            break;
                        case BluetoothClass.Device.WEARABLE_UNCATEGORIZED:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_WEARABLE_UNCATEGORIZED);
                            break;
                        case BluetoothClass.Device.WEARABLE_WRIST_WATCH:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_WEARABLE_WRIST_WATCH);
                            break;
                        default:
                            values.put(BluetoothDevices.HISTORY_DEVICE_CLASS, BluetoothDevices.CLASS_UNKNOWN);
                            break;
                    }

                    Bundle bundle = new Bundle();

                    bundle.putString(BluetoothDevices.HISTORY_NAME, values.getAsString(BluetoothDevices.HISTORY_NAME));
                    bundle.putString(BluetoothDevices.HISTORY_ADDRESS, values.getAsString(BluetoothDevices.HISTORY_ADDRESS));
                    bundle.putString(BluetoothDevices.HISTORY_PAIR_STATUS, values.getAsString(BluetoothDevices.HISTORY_PAIR_STATUS));
                    bundle.putString(BluetoothDevices.HISTORY_DEVICE_TYPE, values.getAsString(BluetoothDevices.HISTORY_DEVICE_TYPE));
                    bundle.putString(BluetoothDevices.HISTORY_DEVICE_CLASS, values.getAsString(BluetoothDevices.HISTORY_DEVICE_CLASS));

                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(BluetoothDevices.GENERATOR_IDENTIFIER, bundle);

                    me.mDatabase.insert(BluetoothDevices.TABLE_HISTORY, null, values);
                }
            }
        };

        final Runnable scanDevices = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                long lastRun = prefs.getLong(BluetoothDevices.LAST_SCAN_STARTED, 0);

                if (now - lastRun > me.mThrottleInterval) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putLong(BluetoothDevices.LAST_SCAN_STARTED, now);
                    e.apply();

                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                    if (bluetoothAdapter != null) {
                        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                            Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                            intent.putExtra(BluetoothDevice.EXTRA_CLASS, device.getBluetoothClass());

                            me.mReceiver.onReceive(me.mContext, intent);
                        }

                        bluetoothAdapter.startDiscovery();

                        me.mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                bluetoothAdapter.cancelDiscovery();
                            }
                        }, me.mScanDuration);
                    }

                    me.mHandler.postDelayed(this, me.mUpdateInterval);
                } else {
                    Log.e("PDK", "Skipping Bluetooth scan: built-in throttle time remaing: " + (now - lastRun) + " ms.");
                }
            }
        };

        Runnable r = new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                me.mHandler = new Handler();

                me.mHandler.postDelayed(scanDevices, 1000);

                Looper.loop();
            }
        };


        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.mContext.registerReceiver(this.mReceiver, filter);

        Thread t = new Thread(r, BluetoothDevices.GENERATOR_IDENTIFIER);
        t.start();

        Generators.getInstance(this.mContext).registerCustomViewClass(BluetoothDevices.GENERATOR_IDENTIFIER, BluetoothDevices.class);
    }

    private void stopGenerator() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        if (this.mHandler != null) {
            this.mHandler.removeCallbacksAndMessages(null);
            this.mHandler.getLooper().quitSafely();
            this.mHandler = null;
        }

        if (this.mReceiver != null) {
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mReceiver = null;
        }

        this.mDatabase.close();
        this.mDatabase = null;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(BluetoothDevices.ENABLED, BluetoothDevices.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static boolean isRunning(Context context) {
        if (BluetoothDevices.sInstance == null) {
            return false;
        }

        return BluetoothDevices.getInstance(context).mReceiver != null;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return BluetoothDevices.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final BluetoothDevices me = this;

        // TODO

        /*
        final Handler handler = new Handler(Looper.getMainLooper());

        if (BluetoothDevices.isEnabled(this.mContext)) {
            int permissionCheck = ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_location_permission_title), me.mContext.getString(R.string.diagnostic_missing_location_permission), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Intent intent = new Intent(me.mContext, RequestPermissionActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra(RequestPermissionActivity.PERMISSION, Manifest.permission.ACCESS_FINE_LOCATION);

                                me.mContext.startActivity(intent);
                            }
                        });
                    }
                }));
            }
        }
        */

        return actions;
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        BluetoothDevices me = BluetoothDevices.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(BluetoothDevices.TABLE_HISTORY, null, null, null, null, null, BluetoothDevices.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(BluetoothDevices.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(BluetoothDevices.DATA_RETENTION_PERIOD, BluetoothDevices.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = BluetoothDevices.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(BluetoothDevices.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(BluetoothDevices.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public void setUpdateInterval(long interval) {
        this.mUpdateInterval = interval;
    }

    public void setScanDuration(long duration) {
        this.mScanDuration = duration;
    }

    @Override
    public String getIdentifier() {
        return BluetoothDevices.GENERATOR_IDENTIFIER;
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, BluetoothDevices.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        if (this.mDatabase != null) {
            return this.mDatabase.query(BluetoothDevices.TABLE_HISTORY, cols, where, args, null, null, orderBy);
        } else {
            if (cols == null) {
                cols = new String[0];
            }
        }

        return new MatrixCursor(cols);
    }

    public void updateConfig(JSONObject config) {
        try {
            if (config.has("update-interval")) {
                this.setUpdateInterval(config.getLong("update-interval"));

                config.remove("update-interval");
            }

            if (config.has("scan-duration")) {
                this.setScanDuration(config.getLong("scan-duration"));

                config.remove("scan-duration");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
