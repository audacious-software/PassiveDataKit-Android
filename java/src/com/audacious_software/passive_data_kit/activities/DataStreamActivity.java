package com.audacious_software.passive_data_kit.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;

import com.audacious_software.passive_data_kit.activities.generators.DataPointsAdapter;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@SuppressWarnings("PointlessBooleanExpression")
public class DataStreamActivity extends AppCompatActivity implements Generators.GeneratorUpdatedListener {
    private DataPointsAdapter mAdapter = null;
    private Menu mMenu = null;
    private boolean mIsUpdating = false;

    protected int layoutResource() {
        return R.layout.layout_data_stream_pdk;
    }

    @SuppressWarnings("ConstantConditions")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(this.layoutResource());
        this.setTitle(R.string.activity_data_stream);
        this.getSupportActionBar().setSubtitle(this.getResources().getQuantityString(R.plurals.activity_data_stream_subtitle, 0, 0));

        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        this.mAdapter = new DataPointsAdapter();
        this.mAdapter.setContext(this.getApplicationContext());
        this.mAdapter.sortGenerators(true);

        RecyclerView listView = this.findViewById(R.id.list_view);

        listView.setLayoutManager(new LinearLayoutManager(this));

        listView.setAdapter(this.mAdapter);
    }

    protected void onResume() {
        super.onResume();

        Generators.getInstance(this).addNewGeneratorUpdatedListener(this);

        final int count = this.mAdapter.getItemCount();

        Handler mainHandler = new Handler(Looper.getMainLooper());

        final DataStreamActivity me = this;

        mainHandler.post(new Runnable() {
            @SuppressWarnings("ConstantConditions")
            @Override
            public void run() {
                me.getSupportActionBar().setSubtitle(me.getResources().getQuantityString(R.plurals.activity_data_stream_subtitle, count, count));
            }
        });
    }

    protected void onPause() {
        super.onPause();

        Generators.getInstance(this).removeGeneratorUpdatedListener(this);
    }

    @Override
    public void onGeneratorUpdated(final String identifier, long timestamp, Bundle data) {
        final DataStreamActivity me = this;

        if (me.mIsUpdating) {
            return;
        }

        me.mIsUpdating = true;

        me.mAdapter.sortGenerators(false);

        this.runOnUiThread(new Runnable() {
            @SuppressWarnings("ConstantConditions")
            @Override
            public void run() {
                try {
                    me.mAdapter.notifyDataSetChanged(identifier);
                } catch (IllegalStateException e) {
                    // Do nothing - recycler is already updating...
                }

                int count = me.mAdapter.getItemCount();
                me.getSupportActionBar().setSubtitle(me.getResources().getQuantityString(R.plurals.activity_data_stream_subtitle, count, count));

                me.mIsUpdating = false;
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.getMenuInflater().inflate(R.menu.activity_data_stream, menu);

        this.mMenu = menu;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean sortEnabled = prefs.getBoolean(DataPointsAdapter.SORT_BY_UPDATED, DataPointsAdapter.SORT_BY_UPDATED_DEFAULT);

        MenuItem lockedItem = this.mMenu.findItem(R.id.action_pdk_toggle_sort_lock);

        if (sortEnabled) {
            lockedItem.setIcon(R.drawable.ic_pdk_action_unlock);
        } else {
            lockedItem.setIcon(R.drawable.ic_pdk_action_lock);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
            return true;
        } else if (id == R.id.action_pdk_toggle_sort_lock) {
            this.toggleSortLock();
            return true;
        }

        return true;
    }

    private void toggleSortLock() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean sortEnabled = prefs.getBoolean(DataPointsAdapter.SORT_BY_UPDATED, DataPointsAdapter.SORT_BY_UPDATED_DEFAULT);

        MenuItem lockedItem = this.mMenu.findItem(R.id.action_pdk_toggle_sort_lock);

        if (sortEnabled) {
            lockedItem.setIcon(R.drawable.ic_pdk_action_lock);
        } else {
            lockedItem.setIcon(R.drawable.ic_pdk_action_unlock);
        }

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(DataPointsAdapter.SORT_BY_UPDATED, (sortEnabled == false));
        e.apply();

        this.mAdapter.sortGenerators(true);
    }
}
