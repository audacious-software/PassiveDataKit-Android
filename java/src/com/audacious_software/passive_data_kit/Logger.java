package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.content.Context;

import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;

import org.apache.commons.lang3.exception.ExceptionUtils;

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

    public void log(String event) {
        this.log(event, new HashMap<>());
    }

    private static class LoggerHolder {
        @SuppressLint("StaticFieldLeak")
        public static final Logger instance = new Logger();
    }

    @SuppressWarnings("SameReturnValue")
    public static synchronized Logger getInstance(Context context) {
        LoggerHolder.instance.setContext(context.getApplicationContext());

        return LoggerHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }

    public void logThrowable(Throwable t) {
        HashMap<String, Object> payload = new HashMap<>();

        if (t.getMessage() != null) {
            payload.put("message", t.getMessage());
        }

        try {
            payload.put("stack-trace", ExceptionUtils.getStackTrace(t));

            t.printStackTrace();
        } catch (StackOverflowError ex) {
            payload.put("stack-trace", "(Circular Stack Trace)");
        }

        this.log("java-exception", payload);
    }
}
