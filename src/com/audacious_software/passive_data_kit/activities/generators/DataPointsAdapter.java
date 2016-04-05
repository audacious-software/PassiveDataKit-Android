package com.audacious_software.passive_data_kit.activities.generators;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

public class DataPointsAdapter extends RecyclerView.Adapter<DataPointViewHolder> {
    private ArrayList<Bundle> mDataPoints = new ArrayList<>();

    @Override
    public DataPointViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Class<? extends Generator> generatorClass = Generators.getInstance(null).fetchCustomViewClass(viewType);

        try {
            Method fetchView = generatorClass.getDeclaredMethod("fetchView", ViewGroup.class);
            View view = (View) fetchView.invoke(null, parent);

            return new DataPointViewHolder(view);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                generatorClass = Generator.class;

                Method fetchView = generatorClass.getDeclaredMethod("fetchView", ViewGroup.class);
                View view = (View) fetchView.invoke(null, parent);

                return new DataPointViewHolder(view);
            } catch (NoSuchMethodException e1) {
                Logger.getInstance(parent.getContext()).logThrowable(e1);
            } catch (InvocationTargetException e1) {
                Logger.getInstance(parent.getContext()).logThrowable(e1);
            } catch (IllegalAccessException e1) {
                Logger.getInstance(parent.getContext()).logThrowable(e1);
            }
        }

        return null;
    }

    @Override
    public void onBindViewHolder(DataPointViewHolder holder, int position) {
        Bundle dataPoint = this.mDataPoints.get(position);
        Class<? extends Generator> generatorClass = Generators.getInstance(null).fetchCustomViewClass(dataPoint.getBundle(Generator.PDK_METADATA).getString(Generator.IDENTIFIER));

        try {
            Method bindViewHolder = generatorClass.getDeclaredMethod("bindViewHolder", DataPointViewHolder.class, Bundle.class);
            bindViewHolder.invoke(null, holder, dataPoint);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                generatorClass = Generator.class;

                Method bindViewHolder = generatorClass.getDeclaredMethod("bindViewHolder", DataPointViewHolder.class, Bundle.class);
                bindViewHolder.invoke(null, holder, dataPoint);
            } catch (NoSuchMethodException e1) {
                Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            } catch (InvocationTargetException e1) {
                Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            } catch (IllegalAccessException e1) {
                Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            }
        }
    }

    @Override
    public int getItemCount() {
        return this.mDataPoints.size();
    }

    public int getItemViewType (int position) {
        Bundle dataPoint = this.mDataPoints.get(position);
        Class<? extends Generator> generatorClass = Generators.getInstance(null).fetchCustomViewClass(dataPoint.getBundle(Generator.PDK_METADATA).getString(Generator.IDENTIFIER));

        return generatorClass.hashCode();
    }

    public void updateDataPoint(String identifier, Bundle data) {
        ArrayList<Bundle> toDelete = new ArrayList<>();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        final DataPointsAdapter me = this;

        for (Bundle bundle : this.mDataPoints) {
            if (bundle.getBundle(Generator.PDK_METADATA).getString(Generator.IDENTIFIER).equals(identifier)) {
                toDelete.add(bundle);
            }
        }

        Collections.reverse(toDelete);

        for (Bundle delete : toDelete) {
            final int position = this.mDataPoints.indexOf(delete);

            this.mDataPoints.remove(position);

//            mainHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    me.notifyItemRemoved(position);
//                }
//            });
        }

        this.mDataPoints.add(0, data);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
//                me.notifyItemInserted(0);
                me.notifyDataSetChanged();
            }
        });
    }
}
