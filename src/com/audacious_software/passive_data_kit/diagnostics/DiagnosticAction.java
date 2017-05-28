package com.audacious_software.passive_data_kit.diagnostics;

public class DiagnosticAction {
    private String mMessage = null;
    private Runnable mAction = null;
    private String mTitle = null;

    public DiagnosticAction(String title, String message, Runnable action) {
        this.mTitle = title;
        this.mMessage = message;
        this.mAction = action;
    }

    public void run() {
        if (this.mAction != null) {
            Thread t = new Thread(this.mAction);

            t.start();
        }
    }

    public String getMessage() {
        return this.mMessage;
    }

    public String getTitle() {
        return this.mTitle;
    }
}
