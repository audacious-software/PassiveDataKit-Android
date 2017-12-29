package com.audacious_software.passive_data_kit.activities.generators;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.activities.DataDisclosureDetailActivity;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class GeneratorsAdapter extends RecyclerView.Adapter<GeneratorViewHolder> {
    private Context mContext = null;
    private FrameLayout mDataView = null;

    @Override
    public GeneratorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = Generator.fetchDisclosureView(parent);

        return new GeneratorViewHolder(view);
    }

    @SuppressWarnings("unchecked")
    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(final GeneratorViewHolder holder, int position) {
        final GeneratorsAdapter me = this;

        List<Class<? extends Generator>> activeGenerators = Generators.getInstance(holder.itemView.getContext()).activeGenerators();

        this.sortGenerators(this.mContext, activeGenerators);

        Class<? extends Generator> generatorClass = activeGenerators.get(position);

        try {
            Method bindViewHolder = generatorClass.getDeclaredMethod("bindDisclosureViewHolder", GeneratorViewHolder.class);
            bindViewHolder.invoke(null, holder);
        } catch (Exception e) {
            TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

            String[] tokens = generatorClass.getName().split(Pattern.quote("."));
            generatorLabel.setText(tokens[tokens.length - 1] + "*");
        }

        final Class<? extends Generator> finalClass = generatorClass;

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                me.mDataView.removeAllViews();

                @SuppressLint("InflateParams") View dataView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.pdk_placeholder_disclosure_view, null);

                //noinspection TryWithIdenticalCatches
                try {
                    Method bindViewHolder = finalClass.getDeclaredMethod("getDisclosureDataView", GeneratorViewHolder.class);

                    dataView = (View) bindViewHolder.invoke(null, holder);
                } catch (NoSuchMethodException e1) {
                    Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
                    e1.printStackTrace();
                } catch (InvocationTargetException e1) {
                    Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
                    e1.printStackTrace();
                } catch (IllegalAccessException e1) {
                    Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
                    e1.printStackTrace();
                }

                me.mDataView.addView(dataView);
            }
        });

        ImageView settingsButton = holder.itemView.findViewById(R.id.button_disclosure_item);

        settingsButton.setVisibility(View.GONE);

        try {
            Method getDisclosureActions = finalClass.getDeclaredMethod("getDisclosureActions", Context.class);

            final List<DataDisclosureDetailActivity.Action> actions = (List<DataDisclosureDetailActivity.Action>) getDisclosureActions.invoke(null, holder.itemView.getContext());

            if (actions.size() > 0) {
                settingsButton.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            // Do nothing - leave invisible...
        }

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(holder.itemView.getContext(), DataDisclosureDetailActivity.class);
                intent.putExtra(DataDisclosureDetailActivity.GENERATOR_CLASS_NAME, finalClass.getCanonicalName());

                holder.itemView.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return Generators.getInstance(null).activeGenerators().size();
    }

    @SuppressWarnings("UnusedParameters")
    private void sortGenerators(final Context context, List<Class<? extends Generator>> generators) {
        Collections.sort(generators, new Comparator<Class<? extends Generator>>() {
            @Override
            public int compare(Class<? extends Generator> one, Class<? extends Generator> two) {
                return one.getName().compareTo(two.getName());
            }
        });
    }

    public int getItemViewType (int position) {
        return 0;
    }

    public void setContext(Context context) {
        this.mContext = context;
    }

    public void setDataView(FrameLayout dataView) {
        this.mDataView = dataView;
    }
}
