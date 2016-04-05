package com.audacious_software.passive_data_kit;

import android.content.Context;

import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;

import java.util.ArrayList;

public class PassiveDataKit {
    private Context mContext = null;
    private boolean mStarted = false;

    public void start() {
        if (!this.mStarted)
        {
            Generators.getInstance(this.mContext).start();
            Logger.getInstance(this.mContext);

            this.mStarted = true;
        }
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        actions.addAll(Generators.getInstance(context).diagnostics());

        return actions;
    }

    private static class PassiveDataKitHolder {
        public static PassiveDataKit instance = new PassiveDataKit();
    }

    public static PassiveDataKit getInstance(Context context)
    {
        PassiveDataKitHolder.instance.setContext(context.getApplicationContext());

        return PassiveDataKitHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }
}
