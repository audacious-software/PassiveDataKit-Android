package com.audacious_software.passive_data_kit.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.audacious_software.passive_data_kit.generators.services.AccessibilityEvents;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class AccessibilityEventsService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityEvents.getInstance(this).processEvent(this, event);
    }

    @Override
    public void onInterrupt() {
    }
}

