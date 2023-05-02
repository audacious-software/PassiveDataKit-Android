package com.audacious_software.passive_data_kit.generators.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.preference.PreferenceManager;

import com.audacious_software.passive_data_kit.accessibility.AccessibilityEventsService;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.R;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AccessibilityEvents extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-accessibility-event";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.AccessibilityEvents.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String CACHED_EVENT_PREFIX = "com.audacious_software.passive_data_kit.generators.services.AccessibilityEvents.CACHED_EVENT_PREFIX: ";

    private static final String TYPE_VIEW_CLICKED = "view_clicked";
    private static final String TYPE_VIEW_LONG_CLICKED = "view_long_clicked";
    private static final String TYPE_VIEW_SELECTED = "view_selected";
    private static final String TYPE_VIEW_FOCUSED = "view_focused";
    private static final String TYPE_VIEW_TEXT_CHANGED = "view_text_changed";
    private static final String TYPE_WINDOW_STATE_CHANGED = "window_state_changed";
    private static final String TYPE_NOTIFICATION_STATE_CHANGED = "notification_state_changed";
    private static final String TYPE_VIEW_HOVER_ENTER = "view_hover_ente";
    private static final String TYPE_VIEW_HOVER_EXIT = "view_hover_exit";
    private static final String TYPE_TOUCH_EXPLORATION_GESTURE_START = "touch_exploration_gesture_start";
    private static final String TYPE_TOUCH_EXPLORATION_GESTURE_END = "touch_exploration_gesture_end";
    private static final String TYPE_WINDOW_CONTENT_CHANGED = "window_content_changed";
    private static final String TYPE_VIEW_SCROLLED = "view_scrolled";
    private static final String TYPE_VIEW_TEXT_SELECTION_CHANGED = "text_selection_changed";
    private static final String TYPE_ANNOUNCEMENT = "announcement";
    private static final String TYPE_VIEW_ACCESSIBILITY_FOCUSED = "accessibility_focused";
    private static final String TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED = "accessibility_focused_cleared";
    private static final String TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY = "view_text_traversed_at_moment_granularity";
    private static final String TYPE_GESTURE_DETECTION_START = "gesture_detection_start";
    private static final String TYPE_GESTURE_DETECTION_END = "gesture_detection_end";
    private static final String TYPE_TOUCH_INTERACTION_START = "touch_interaction_start";
    private static final String TYPE_TOUCH_INTERACTION_END = "touch_interaction_end";
    private static final String TYPE_VIEW_CONTEXT_CLICKED = "context_clicked";
    private static final String TYPE_ASSIST_READING_CONTEXT = "assist_reading_context";

    private static AccessibilityEvents sInstance;

    private static HashMap<String, String> sEventCache = null;
    private static ObjectMapper sObjectMapper;

    public static String generatorIdentifier() {
        return AccessibilityEvents.GENERATOR_IDENTIFIER;
    }

    public static synchronized AccessibilityEvents getInstance(Context context) {
        if (AccessibilityEvents.sInstance == null) {
            AccessibilityEvents.sInstance = new AccessibilityEvents(context.getApplicationContext());
        }

        return AccessibilityEvents.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public AccessibilityEvents(Context context) {
        super(context);

        /*
        synchronized (context.getApplicationContext()) {
            File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

            path = new File(path, AccessibilityEvents.DATABASE_PATH);

            this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

            int version = this.getDatabaseVersion(this.mDatabase);

            switch (version) {
                case 0:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_create_history_table));
                case 1:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_duration));
                case 2:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_screen_active));
                case 3:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_display_state));
                case 4:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_foreground_applications_history_table_add_is_home));
            }

            if (version != ForegroundApplication.DATABASE_VERSION) {
                this.setDatabaseVersion(this.mDatabase, ForegroundApplication.DATABASE_VERSION);
            }
        }
         */
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(AccessibilityEvents.ENABLED, AccessibilityEvents.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(AccessibilityEvents.ENABLED, AccessibilityEvents.ENABLED_DEFAULT);
    }

    public static void start(final Context context) {
        AccessibilityEvents.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    @SuppressLint("InlinedApi")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (AccessibilityEvents.hasPermissions(context) == false) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_accessibility_permission_required_title), context.getString(R.string.diagnostic_accessibility_permission_required), new Runnable() {
                @Override
                public void run() {
                    AccessibilityEvents.fetchPermissions(context);
                }
            }));
        }

        return actions;
    }

    public static void fetchPermissions(final Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static boolean hasPermissions(final Context context) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;

            if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(AccessibilityEventsService.class.getName()))
                return true;
        }

        return false;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {

    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {

    }

    @Override
    public String getIdentifier() {
        return null;
    }

    private String urlFromAddressFields(List<AccessibilityNodeInfo> urlFields, String url) {
        if (!urlFields.isEmpty())
        {
            AccessibilityNodeInfo addressField = urlFields.get(0);
            CharSequence text = addressField.getText();

            if (text != null)
            {
                url = text.toString();
            }
        }
        return url;
    }
    public void processEvent(AccessibilityService service, AccessibilityEvent event) {
        Log.e ("PDK", "Accessibility Event: " + event);

        AccessibilityNodeInfo source = event.getSource();

        if (source == null) {
            return;
        }

        AccessibilityNodeInfo root = service.getRootInActiveWindow();

        if (root != null) {
            source = root;

            String url = "app:" + root.getPackageName();

            if ( "com.android.chrome".equals(root.getPackageName()) ) {
                List<AccessibilityNodeInfo> urlFields = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar");
                url = urlFromAddressFields(urlFields, url);
            } else if ("com.sec.android.app.sbrowser".equals(root.getPackageName())) {
                List<AccessibilityNodeInfo> urlFields = root.findAccessibilityNodeInfosByViewId("com.sec.android.app.sbrowser:id/location_bar_edit_text");
                url = urlFromAddressFields(urlFields, url);
            } else if ("org.mozilla.firefox".equals(root.getPackageName())) {
                List<AccessibilityNodeInfo> urlFields = root.findAccessibilityNodeInfosByViewId("org.mozilla.firefox:id/mozac_browser_toolbar_url_view");
                url = urlFromAddressFields(urlFields, url);
            } else if ("com.android.browser".equals(root.getPackageName())) {
                List<AccessibilityNodeInfo> urlFields = root.findAccessibilityNodeInfosByViewId("com.android.browser:id/url");
                url = urlFromAddressFields(urlFields, url);
            } else if ("com.instagram.android".equals(root.getPackageName())) {
                List<AccessibilityNodeInfo> urlFields = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/ig_browser_text_subtitle");
                url = urlFromAddressFields(urlFields, url);
            }

            Log.e("PDK", "URL[" + root.getPackageName() + "]: " + url);
        }

        try {
            String eventType = "Unknown";

            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_GESTURE_DETECTION_START:
                    eventType = AccessibilityEvents.TYPE_GESTURE_DETECTION_START;
                    break;
                case AccessibilityEvent.TYPE_GESTURE_DETECTION_END:
                    eventType = AccessibilityEvents.TYPE_GESTURE_DETECTION_END;
                    break;
                case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
                    eventType = AccessibilityEvents.TYPE_TOUCH_INTERACTION_START;
                    break;
                case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
                    eventType = AccessibilityEvents.TYPE_TOUCH_INTERACTION_END;
                    break;
                case AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED:
                    eventType = AccessibilityEvents.TYPE_VIEW_CONTEXT_CLICKED;
                    break;
                case AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT:
                    eventType = AccessibilityEvents.TYPE_ASSIST_READING_CONTEXT;
                    break;
                case AccessibilityEvent.TYPE_VIEW_CLICKED:
                    eventType = AccessibilityEvents.TYPE_VIEW_CLICKED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                    eventType = AccessibilityEvents.TYPE_VIEW_LONG_CLICKED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_SELECTED:
                    eventType = AccessibilityEvents.TYPE_VIEW_SELECTED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                    eventType = AccessibilityEvents.TYPE_VIEW_FOCUSED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                    eventType = AccessibilityEvents.TYPE_VIEW_TEXT_CHANGED;
                    break;
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    eventType = AccessibilityEvents.TYPE_WINDOW_STATE_CHANGED;
                    break;
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    eventType = AccessibilityEvents.TYPE_NOTIFICATION_STATE_CHANGED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                    eventType = AccessibilityEvents.TYPE_VIEW_HOVER_ENTER;
                    break;
                case AccessibilityEvent.TYPE_VIEW_HOVER_EXIT:
                    eventType = AccessibilityEvents.TYPE_VIEW_HOVER_EXIT;
                    break;
                case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
                    eventType = AccessibilityEvents.TYPE_TOUCH_EXPLORATION_GESTURE_START;
                    break;
                case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END:
                    eventType = AccessibilityEvents.TYPE_TOUCH_EXPLORATION_GESTURE_END;
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    eventType = AccessibilityEvents.TYPE_WINDOW_CONTENT_CHANGED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                    eventType = AccessibilityEvents.TYPE_VIEW_SCROLLED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                    eventType = AccessibilityEvents.TYPE_VIEW_TEXT_SELECTION_CHANGED;
                    break;
                case AccessibilityEvent.TYPE_ANNOUNCEMENT:
                    eventType = AccessibilityEvents.TYPE_ANNOUNCEMENT;
                    break;
                case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
                    eventType = AccessibilityEvents.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
                    eventType = AccessibilityEvents.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;
                    break;
                case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
                    eventType = AccessibilityEvents.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;
                    break;
                default:
                    eventType = "Unknown: " + event.getEventType();
                    break;
            }

            JSONObject jsonNode = AccessibilityEvents.convertToJSON(source);
            jsonNode.put("event_type", eventType);
            jsonNode.put("package", source.getPackageName());
            jsonNode.put("window_id", source.getWindowId());

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
            SharedPreferences.Editor editor = prefs.edit();

            if (AccessibilityEvents.sEventCache == null) {
                AccessibilityEvents.sEventCache = new HashMap<>();
            }

            if (AccessibilityEvents.sObjectMapper == null) {
                AccessibilityEvents.sObjectMapper = new ObjectMapper();
            }

            String cachedString = AccessibilityEvents.sEventCache.get(eventType);

            if (cachedString == null) {
                cachedString = prefs.getString(AccessibilityEvents.CACHED_EVENT_PREFIX + eventType, "{}");
            }

            boolean updateCache = false;

            try {
                JsonNode cachedNode = AccessibilityEvents.sObjectMapper.readTree(cachedString);
                JsonNode newNode = AccessibilityEvents.sObjectMapper.readTree(jsonNode.toString());

                JsonPatch patch = JsonDiff.asJsonPatch(cachedNode, newNode);

                if (cachedNode.equals(newNode) == false) {
                    Bundle update = AccessibilityEvents.convertToBundle(source);
                    update.putString("event_type", eventType);

                    update.putCharSequence("package", source.getPackageName());
                    update.putInt("window_id", source.getWindowId());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AccessibilityWindowInfo window = source.getWindow();

                        if (window != null) {
                            Bundle windowObj = new Bundle();

                            windowObj.putBoolean("active", window.isActive());
                            windowObj.putBoolean("focused", window.isFocused());

                            switch (window.getType()) {
                                case AccessibilityWindowInfo.TYPE_APPLICATION:
                                    windowObj.putString("type", "application");
                                    break;
                                case AccessibilityWindowInfo.TYPE_INPUT_METHOD:
                                    windowObj.putString("type", "input_method");
                                    break;
                                case AccessibilityWindowInfo.TYPE_SYSTEM:
                                    windowObj.putString("type", "system");
                                    break;
                                case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
                                    windowObj.putString("type", "accessibilty_overlay");
                                    break;
                                default:
                                    windowObj.putString("type", "Unknown: " + window.getType());
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                windowObj.putCharSequence("title", window.getTitle());
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                windowObj.putBoolean("picture_in_picture", window.isInPictureInPictureMode());
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                windowObj.putInt("display_id", window.getDisplayId());

                                Region region = new Region();
                                window.getRegionInScreen(region);

                                Rect bounds = region.getBounds();

                                Bundle position = new Bundle();
                                position.putInt("left", bounds.left);
                                position.putInt("top", bounds.top);
                                position.putInt("width", bounds.right - bounds.left);
                                position.putInt("height", bounds.bottom - bounds.top);

                                update.putParcelable("window_position", position);
                            }
                        }
                    }

                    Log.e("PDK", "DIFF: " + patch);

                    Generators.getInstance(this.mContext).notifyGeneratorUpdated(AccessibilityEvents.GENERATOR_IDENTIFIER, update);

                    updateCache = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (updateCache) {
                String newEventString = jsonNode.toString();

                AccessibilityEvents.sEventCache.put(eventType, newEventString);

                editor.putString(AccessibilityEvents.CACHED_EVENT_PREFIX + eventType, newEventString);
                editor.apply();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static Bundle convertToBundle(AccessibilityNodeInfo source) {
        Bundle bundle = new Bundle();

        bundle.putCharSequence("class_name", source.getClassName());
        bundle.putString("resource_id", source.getViewIdResourceName());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bundle.putCharSequence("pane_name", source.getPaneTitle());
        }

        bundle.putCharSequence("text", source.getText());
        // bundle.putBoolean("clickable", source.isClickable());
        // bundle.putBoolean("editable", source.isEditable());
        bundle.putCharSequence("content_description", source.getContentDescription());

        /*
        Bundle extras = source.getExtras();

        if (extras.isEmpty() == false) {
            bundle.putParcelable("extras", extras);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bundle.putCharSequence("state_description", source.getStateDescription());
        }

        Rect screenCoordinates = new Rect();

        source.getBoundsInScreen(screenCoordinates);

        if (screenCoordinates.isEmpty() == false) {
            Bundle coordinates = new Bundle();
            coordinates.putInt("left", screenCoordinates.left);
            coordinates.putInt("top", screenCoordinates.top);
            coordinates.putInt("width", screenCoordinates.right - screenCoordinates.left);
            coordinates.putInt("height", screenCoordinates.bottom - screenCoordinates.top);

            bundle.putParcelable("position", coordinates);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bundle.putCharSequence("tooltip", source.getTooltipText());
            bundle.putCharSequence("hint", source.getHintText());
        }

        String[] clearEmpty = {
                "text",
                "hint",
                "tooltip",
                "content_description",
        };

        for (String key : clearEmpty) {
            if (bundle.containsKey(key)) {
                CharSequence value = bundle.getCharSequence(key);

                if (value == null || value.length() == 0) {
                    bundle.remove(key);
                }
            }
        }

        String[] clearFalse = {
                "clickable",
                "editable",
        };

        for (String key : clearFalse) {
            if (bundle.containsKey(key)) {
                boolean value = bundle.getBoolean(key);

                if (value == false) {
                    bundle.remove(key);
                }
            }
        } */

        ArrayList<Bundle> children = new ArrayList<>();

        for (int i = 0; i < source.getChildCount(); i++) {
            AccessibilityNodeInfo child = source.getChild(i);

            if (child != null) {
                Bundle childBundle = AccessibilityEvents.convertToBundle(child);

                if (childBundle != null) {
                    children.add(childBundle);
                }
            }
        }

        if (children.size() > 0) {
            bundle.putParcelableArrayList("children", children);
        }

        return bundle;
    }

    private static JSONObject convertToJSON(AccessibilityNodeInfo source) throws JSONException {
        JSONObject jsonObj = new JSONObject();

        jsonObj.put("class_name", source.getClassName());
        jsonObj.put("resource_id", source.getViewIdResourceName());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            jsonObj.put("pane_name", source.getPaneTitle());
        }

        jsonObj.put("text", source.getText());
        jsonObj.put("clickable", source.isClickable());
        jsonObj.put("editable", source.isEditable());
        jsonObj.put("content_description", source.getContentDescription());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            jsonObj.put("state_description", source.getStateDescription());
        }

        Rect screenCoordinates = new Rect();

        source.getBoundsInScreen(screenCoordinates);

        if (screenCoordinates.isEmpty() == false) {
            JSONObject coordinates = new JSONObject();
            coordinates.put("left", screenCoordinates.left);
            coordinates.put("top", screenCoordinates.top);
            coordinates.put("width", screenCoordinates.right - screenCoordinates.left);
            coordinates.put("height", screenCoordinates.bottom - screenCoordinates.top);

            jsonObj.put("position", coordinates);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            jsonObj.put("tooltip", source.getTooltipText());
            jsonObj.put("hint", source.getHintText());
        }

        String[] clearEmpty = {
            "text",
            "hint",
            "tooltip",
            "content_description",
        };

        for (String key : clearEmpty) {
            if (jsonObj.has(key)) {
                String value = jsonObj.getString(key);

                if (value.length() == 0) {
                    jsonObj.remove(key);
                }
            }
        }

        String[] clearFalse = {
                "clickable",
                "editable",
        };

        for (String key : clearFalse) {
            if (jsonObj.has(key)) {
                boolean value = jsonObj.getBoolean(key);

                if (value == false) {
                    jsonObj.remove(key);
                }
            }
        }

        JSONArray children = new JSONArray();

        for (int i = 0; i < source.getChildCount(); i++) {
            AccessibilityNodeInfo child = source.getChild(i);

            if (child != null) {
                JSONObject childJSON = AccessibilityEvents.convertToJSON(child);

                if (childJSON != null) {
                    children.put(childJSON);
                }
            }
        }

        if (children.length() > 0) {
            jsonObj.put("children", children);
        }

        return jsonObj;
    }
}
