package com.audacious_software.passive_data_kit.generators;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.pdk.passivedatakit.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class Generators {
    private Context mContext = null;
    private boolean mStarted = false;
    private ArrayList<String> mGenerators = new ArrayList<>();
    private HashSet<String> mActiveGenerators = new HashSet<>();
    private SharedPreferences mSharedPreferences = null;

    public void start() {
        if (!this.mStarted)
        {
            this.mGenerators.clear();

            for (String className : this.mContext.getResources().getStringArray(R.array.pdk_available_generators))
            {
                this.mGenerators.add(className);
            }

            for (String className : this.mContext.getResources().getStringArray(R.array.pdk_app_generators))
            {
                this.mGenerators.add(className);
            }

            for (String className : this.mGenerators)
            {
                try {
                    Class<Generator> probeClass = (Class<Generator>) Class.forName(className);

                    Method isEnabled = probeClass.getDeclaredMethod("isEnabled", Context.class);

                    Boolean enabled = (Boolean) isEnabled.invoke(null, this.mContext);

                    if (enabled) {
                        this.startGenerator(className);
                    }
                    else {
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

            this.mStarted = true;
        }
    }

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

    public ArrayList<DiagnosticAction> diagnostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        for (String className : this.mActiveGenerators) {
            try {
                Class<Generator> generatorClass = (Class<Generator>) Class.forName(className);

                Method diagnostics = generatorClass.getDeclaredMethod("diagnostics", Context.class);
                Collection<DiagnosticAction> generatorActions = (Collection<DiagnosticAction>) diagnostics.invoke(null, this.mContext);

                actions.addAll(generatorActions);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return actions;
    }

    private static class GeneratorsHolder {
        public static Generators instance = new Generators();
    }

    public static Generators getInstance(Context context)
    {
        GeneratorsHolder.instance.setContext(context);

        return GeneratorsHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context.getApplicationContext();
    }
}
