package com.wattwise.android.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wattwise.android.R;
import com.wattwise.android.data.local.entity.RecommendationEntity;
import com.wattwise.android.data.repository.RecommendationRepository;
import com.wattwise.android.util.PriceUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tarjetas de recomendación: nombre del aparato, ventana recomendada (hora local
 * de Madrid), precio en €/kWh, coste estimado frente al peor caso y el ahorro tanto
 * en € como en %.
 */
public class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.RecHolder> {

    private final List<RecommendationEntity> items = new ArrayList<>();

    public void setItems(List<RecommendationEntity> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation, parent, false);
        return new RecHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecHolder holder, int position) {
        RecommendationEntity e = items.get(position);

        holder.applianceName.setText(e.applianceName);
        String semaphore = e.semaphore == null ? "AMBER" : e.semaphore;
        holder.semaphore.setText(semaphore);
        holder.semaphore.setBackgroundResource(
                "GREEN".equals(semaphore) ? R.drawable.bg_slot_green
                        : "RED".equals(semaphore) ? R.drawable.bg_slot_red
                        : R.drawable.bg_slot_amber);

        LocalDateTime start = RecommendationRepository.parseStart(e);
        LocalDateTime end = RecommendationRepository.parseEnd(e);
        holder.window.setText(holder.itemView.getContext().getString(R.string.recommendation_window)
                + ": " + PriceUtils.windowLabel(start, end));
        holder.recommendedPrice.setText(
                holder.itemView.getContext().getString(R.string.recommendation_price)
                        + " · " + PriceUtils.formatPriceEuro(e.recommendedPriceEurPerKwh));

        holder.cost.setText(holder.itemView.getContext().getString(R.string.estimated_cost)
                + ": " + PriceUtils.formatEuros(e.estimatedCostEur));
        holder.worstCase.setText(holder.itemView.getContext().getString(R.string.worst_case)
                + ": " + PriceUtils.formatEuros(e.worstCaseCostEur));
        holder.savings.setText(holder.itemView.getContext().getString(R.string.savings_percent,
                PriceUtils.formatEuros(e.estimatedSavingsEur),
                PriceUtils.formatPercent(e.savingsPercentage)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class RecHolder extends RecyclerView.ViewHolder {
        final TextView applianceName;
        final TextView semaphore;
        final TextView window;
        final TextView recommendedPrice;
        final TextView cost;
        final TextView worstCase;
        final TextView savings;

        RecHolder(@NonNull View itemView) {
            super(itemView);
            applianceName = itemView.findViewById(R.id.txtApplianceName);
            semaphore = itemView.findViewById(R.id.txtSemaphore);
            window = itemView.findViewById(R.id.txtWindow);
            recommendedPrice = itemView.findViewById(R.id.txtRecommendedPrice);
            cost = itemView.findViewById(R.id.txtCost);
            worstCase = itemView.findViewById(R.id.txtWorstCase);
            savings = itemView.findViewById(R.id.txtSavings);
        }
    }
}