package com.audacious_software.passive_data_kit.activities.generators;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

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

public class GeneratorsAdapter extends RecyclerView.Adapter<GeneratorViewHolder> {
    private Context mContext = null;
    private FrameLayout mDataView = null;

    @Override
    public GeneratorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = Generator.fetchDisclosureView(parent);

        return new GeneratorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final GeneratorViewHolder holder, int position) {
        final GeneratorsAdapter me = this;

        List<Class<? extends Generator>> activeGenerators = Generators.getInstance(holder.itemView.getContext()).activeGenerators();

        this.sortGenerators(this.mContext, activeGenerators);

        Class<? extends Generator> generatorClass = activeGenerators.get(position);

        Log.e("PDK", "GENERATOR CLASS: " + generatorClass);

        try {
            Method bindViewHolder = generatorClass.getDeclaredMethod("bindDisclosureViewHolder", GeneratorViewHolder.class);
            bindViewHolder.invoke(null, holder);
        } catch (Exception e) {
//            e.printStackTrace();
            try {
                generatorClass = Generator.class;

                Method bindViewHolder = generatorClass.getDeclaredMethod("bindDisclosureViewHolder", GeneratorViewHolder.class);

                bindViewHolder.invoke(null, holder);
            } catch (NoSuchMethodException e1) {
                Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            } catch (InvocationTargetException e1) {
                Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            } catch (IllegalAccessException e1) {
                Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            }
        }

        final Class<? extends Generator> finalClass = generatorClass;

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                me.mDataView.removeAllViews();

                try {
                    Method bindViewHolder = finalClass.getDeclaredMethod("getDisclosureDataView", GeneratorViewHolder.class);

                    View dataView = (View) bindViewHolder.invoke(null, holder);
                    me.mDataView.addView(dataView);
                } catch (NoSuchMethodException e1) {
                    Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
                } catch (InvocationTargetException e1) {
                    Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
                } catch (IllegalAccessException e1) {
                    Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
                }
            }
        });

        ImageView settingsButton = (ImageView) holder.itemView.findViewById(R.id.button_disclosure_item);

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
