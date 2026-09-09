package com.wattwise.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.wattwise.android.R;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.remote.dto.AlertPreferenceDto;
import com.wattwise.android.data.repository.AlertRepository;
import com.wattwise.android.sync.SyncWorker;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;
import com.wattwise.android.util.Prefs;

import java.math.BigDecimal;

/**
 * Configura los umbrales de alerta y dispara sync / comprobación de alertas a demanda.
 *
 * <ul>
 *   <li>El SeekBar de umbral (0–100, por defecto 20) define cuántos puntos porcentuales
 *       por debajo de la media diaria debe caer un precio para disparar una alerta.</li>
 *   <li>El switch activa/desactiva las alertas (consumido localmente por {@link
 *       com.wattwise.android.notification.PriceCheckWorker}).</li>
 *   <li>El campo applianceId es opcional — si se define, la preferencia del servidor
 *       se limita a ese aparato.</li>
 *   <li>Logout limpia la sesión y vuelve a la pantalla de inicio de sesión.</li>
 * </ul>
 */
public class SettingsFragment extends Fragment {

    private Prefs prefs;
    private SessionManager session;

    private TextView txtThreshold;
    private SeekBar seekbar;
    private MaterialSwitch switchAlerts;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = new Prefs(requireContext());
        session = new SessionManager(requireContext());

        txtThreshold = view.findViewById(R.id.txtThresholdValue);
        seekbar = view.findViewById(R.id.seekbarThreshold);
        switchAlerts = view.findViewById(R.id.switchAlerts);

        int pct = prefs.alertThresholdPct();
        seekbar.setProgress(pct);
        txtThreshold.setText(getString(R.string.alert_percent_below, pct));
        switchAlerts.setChecked(prefs.alertsEnabled());

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                txtThreshold.setText(getString(R.string.alert_percent_below, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        view.findViewById(R.id.btnSavePreferences).setOnClickListener(v -> save(view));
        view.findViewById(R.id.btnCheckAlerts).setOnClickListener(v -> checkAlerts());
        view.findViewById(R.id.btnSyncNow).setOnClickListener(v -> syncNow());
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        if (!session.hasSession()) {
            view.findViewById(R.id.btnSavePreferences).setEnabled(false);
            view.findViewById(R.id.btnCheckAlerts).setEnabled(false);
            ((TextView) view.findViewById(R.id.txtSyncStatus)).setText(R.string.action_login_prompt);
        }
    }

    private void save(View view) {
        int pct = seekbar.getProgress();
        prefs.setAlertThresholdPct(pct);
        prefs.setAlertsEnabled(switchAlerts.isChecked());

        String applianceIdText = "";
        android.widget.EditText applianceIdField = view.findViewById(R.id.editApplianceId);
        if (applianceIdField != null && applianceIdField.getText() != null) {
            applianceIdText = applianceIdField.getText().toString().trim();
        }
        long applianceId = applianceIdText.isEmpty() ? -1L : Long.parseLong(applianceIdText);
        prefs.setAlertApplianceId(applianceId);

        if (!session.hasSession()) {
            Toast.makeText(getContext(), R.string.prefs_saved, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertPreferenceDto dto = new AlertPreferenceDto();
        dto.setThresholdPctBelowMean(BigDecimal.valueOf(pct));
        dto.setIsActive(switchAlerts.isChecked());
        if (applianceId >= 0) {
            dto.setApplianceId(applianceId);
        }

        TextView syncStatus = view.findViewById(R.id.txtSyncStatus);
        new AlertRepository().savePreferences(dto, new Callback<AlertPreferenceDto>() {
            @Override
            public void onSuccess(AlertPreferenceDto data) {
                syncStatus.setText(R.string.prefs_saved);
            }

            @Override
            public void onError(boolean authError, String message) {
                if (authError) {
                    MainActivity.clearSessionAndGoToLogin((MainActivity) getActivity());
                } else {
                    Toast.makeText(getContext(), R.string.prefs_saved, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkAlerts() {
        TextView result = getView().findViewById(R.id.txtAlertsResult);
        if (!session.hasSession()) {
            result.setVisibility(View.VISIBLE);
            result.setText(R.string.action_login_prompt);
            return;
        }
        result.setVisibility(View.VISIBLE);
        result.setText(getString(R.string.alert_checked) + " …");

        new AlertRepository().checkAlerts(new Callback<java.util.List<String>>() {
            @Override
            public void onSuccess(java.util.List<String> data) {
                if (data == null || data.isEmpty()) {
                    result.setText(R.string.no_alerts);
                } else {
                    result.setText(getString(R.string.alert_fired, data.size())
                            + "\n" + String.join("\n", data));
                }
            }

            @Override
            public void onError(boolean authError, String message) {
                if (authError) {
                    MainActivity.clearSessionAndGoToLogin((MainActivity) getActivity());
                } else {
                    result.setText(message);
                }
            }
        });
    }

    private void syncNow() {
        TextView status = getView().findViewById(R.id.txtSyncStatus);
        status.setText(R.string.syncing);
        SyncWorker.enqueue(requireContext());
    }

    private void logout() {
        session.clear();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}