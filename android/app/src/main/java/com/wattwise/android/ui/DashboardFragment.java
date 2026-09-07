package com.wattwise.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.wattwise.android.R;
import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.repository.PriceRepository;
import com.wattwise.android.ui.adapter.PriceAdapter;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;
import com.wattwise.android.util.Prefs;
import com.wattwise.android.util.PriceUtils;

import android.content.Intent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Offline-first price dashboard.
 *
 * <ul>
 *   <li>SwipeRefreshLayout → API refresh; on failure the Room cache is rendered
 *       behind a "Datos sin conexión" banner with the last update time.</li>
 *   <li>HOY / MAÑANA tabs switch the slot list (grouped by Spanish calendar day).</li>
 *   <li>Header card: cheapest / costliest slot and the day mean of
 *       {@code totalEurPerKwh} (the model documented for charts).</li>
 *   <li>Anonymous users get an "Iniciar sesión" prompt instead of sync features.</li>
 * </ul>
 */
public class DashboardFragment extends Fragment {

    private PriceRepository priceRepository;
    private Prefs prefs;

    private final PriceAdapter adapter = new PriceAdapter();
    private LocalDate selectedDate;

    private RecyclerView priceList;
    private SwipeRefreshLayout swipeRefresh;
    private TabLayout dayTabs;
    private View offlineBanner;
    private TextView txtOffline;
    private View loginPrompt;
    private TextView txtDayTitle;
    private TextView txtCheapest;
    private TextView txtCostliest;
    private TextView txtMean;
    private TextView txtNoPrices;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        priceRepository = new PriceRepository(
                WattWiseDatabase.get(requireContext()), new Prefs(requireContext()));
        prefs = new Prefs(requireContext());

        priceList = view.findViewById(R.id.priceList);
        priceList.setLayoutManager(new LinearLayoutManager(getContext()));
        priceList.setAdapter(adapter);

        txtNoPrices = view.findViewById(R.id.txtNoPrices);
        txtDayTitle = view.findViewById(R.id.txtDayTitle);
        txtCheapest = view.findViewById(R.id.txtCheapest);
        txtCostliest = view.findViewById(R.id.txtCostliest);
        txtMean = view.findViewById(R.id.txtMean);
        offlineBanner = view.findViewById(R.id.offlineBanner);
        txtOffline = view.findViewById(R.id.txtOffline);
        loginPrompt = view.findViewById(R.id.loginPromptCard);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.semaphore_green);
        swipeRefresh.setOnRefreshListener(() -> {
            swipeRefresh.setRefreshing(true);
            loadDay(selectedDate, true, () -> swipeRefresh.setRefreshing(false));
        });

        dayTabs = view.findViewById(R.id.dayTabs);
        dayTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull TabLayout.Tab tab) {
                LocalDate date = tab.getPosition() == 0
                        ? LocalDate.now(PriceUtils.SPAIN)
                        : LocalDate.now(PriceUtils.SPAIN).plusDays(1);
                loadDay(date, false, null);
            }

            @Override
            public void onTabUnselected(@NonNull TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(@NonNull TabLayout.Tab tab) {
            }
        });

        view.findViewById(R.id.btnGoLogin).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        updateLoginPrompt();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLoginPrompt();
        if (selectedDate == null) {
            selectedDate = LocalDate.now(PriceUtils.SPAIN);
        }
        // Render cached data instantly, then refresh in the background.
        loadDay(selectedDate, true, null);
    }

    private void updateLoginPrompt() {
        boolean loggedIn = new SessionManager(requireContext()).hasSession();
        if (loginPrompt != null) {
            loginPrompt.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        }
    }

    private void loadDay(LocalDate date, boolean forceRefresh, Runnable onDone) {
        selectedDate = date;
        txtDayTitle.setText(LocalDate.now(PriceUtils.SPAIN).equals(date)
                ? getString(R.string.tab_today)
                : getString(R.string.tab_tomorrow));

        if (forceRefresh) {
            refresh(date, onDone);
        } else {
            LocalDate finalDate = date;
            AppExecutors.io(() -> {
                List<PriceDto> cache = priceRepository.cachedForDate(finalDate);
                AppExecutors.main(() -> {
                    render(cache, cache.isEmpty() ? false : prefs.priceLastUpdatedMs() > 0, false);
                    if (getView() != null) {
                        // Soft refresh so the cache is replaced once the API answers.
                        refresh(finalDate, null);
                    }
                });
            });
        }
    }

    private void refresh(LocalDate date, Runnable onDone) {
        priceRepository.fetchDay(date, new Callback<PriceRepository.DayResult>() {
            @Override
            public void onSuccess(PriceRepository.DayResult result) {
                if (onDone != null) {
                    onDone.run();
                }
                offlineBanner.setVisibility(result.offline ? View.VISIBLE : View.GONE);
                if (result.offline) {
                    long last = result.lastUpdatedMs;
                    String time = last > 0
                            ? PriceUtils.formatLastUpdated(
                                    java.time.Instant.ofEpochMilli(last)
                                            .atZone(PriceUtils.UTC).toLocalDateTime())
                            : "–";
                    txtOffline.setText(getString(R.string.offline_banner) + " · "
                            + getString(R.string.offline_last_update, time));
                }
                render(result.slots, result.offline, true);
            }

            @Override
            public void onError(boolean authError, String message) {
                if (onDone != null) {
                    onDone.run();
                }
                if (authError) {
                    Toast.makeText(getContext(), R.string.session_expired, Toast.LENGTH_SHORT).show();
                    if (getActivity() instanceof MainActivity) {
                        MainActivity.clearSessionAndGoToLogin((MainActivity) getActivity());
                    }
                } else {
                    Toast.makeText(getContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void render(List<PriceDto> slots, boolean offline, boolean hasAttemptedRefresh) {
        boolean empty = slots == null || slots.isEmpty();
        priceList.setVisibility(empty ? View.GONE : View.VISIBLE);
        txtNoPrices.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.setSlots(slots);

        if (empty) {
            txtCheapest.setText("--");
            txtCostliest.setText("--");
            txtMean.setText("--");
            return;
        }

        fillSummary(slots);
    }

    private void fillSummary(List<PriceDto> slots) {
        PriceDto cheapest = null;
        PriceDto costliest = null;
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (PriceDto s : slots) {
            if (s.getTotalEurPerKwh() == null) {
                continue;
            }
            sum = sum.add(s.getTotalEurPerKwh());
            n++;
            if (cheapest == null || s.getTotalEurPerKwh().compareTo(cheapest.getTotalEurPerKwh()) < 0) {
                cheapest = s;
            }
            if (costliest == null || s.getTotalEurPerKwh().compareTo(costliest.getTotalEurPerKwh()) > 0) {
                costliest = s;
            }
        }
        if (n == 0) {
            txtCheapest.setText("--");
            txtCostliest.setText("--");
            txtMean.setText("--");
            return;
        }

        txtCheapest.setText(PriceUtils.slotStartLabel(cheapest.getTimestamp()) + " · "
                + PriceUtils.formatPrice(cheapest.getTotalEurPerKwh()));
        txtCostliest.setText(PriceUtils.slotStartLabel(costliest.getTimestamp()) + " · "
                + PriceUtils.formatPrice(costliest.getTotalEurPerKwh()));
        txtMean.setText(PriceUtils.formatPrice(
                sum.divide(BigDecimal.valueOf(n), 4, java.math.RoundingMode.HALF_UP)));
    }
}