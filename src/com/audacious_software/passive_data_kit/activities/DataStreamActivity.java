package com.audacious_software.passive_data_kit.activities;

import android.os.Bundle;
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

public class DataStreamActivity extends AppCompatActivity implements Generators.NewDataPointListener {
    private DataPointsAdapter mAdapter = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.layout_diagnostics_pdk);

        this.mAdapter = new DataPointsAdapter();

        RecyclerView listView = (RecyclerView) this.findViewById(R.id.list_view);

        listView.setLayoutManager(new LinearLayoutManager(this));

        listView.setAdapter(this.mAdapter);
    }

    protected void onResume() {
        super.onResume();

        Generators.getInstance(this).addNewDataPointListener(this);
    }

    protected void onPause() {
        super.onPause();

        Generators.getInstance(this).removeNewDataPointListener(this);
    }

    @Override
    public void onNewDataPoint(String identifier, Bundle data) {
        this.mAdapter.updateDataPoint(identifier, data);
    }
}
