package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.content.Context;

import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;

import java.io.File;
import java.util.ArrayList;

@SuppressWarnings("PointlessBooleanExpression")
public class PassiveDataKit {
    private static final String STORAGE_PATH = "passive-data-kit";
    private static final String GENERATORS_PATH = "generators";

    private Context mContext = null;
    private boolean mStarted = false;

    public void start() {
        synchronized (this) {
            if (!this.mStarted) {
                this.mStarted = true;

                final PassiveDataKit me = this;

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        Generators.getInstance(me.mContext).start();
                        Logger.getInstance(me.mContext);
                    }
                };

                Thread t = new Thread(r);
                t.start();
            }
        }
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        actions.addAll(Generators.getInstance(context).diagnostics());

        return actions;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File getGeneratorsStorage(Context context) {
        File path = new File(context.getFilesDir(), PassiveDataKit.STORAGE_PATH);
        path = new File(path, PassiveDataKit.GENERATORS_PATH);

        if (path.exists() == false) {
            path.mkdirs();
        }

        return path;
    }

    private static class PassiveDataKitHolder {
        @SuppressLint("StaticFieldLeak")
        public static final PassiveDataKit instance = new PassiveDataKit();
    }

    @SuppressWarnings("SameReturnValue")
    public static PassiveDataKit getInstance(Context context)
    {
        PassiveDataKitHolder.instance.setContext(context.getApplicationContext());

        return PassiveDataKitHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }
}
