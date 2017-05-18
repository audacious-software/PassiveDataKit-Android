package com.audacious_software.passive_data_kit.activities;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.pdk.passivedatakit.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class DataDisclosureDetailActivity extends AppCompatActivity {
    public static class Action {
        public String title;
        public String subtitle;

        public View view;
    }

    public static final String GENERATOR_CLASS_NAME = "com.audacious_software.passive_data_kit.activities.DataDisclosureDetailActivity.GENERATOR_CLASS_NAME";

    private Class<? extends Generator> mGeneratorClass = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final DataDisclosureDetailActivity me = this;

        this.setContentView(R.layout.layout_data_disclosure_detail_pdk);
        this.getSupportActionBar().setSubtitle(R.string.title_data_disclosure);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try {
            this.mGeneratorClass = (Class<? extends Generator>) Class.forName(this.getIntent().getStringExtra(DataDisclosureDetailActivity.GENERATOR_CLASS_NAME));
        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
        }

        if (this.mGeneratorClass != null) {
            try {
                Method getGeneratorTitle = this.mGeneratorClass.getDeclaredMethod("getGeneratorTitle", Context.class);

                String title = (String) getGeneratorTitle.invoke(null, this);
                this.getSupportActionBar().setTitle(title);

                Method getDisclosureActions = this.mGeneratorClass.getDeclaredMethod("getDisclosureActions", Context.class);

                final List<Action> actions = (List<Action>) getDisclosureActions.invoke(null, this);

                ListView actionsList = (ListView) this.findViewById(R.id.disclosure_actions);
                ArrayAdapter<Action> adapter = new ArrayAdapter<Action>(this, R.layout.row_disclosure_action_pdk, actions) {
                    public View getView (int position, View convertView, ViewGroup parent) {
                        if (convertView == null) {
                            convertView = LayoutInflater.from(me).inflate(R.layout.row_disclosure_action_pdk, null);
                        }

                        Action action = actions.get(position);

                        TextView title = (TextView) convertView.findViewById(R.id.action_title);
                        title.setText(action.title);

                        TextView description = (TextView) convertView.findViewById(R.id.action_description);
                        description.setText(action.subtitle);

                        return convertView;
                    }
                };

                actionsList.setAdapter(adapter);

                actionsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                        Log.e("PDK", "TAPPED: " + position);

                        Action action = actions.get(position);

                        FrameLayout dataView = (FrameLayout) me.findViewById(R.id.data_view);
                        dataView.removeAllViews();

                        if (action.view != null) {
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

                            action.view.setLayoutParams(params);

                            dataView.addView(action.view);
                        }
                    }
                });

                actionsList.performItemClick(null, 0, 0);
            } catch (NoSuchMethodException e1) {
                Logger.getInstance(this).logThrowable(e1);
            } catch (InvocationTargetException e1) {
                Logger.getInstance(this).logThrowable(e1);
            } catch (IllegalAccessException e1) {
                Logger.getInstance(this).logThrowable(e1);
            }
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
        }

        return true;
    }

}
