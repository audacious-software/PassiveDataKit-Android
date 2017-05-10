package com.audacious_software.passive_data_kit.generators.diagnostics;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.DataDisclosureDetailActivity;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.GeneratorViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppEvent extends Generator{
    private static final String GENERATOR_IDENTIFIER = "pdk-app-event";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATABASE_PATH = "pdk-app-event.sqlite";
    private static final int DATABASE_VERSION = 1;

    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_EVENT_NAME = "event_name";
    public static final String HISTORY_EVENT_DETAILS = "event_details";
    public static final String TABLE_HISTORY = "history";

    private static final int CARD_PAGE_SIZE = 8;
    private static final int CARD_MAX_PAGES = 8;

    private static AppEvent sInstance = null;

    private SQLiteDatabase mDatabase = null;

    private int mPage = 0;

    public static AppEvent getInstance(Context context) {
        if (AppEvent.sInstance == null) {
            AppEvent.sInstance = new AppEvent(context.getApplicationContext());
        }

        return AppEvent.sInstance;
    }

    public AppEvent(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        AppEvent.getInstance(context).startGenerator();
    }

    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_app_events);
    }

    private void startGenerator() {
        Generators.getInstance(this.mContext).registerCustomViewClass(AppEvent.GENERATOR_IDENTIFIER, AppEvent.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, AppEvent.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_app_events_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, AppEvent.DATABASE_VERSION);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(AppEvent.ENABLED, AppEvent.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        return (AppEvent.sInstance != null);
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        Class currentClass = new Object() { }.getClass().getEnclosingClass();

        String identifier = currentClass.getCanonicalName();

        TextView generatorLabel = (TextView) holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(AppEvent.getGeneratorTitle(holder.itemView.getContext()));
    }

    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        final AppEvent generator = AppEvent.getInstance(context);

        final ArrayList<Object[]> events = new ArrayList<>();

        Cursor c = generator.mDatabase.query(AppEvent.TABLE_HISTORY, null, null, null, null, null, AppEvent.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardSizer = holder.itemView.findViewById(R.id.card_sizer);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        int eventCount = c.getCount();

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);
            cardSizer.setVisibility(View.INVISIBLE);

            long timestamp = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED)) / 1000;

            dateLabel.setText(Generator.formatTimestamp(context, timestamp));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);
            cardSizer.setVisibility(View.INVISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.moveToPrevious();

        while (c.moveToNext()) {
            String event = c.getString(c.getColumnIndex(AppEvent.HISTORY_EVENT_NAME));
            long timestamp = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED));

            Object[] values = { event, timestamp };

            events.add(values);
        }

        c.close();

        if (eventCount > AppEvent.CARD_PAGE_SIZE * AppEvent.CARD_MAX_PAGES) {
            eventCount = AppEvent.CARD_PAGE_SIZE * AppEvent.CARD_MAX_PAGES;
        }

        final int pages = (int) Math.ceil(((double) eventCount) / AppEvent.CARD_PAGE_SIZE);

        final AppEvent appEvent = AppEvent.getInstance(holder.itemView.getContext());

        ViewPager pager = (ViewPager) holder.itemView.findViewById(R.id.content_pager);

        PagerAdapter adapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return pages;
            }

            @Override
            public boolean isViewFromObject(View view, Object content) {
                return view.getTag().equals(content);
            }

            public void destroyItem(ViewGroup container, int position, Object content) {
                int toRemove = -1;

                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);

                    if (this.isViewFromObject(child, content))
                        toRemove = i;
                }

                if (toRemove >= 0)
                    container.removeViewAt(toRemove);
            }

            public Object instantiateItem(ViewGroup container, int position) {
                LinearLayout list = (LinearLayout) LayoutInflater.from(container.getContext()).inflate(R.layout.card_generator_app_event_page, container, false);

                int listPosition = AppEvent.CARD_PAGE_SIZE * position;

                for (int i = 0; i < AppEvent.CARD_PAGE_SIZE && listPosition + i < events.size(); i++) {
                    Object[] values = events.get(listPosition + i);

                    String event = (String) values[0];
                    long timestamp = (long) values[1];

                    LinearLayout row = null;

                    switch (i) {
                        case 0:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_0);
                            break;
                        case 1:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_1);
                            break;
                        case 2:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_2);
                            break;
                        case 3:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_3);
                            break;
                        case 4:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_4);
                            break;
                        case 5:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_5);
                            break;
                        case 6:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_6);
                            break;
                        default:
                            row = (LinearLayout) list.findViewById(R.id.app_event_row_7);
                            break;
                    }

                    TextView eventName = (TextView) row.findViewById(R.id.app_event_row_event_name);
                    TextView eventWhen = (TextView) row.findViewById(R.id.app_event_row_event_when);

                    eventName.setText(event);
                    eventWhen.setText(Generator.formatTimestamp(context, timestamp / 1000));
                }

                list.setTag("" + position);

                container.addView(list);

                return "" + list.getTag();
            }
        };

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                appEvent.mPage = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        pager.setAdapter(adapter);

        pager.setCurrentItem(appEvent.mPage);
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_app_event, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        AppEvent me = AppEvent.getInstance(context);

        Cursor c = me.mDatabase.query(AppEvent.TABLE_HISTORY, null, null, null, null, null, AppEvent.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(AppEvent.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    public boolean logEvent(String eventName, Map<String, ? extends Object> eventDetails) {
        try {
            long now = System.currentTimeMillis();

            ContentValues values = new ContentValues();
            values.put(AppEvent.HISTORY_OBSERVED, now);
            values.put(AppEvent.HISTORY_EVENT_NAME, eventName);

            Bundle detailsBundle = new Bundle();
            JSONObject detailsJson = new JSONObject();

            for (String key : eventDetails.keySet()) {
                Object value = eventDetails.get(key);

                if (value instanceof Double) {
                    Double doubleValue = ((Double) value);

                    detailsBundle.putDouble(key, doubleValue);
                    detailsJson.put(key, doubleValue.doubleValue());
                } else if (value instanceof  Float) {
                    Float floatValue = ((Float) value);

                    detailsBundle.putDouble(key, floatValue.doubleValue());
                    detailsJson.put(key, floatValue.doubleValue());
                } else if (value instanceof  Long) {
                    Long longValue = ((Long) value);

                    detailsBundle.putLong(key, longValue.longValue());
                    detailsJson.put(key, longValue.longValue());
                } else if (value instanceof  Integer) {
                    Integer intValue = ((Integer) value);

                    detailsBundle.putLong(key, intValue.longValue());
                    detailsJson.put(key, intValue.longValue());
                } else if (value instanceof String) {
                    detailsBundle.putString(key, value.toString());
                    detailsJson.put(key, value.toString());
                } else if (value instanceof Boolean) {
                    detailsBundle.putBoolean(key, ((Boolean) value).booleanValue());
                    detailsJson.put(key, ((Boolean) value).booleanValue());
                } else {
                    detailsBundle.putString(key, "Unknown Class: " + value.getClass().getCanonicalName());
                    detailsJson.put(key, "Unknown Class: " + value.getClass().getCanonicalName());
                }
            }

            values.put(AppEvent.HISTORY_EVENT_DETAILS, detailsJson.toString(2));

            this.mDatabase.insert(AppEvent.TABLE_HISTORY, null, values);

            Bundle update = new Bundle();
            update.putLong(AppEvent.HISTORY_OBSERVED, now);
            update.putString(AppEvent.HISTORY_EVENT_NAME, values.getAsString(AppEvent.HISTORY_EVENT_NAME));
            update.putBundle(AppEvent.HISTORY_EVENT_DETAILS, detailsBundle);

            Generators.getInstance(this.mContext).notifyGeneratorUpdated(AppEvent.GENERATOR_IDENTIFIER, update);

            return true;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    public void logThrowable(Throwable t) {

    }

    public static List<DataDisclosureDetailActivity.Action> getDisclosureActions(final Context context) {
        List<DataDisclosureDetailActivity.Action> actions = new ArrayList<>();

        DataDisclosureDetailActivity.Action disclosure = new DataDisclosureDetailActivity.Action();

        disclosure.title = context.getString(R.string.label_data_collection_description);
        disclosure.subtitle = context.getString(R.string.label_data_collection_description_more);

        WebView disclosureView = new WebView(context);
        disclosureView.loadUrl("file:///android_asset/html/passive_data_kit/generator_app_events_disclosure.html");

        disclosure.view = disclosureView;

        actions.add(disclosure);

        return actions;
    }

    public static View getDisclosureDataView(final GeneratorViewHolder holder) {
        final Context context = holder.itemView.getContext();

        WebView disclosureView = new WebView(context);
        disclosureView.loadUrl("file:///android_asset/html/passive_data_kit/generator_app_events_disclosure.html");

        return disclosureView;
    }
}
