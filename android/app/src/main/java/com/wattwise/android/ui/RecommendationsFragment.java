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

import com.wattwise.android.R;
import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.entity.RecommendationEntity;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.repository.RecommendationRepository;
import com.wattwise.android.ui.adapter.RecommendationAdapter;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;
import com.wattwise.android.util.Prefs;
import com.wattwise.android.util.PriceUtils;

import android.content.Intent;
import java.util.List;

/**
 * Personalized recommendations per appliance. Server data is cached in Room and
 * the cached copy is served instantly; a refresh replaces it when a session
 * exists. Offline shows the stale list; anonymous users see the login prompt.
 */
public class RecommendationsFragment extends Fragment {

    private RecommendationRepository repository;
    private final RecommendationAdapter adapter = new RecommendationAdapter();

    private View offlineBanner;
    private TextView txtOffline;
    private View empty;
    private View loginPrompt;
    private boolean authRedirectPending;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recommendations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new RecommendationRepository(
                WattWiseDatabase.get(requireContext()), new Prefs(requireContext()));

        RecyclerView list = view.findViewById(R.id.recommendationList);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        offlineBanner = view.findViewById(R.id.offlineBanner);
        txtOffline = view.findViewById(R.id.txtOffline);
        empty = view.findViewById(R.id.txtEmpty);
        loginPrompt = view.findViewById(R.id.loginPromptCard);

        view.findViewById(R.id.btnGoLogin).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean loggedIn = new SessionManager(requireContext()).hasSession();
        loginPrompt.setVisibility(loggedIn ? View.GONE : View.VISIBLE);

        // 1) serve cache instantly
        AppExecutors.io(() -> {
            List<RecommendationEntity> cached = repository.getAllCachedSync();
            AppExecutors.main(() -> render(cached, false));
        });

        // 2) refresh from server when a session exists
        if (loggedIn && !authRedirectPending) {
            authRedirectPending = true;
            repository.refresh(new Callback<List<RecommendationEntity>>() {
                @Override
                public void onSuccess(List<RecommendationEntity> data) {
                    authRedirectPending = false;
                    offlineBanner.setVisibility(View.GONE);
                    render(data, false);
                }

                @Override
                public void onError(boolean authError, String message) {
                    authRedirectPending = false;
                    if (authError) {
                        Toast.makeText(getContext(), R.string.session_expired, Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof MainActivity) {
                            MainActivity.clearSessionAndGoToLogin((MainActivity) getActivity());
                        }
                    } else if (adapterCachedEmpty()) {
                        // Nothing cached to fall back on — the empty state stays visible.
                    } else {
                        // Offline: the stale cache already rendered; label it.
                        offlineBanner.setVisibility(View.VISIBLE);
                        long last = new Prefs(requireContext()).recommendationLastUpdatedMs();
                        String time = last > 0
                                ? PriceUtils.formatLastUpdated(
                                        java.time.Instant.ofEpochMilli(last)
                                                .atZone(PriceUtils.UTC).toLocalDateTime())
                                : "–";
                        txtOffline.setText(getString(R.string.offline_banner) + " · "
                                + getString(R.string.offline_last_update, time));
                    }
                }
            });
        }
    }

    private boolean adapterCachedEmpty() {
        try {
            return repository.getAllCachedSync().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private void render(List<RecommendationEntity> data, boolean offline) {
        boolean emptyList = data == null || data.isEmpty();
        empty.setVisibility(emptyList ? View.VISIBLE : View.GONE);
        if (!offline && !emptyList) {
            offlineBanner.setVisibility(View.GONE);
        }
        adapter.setItems(data);
    }
}