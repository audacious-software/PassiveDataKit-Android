package com.audacious_software.passive_data_kit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import com.audacious_software.passive_data_kit.activities.generators.DataPointsAdapter;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import java.util.ArrayList;

public class DataStreamActivity extends AppCompatActivity implements Generators.GeneratorUpdatedListener {
    private DataPointsAdapter mAdapter = null;
    private Menu mMenu = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.layout_data_stream_pdk);
        this.setTitle(R.string.activity_data_stream);
        this.getSupportActionBar().setSubtitle(this.getResources().getQuantityString(R.plurals.activity_data_stream_subtitle, 0, 0));

        this.mAdapter = new DataPointsAdapter();
        this.mAdapter.setContext(this.getApplicationContext());

        RecyclerView listView = (RecyclerView) this.findViewById(R.id.list_view);

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
    public void onGeneratorUpdated(String identifier, long timestamp, Bundle data) {
        final DataStreamActivity me = this;

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                me.mAdapter.notifyDataSetChanged();
                int count = me.mAdapter.getItemCount();
                me.getSupportActionBar().setSubtitle(me.getResources().getQuantityString(R.plurals.activity_data_stream_subtitle, count, count));
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.getMenuInflater().inflate(R.menu.activity_data_stream, menu);

        this.mMenu = menu;

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_pdk_toggle_sort_lock) {
            this.toggleSortLock();

            return true;
        }

        return true;
    }

    private void toggleSortLock() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean locked = prefs.getBoolean(DataPointsAdapter.SORT_BY_UPDATED, DataPointsAdapter.SORT_BY_UPDATED_DEFAULT);

        MenuItem lockedItem = this.mMenu.findItem(R.id.action_pdk_toggle_sort_lock);

        if (locked) {
            lockedItem.setIcon(R.drawable.ic_pdk_action_unlock);
        } else {
            lockedItem.setIcon(R.drawable.ic_pdk_action_lock);
        }

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(DataPointsAdapter.SORT_BY_UPDATED, (locked == false));
        e.apply();

        this.mAdapter.notifyDataSetChanged();
    }
}
