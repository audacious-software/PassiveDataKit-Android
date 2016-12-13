package com.audacious_software.passive_data_kit.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointsAdapter;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import java.util.ArrayList;

public class DataStreamActivity extends AppCompatActivity implements Generators.GeneratorUpdatedListener {
    private DataPointsAdapter mAdapter = null;

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
    public void onGeneratorUpdated(String identifier, Bundle data) {
        Log.e("PDK", "GOT GENERATOR UPDATE: " + identifier + " -- " + data);

        this.mAdapter.notifyDataSetChanged();

        final int count = this.mAdapter.getItemCount();

        Handler mainHandler = new Handler(Looper.getMainLooper());

        final DataStreamActivity me = this;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                me.getSupportActionBar().setSubtitle(me.getResources().getQuantityString(R.plurals.activity_data_stream_subtitle, count, count));
            }
        });

//        RecyclerView listView = (RecyclerView) this.findViewById(R.id.list_view);
//        listView.setAdapter(this.mAdapter);
//        listView.invalidate();
    }
}
