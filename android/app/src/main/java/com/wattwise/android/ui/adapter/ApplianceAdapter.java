package com.wattwise.android.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wattwise.android.R;
import com.wattwise.android.data.local.entity.ApplianceEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Filas de aparatos con acciones de editar/borrar. Las filas en espera de sync
 * muestran la etiqueta "Pendiente de sincronizar".
 */
public class ApplianceAdapter extends RecyclerView.Adapter<ApplianceAdapter.ApplianceHolder> {

    public interface Listener {
        void onEdit(ApplianceEntity appliance);

        void onDelete(ApplianceEntity appliance);
    }

    private final List<ApplianceEntity> items = new ArrayList<>();
    private final Listener listener;

    public ApplianceAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ApplianceEntity> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ApplianceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_appliance, parent, false);
        return new ApplianceHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ApplianceHolder holder, int position) {
        ApplianceEntity e = items.get(position);
        holder.name.setText(e.name);
        holder.type.setText(typeLabel(holder.itemView.getContext(), e.type));
        holder.meta.setText(metaLine(e));
        holder.pending.setVisibility(e.isPendingSync ? View.VISIBLE : View.GONE);

        holder.edit.setOnClickListener(v -> listener.onEdit(items.get(holder.getBindingAdapterPosition())));
        holder.delete.setOnClickListener(v -> listener.onDelete(items.get(holder.getBindingAdapterPosition())));
    }

    private static String metaLine(ApplianceEntity e) {
        final StringBuilder sb = new StringBuilder();
        if (e.powerWatts != null) {
            sb.append(e.powerWatts).append(" W");
        }
        if (e.avgCycleKwh != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.avgCycleKwh.toPlainString()).append(" kWh");
        }
        if (e.estimatedCycleMinutes != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.estimatedCycleMinutes).append(" min");
        }
        return sb.toString();
    }

    private static String typeLabel(android.content.Context ctx, String type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case "WASHING_MACHINE":
                return ctx.getString(R.string.type_washing_machine);
            case "DISHWASHER":
                return ctx.getString(R.string.type_dishwasher);
            case "EV_CHARGER":
                return ctx.getString(R.string.type_ev_charger);
            case "DRYER":
                return ctx.getString(R.string.type_dryer);
            case "POOL_PUMP":
                return ctx.getString(R.string.type_pool_pump);
            case "AC":
                return ctx.getString(R.string.type_ac);
            default:
                return ctx.getString(R.string.type_other);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ApplianceHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView type;
        final TextView meta;
        final TextView pending;
        final ImageView edit;
        final ImageView delete;

        ApplianceHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txtName);
            type = itemView.findViewById(R.id.txtType);
            meta = itemView.findViewById(R.id.txtMeta);
            pending = itemView.findViewById(R.id.txtPending);
            edit = itemView.findViewById(R.id.btnEdit);
            delete = itemView.findViewById(R.id.btnDelete);
        }
    }
}