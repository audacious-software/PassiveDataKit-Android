package com.audacious_software.passive_data_kit.activities.generators;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class DataPointsAdapter extends RecyclerView.Adapter<DataPointViewHolder> {
    public static final String SORT_BY_UPDATED = "com.audacious_software.passive_data_kit.activities.generators.DataPointsAdapter";
    public static final boolean SORT_BY_UPDATED_DEFAULT = true;

    private Context mContext = null;
    private List<Class<? extends Generator>> mActiveGenerators = null;

    @Override
    public DataPointViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Class<? extends Generator> generatorClass = Generators.getInstance(this.mContext).fetchCustomViewClass(viewType);

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

    private List<Class<? extends Generator>> getGenerators(Context context) {
        if (this.mActiveGenerators == null) {
            this.mActiveGenerators = Generators.getInstance(context).activeGenerators();
        }

        this.sortGenerators();

        return this.mActiveGenerators;
    }

    @Override
    public void onBindViewHolder(final DataPointViewHolder holder, int position) {
        this.getGenerators(holder.itemView.getContext());

        Class<? extends Generator> generatorClass = this.mActiveGenerators.get(position);

        try {
            Method bindViewHolder = generatorClass.getDeclaredMethod("bindViewHolder", DataPointViewHolder.class);
            bindViewHolder.invoke(null, holder);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                generatorClass = Generator.class;

                Method bindViewHolder = generatorClass.getDeclaredMethod("bindViewHolder", DataPointViewHolder.class);

                bindViewHolder.invoke(null, holder);
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
        this.getGenerators(this.mContext);

        return this.mActiveGenerators.size();
    }

    public void sortGenerators() {
        final Context context = this.mContext;

        if (this.mActiveGenerators == null) {
            this.mActiveGenerators = Generators.getInstance(this.mContext).activeGenerators();
        }

        final DataPointsAdapter me = this;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getBoolean(DataPointsAdapter.SORT_BY_UPDATED, DataPointsAdapter.SORT_BY_UPDATED_DEFAULT)) {
            Collections.sort(me.mActiveGenerators, new Comparator<Class<? extends Generator>>() {
                @Override
                public int compare(Class<? extends Generator> one, Class<? extends Generator> two) {
                    long oneUpdated = 0;

                    try {
                        Method oneGenerated = one.getDeclaredMethod("latestPointGenerated", Context.class);

                        oneUpdated = (long) oneGenerated.invoke(null, context);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }

                    long twoUpdated = 0;

                    try {
                        Method twoGenerated = two.getDeclaredMethod("latestPointGenerated", Context.class);

                        twoUpdated = (long) twoGenerated.invoke(null, context);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }

                    if (oneUpdated < twoUpdated) {
                        return 1;
                    } else if (oneUpdated > twoUpdated) {
                        return -1;
                    }

                    return 0;
                }
            });
        }
    }

    public int getItemViewType (int position) {
        this.getGenerators(this.mContext);

        Class<? extends Generator> generatorClass = this.mActiveGenerators.get(position);

        return generatorClass.hashCode();
    }

    public void setContext(Context context) {
        this.mContext = context;
    }
}
