package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.content.Context;

import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;

import java.util.HashMap;
import java.util.Map;

public class Logger {
    private Context mContext = null;

    @SuppressWarnings("SameParameterValue")
    public void log(String event, Map<String, ?> details) {
        if (details == null) {
            details = new HashMap<>();
        }

        AppEvent.getInstance(this.mContext).logEvent(event, details);
    }

    private static class LoggerHolder {
        @SuppressLint("StaticFieldLeak")
        public static final Logger instance = new Logger();
    }

    @SuppressWarnings("SameReturnValue")
    public static Logger getInstance(Context context) {
        LoggerHolder.instance.setContext(context.getApplicationContext());

        return LoggerHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }

    public void logThrowable(Throwable t) {
        t.printStackTrace();
    }
}
