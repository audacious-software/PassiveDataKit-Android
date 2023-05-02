package com.audacious_software.passive_data_kit.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;

import com.audacious_software.passive_data_kit.activities.generators.GeneratorsAdapter;
import com.audacious_software.passive_data_kit.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DataDisclosureActivity extends AppCompatActivity {
    @SuppressWarnings("ConstantConditions")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.layout_data_disclosure_pdk);
        this.getSupportActionBar().setTitle(R.string.title_data_disclosure);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FrameLayout dataView = this.findViewById(R.id.data_view);

        GeneratorsAdapter adapter = new GeneratorsAdapter();
        adapter.setContext(this.getApplicationContext());
        adapter.setDataView(dataView);

        RecyclerView listView = this.findViewById(R.id.list_view);

        listView.setLayoutManager(new LinearLayoutManager(this));

        listView.setAdapter(adapter);
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            this.finish();
        }

        return true;
    }
}
