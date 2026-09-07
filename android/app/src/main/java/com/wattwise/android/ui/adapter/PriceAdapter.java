package com.wattwise.android.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wattwise.android.R;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.util.PriceUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the HOY / MAÑANA price slots as a fully colour-coded list: the row
 * background and the leading dot take the traffic-light colour, with the slot
 * time range on the left and the €/kWh total (taxes included) on the right.
 */
public class PriceAdapter extends RecyclerView.Adapter<PriceAdapter.PriceHolder> {

    private final List<PriceDto> slots = new ArrayList<>();

    public void setSlots(List<PriceDto> newSlots) {
        slots.clear();
        if (newSlots != null) {
            slots.addAll(newSlots);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PriceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_price, parent, false);
        return new PriceHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PriceHolder holder, int position) {
        PriceDto slot = slots.get(position);
        String start = PriceUtils.slotStartLabel(slot.getTimestamp());
        String end = PriceUtils.slotEndLabel(slot.getTimestamp());
        holder.time.setText(start + " – " + end);
        holder.price.setText(PriceUtils.formatPrice(slot.getTotalEurPerKwh()));

        int res = backgroundFor(slot.getColor());
        holder.itemView.setBackgroundResource(res);
        holder.dot.setBackgroundResource(res);
    }

    private static int backgroundFor(String color) {
        if ("GREEN".equals(color)) {
            return R.drawable.bg_slot_green;
        } else if ("RED".equals(color)) {
            return R.drawable.bg_slot_red;
        }
        return R.drawable.bg_slot_amber;
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }

    static final class PriceHolder extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView price;
        final View dot;

        PriceHolder(@NonNull View itemView) {
            super(itemView);
            time = itemView.findViewById(R.id.txtTimeRange);
            price = itemView.findViewById(R.id.txtPrice);
            dot = itemView.findViewById(R.id.slotDot);
        }
    }
}