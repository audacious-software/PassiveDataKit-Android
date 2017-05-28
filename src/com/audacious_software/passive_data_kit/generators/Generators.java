package com.audacious_software.passive_data_kit.generators;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.SparseArray;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;
import com.audacious_software.pdk.passivedatakit.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Generators {
    private Context mContext = null;
    private boolean mStarted = false;

    private final ArrayList<String> mGenerators = new ArrayList<>();
    private final HashSet<String> mActiveGenerators = new HashSet<>();
    private SharedPreferences mSharedPreferences = null;
    private final HashMap<String, Class<? extends Generator>> mGeneratorMap = new HashMap<>();
    private final SparseArray<Class<? extends Generator>> mViewTypeMap = new SparseArray<>();
    private final HashSet<GeneratorUpdatedListener> mGeneratorUpdatedListeners = new HashSet<>();
    private final HashMap<String, PowerManager.WakeLock> mWakeLocks = new HashMap<>();

    @SuppressWarnings("unchecked")
    public void start() {
        synchronized (this) {
            if (!this.mStarted) {
                this.mStarted = true;

                this.mGenerators.clear();

                this.mGenerators.add(AppEvent.class.getCanonicalName());

                Collections.addAll(this.mGenerators, this.mContext.getResources().getStringArray(R.array.pdk_available_generators));
                Collections.addAll(this.mGenerators, this.mContext.getResources().getStringArray(R.array.pdk_app_generators));

                for (String className : this.mGenerators) {
                    //noinspection TryWithIdenticalCatches
                    try {
                        Class<Generator> probeClass = (Class<Generator>) Class.forName(className);

                        Method isEnabled = probeClass.getDeclaredMethod("isEnabled", Context.class);

                        Boolean enabled = (Boolean) isEnabled.invoke(null, this.mContext);

                        if (enabled) {
                            this.startGenerator(className);
                        } else {
                            this.stopGenerator(className);
                        }
                    } catch (ClassNotFoundException e) {
                        Logger.getInstance(this.mContext).logThrowable(e);
                    } catch (NoSuchMethodException e) {
                        Logger.getInstance(this.mContext).logThrowable(e);
                    } catch (InvocationTargetException e) {
                        Logger.getInstance(this.mContext).logThrowable(e);
                    } catch (IllegalAccessException e) {
                        Logger.getInstance(this.mContext).logThrowable(e);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void startGenerator(String className) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (!this.mActiveGenerators.contains(className)) {
            Class<Generator> generatorClass = (Class<Generator>) Class.forName(className);

            Method isRunning = generatorClass.getDeclaredMethod("isRunning", Context.class);
            Boolean running = (Boolean) isRunning.invoke(null, this.mContext);

            if (running) {
                this.stopGenerator(className);
            }
            else {
                Method start = generatorClass.getDeclaredMethod("start", Context.class);

                start.invoke(null, this.mContext);

                this.mActiveGenerators.add(className);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void stopGenerator(String className) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (this.mActiveGenerators.contains(className)) {
            Class<Generator> probeClass = (Class<Generator>) Class.forName(className);

            Method stop = probeClass.getDeclaredMethod("stop", probeClass);
            stop.invoke(null, this.mContext);

            this.mActiveGenerators.remove(className);
        }
    }

    public SharedPreferences getSharedPreferences(Context context) {
        if (this.mSharedPreferences == null)
            this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        return this.mSharedPreferences;
    }

    @SuppressWarnings({"TryWithIdenticalCatches", "unchecked"})
    public ArrayList<DiagnosticAction> diagnostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        for (String className : this.mActiveGenerators) {
            try {
                Class<Generator> generatorClass = (Class<Generator>) Class.forName(className);

                Method diagnostics = generatorClass.getDeclaredMethod("diagnostics", Context.class);
                Collection<DiagnosticAction> generatorActions = (Collection<DiagnosticAction>) diagnostics.invoke(null, this.mContext);

                actions.addAll(generatorActions);
            } catch (ClassNotFoundException e) {
//                e.printStackTrace();
            } catch (InvocationTargetException e) {
//                e.printStackTrace();
            } catch (NoSuchMethodException e) {
//                e.printStackTrace();
            } catch (IllegalAccessException e) {
//                e.printStackTrace();
            }
        }

        return actions;
    }

    @SuppressWarnings("SameReturnValue")
    public String getSource() {
        return "unknown-user-please-set-me";
    }

    public String getGeneratorFullName(String identifier) {
        String pdkName = this.mContext.getString(R.string.pdk_name);
        String pdkVersion = this.mContext.getString(R.string.pdk_version);
        String appName = this.mContext.getString(this.mContext.getApplicationInfo().labelRes);

        String version = this.mContext.getString(R.string.unknown_version);

        try {
            PackageInfo pInfo = this.mContext.getPackageManager().getPackageInfo(this.mContext.getPackageName(), 0);

            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Logger.getInstance(this.mContext).logThrowable(e);
        }

        return identifier + ": " + appName + "/" + version + " " + pdkName + "/" + pdkVersion;
    }

    public void registerCustomViewClass(String identifier, Class<? extends Generator> generatorClass) {
        this.mGeneratorMap.put(identifier, generatorClass);
        this.mViewTypeMap.put(generatorClass.hashCode(), generatorClass);
    }

    @SuppressWarnings("unused")
    public Class<? extends Generator> fetchCustomViewClass(String identifier) {
        Class<? extends Generator> generatorClass = this.mGeneratorMap.get(identifier);

        if (generatorClass == null)
            generatorClass = Generator.class;

        return generatorClass;
    }

    public Class<? extends Generator> fetchCustomViewClass(int viewType) {
        Class<? extends Generator> generatorClass = this.mViewTypeMap.get(viewType);

        if (generatorClass == null)
            generatorClass = Generator.class;

        return generatorClass;
    }

    @SuppressWarnings({"TryWithIdenticalCatches", "unchecked", "unused"})
    public Generator getGenerator(String className) {
        if (this.mActiveGenerators.contains(className)) {
            try {
                Class<Generator> probeClass = (Class<Generator>) Class.forName(className);

                Method getInstance = probeClass.getDeclaredMethod("getInstance", Context.class);
                return (Generator) getInstance.invoke(null, this.mContext);
            } catch (ClassNotFoundException e) {
//                e.printStackTrace();
            } catch (NoSuchMethodException e) {
//                e.printStackTrace();
            } catch (IllegalAccessException e) {
//                e.printStackTrace();
            } catch (InvocationTargetException e) {
//                e.printStackTrace();
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Class<? extends Generator>> activeGenerators() {
        ArrayList<Class<? extends Generator>> active = new ArrayList<>();

        for (String className : this.mActiveGenerators) {
            try {
                active.add((Class<? extends Generator>) Class.forName(className));
            } catch (ClassNotFoundException e) {
//                e.printStackTrace();
            }
        }

        return active;
    }

    public void notifyGeneratorUpdated(String identifier, long timestamp, Bundle bundle) {
        for (GeneratorUpdatedListener listener : this.mGeneratorUpdatedListeners) {
            listener.onGeneratorUpdated(identifier, timestamp, bundle);
        }
    }

    public void notifyGeneratorUpdated(String identifier, Bundle bundle) {
        long timestamp = System.currentTimeMillis();

        synchronized(this.mGeneratorUpdatedListeners) {
            for (GeneratorUpdatedListener listener : this.mGeneratorUpdatedListeners) {
                listener.onGeneratorUpdated(identifier, timestamp, bundle);
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    public void acquireWakeLock(String tag, int lockType) {
        this.releaseWakeLock(tag);

        PowerManager power = (PowerManager) this.mContext.getSystemService(Context.POWER_SERVICE);

        PowerManager.WakeLock lock = power.newWakeLock(lockType, tag);

        this.mWakeLocks.put(tag, lock);
    }

    public void releaseWakeLock(String tag) {
        if (this.mWakeLocks.containsKey(tag)) {
            PowerManager.WakeLock lock = this.mWakeLocks.get(tag);

            if (lock.isHeld()) {
                lock.release();
            }

            this.mWakeLocks.remove(tag);
        }
    }

    private static class GeneratorsHolder {
        @SuppressLint("StaticFieldLeak")
        public static final Generators instance = new Generators();
    }

    @SuppressWarnings("SameReturnValue")
    public static Generators getInstance(Context context) {
        if (context != null) {
            GeneratorsHolder.instance.setContext(context);
        }

        return GeneratorsHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public void addNewGeneratorUpdatedListener(Generators.GeneratorUpdatedListener listener) {
        synchronized(this.mGeneratorUpdatedListeners) {
            this.mGeneratorUpdatedListeners.add(listener);
        }
    }

    public void removeGeneratorUpdatedListener(Generators.GeneratorUpdatedListener listener) {
        synchronized (this.mGeneratorUpdatedListeners) {
            this.mGeneratorUpdatedListeners.remove(listener);
        }
    }

    public interface GeneratorUpdatedListener {
        void onGeneratorUpdated(String identifier, long timestamp, Bundle data);
    }
}
