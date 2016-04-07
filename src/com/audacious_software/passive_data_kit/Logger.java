package com.audacious_software.passive_data_kit;

import android.content.Context;

import java.util.HashMap;

/**
 * Created by cjkarr on 4/3/2016.
 */
public class Logger {
    private Context mContext = null;

    public void log(String event, HashMap<String, Object> details) {
        // TODO
    }

    private static class LoggerHolder {
        public static Logger instance = new Logger();
    }

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
