package com.wattwise.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.wattwise.android.R;
import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.entity.ApplianceEntity;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.repository.ApplianceRepository;
import com.wattwise.android.ui.adapter.ApplianceAdapter;
import com.wattwise.android.util.AppExecutors;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Lista de aparatos respaldada por Room. Las ediciones (offline) en espera muestran la
 * etiqueta "Pendiente de sincronizar"; el {@link com.wattwise.android.sync.SyncWorker}
 * las envía la próxima vez que haya red disponible. Los usuarios anónimos siguen viendo
 * el FAB para poder empezar en local, pero aparece un toast invitando a sincronizar.
 */
public class AppliancesFragment extends Fragment implements ApplianceAdapter.Listener {

    private static final String[] TYPE_KEYS = {
            "WASHING_MACHINE", "DISHWASHER", "EV_CHARGER", "DRYER", "POOL_PUMP", "AC", "OTHER"
    };
    private static final String[] TYPE_LABELS = {
            "Lavadora", "Lavavajillas", "Cargador VE", "Secadora",
            "Bomba de piscina", "Aire acondicionado", "Otro"
    };

    private ApplianceRepository repository;
    private final ApplianceAdapter adapter = new ApplianceAdapter(this);

    private View empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appliances, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new ApplianceRepository(WattWiseDatabase.get(requireContext()));

        RecyclerView list = view.findViewById(R.id.applianceList);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        empty = view.findViewById(R.id.txtEmpty);

        FloatingActionButton fab = view.findViewById(R.id.btnAddAppliance);
        fab.setOnClickListener(v -> showApplianceDialog(null));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAll();
    }

    private void loadAll() {
        boolean loggedIn = new SessionManager(requireContext()).hasSession();
        if (loggedIn) {
            repository.refresh(new com.wattwise.android.util.Callback<List<ApplianceEntity>>() {
                @Override
                public void onSuccess(List<ApplianceEntity> data) {
                    show(data);
                }

                @Override
                public void onError(boolean authError, String message) {
                    // respaldo offline
                    repository.getAllActive(data -> show(data));
                }
            });
        } else {
            repository.getAllActive(this::show);
        }
    }

    private void show(List<ApplianceEntity> list) {
        boolean emptyList = list == null || list.isEmpty();
        empty.setVisibility(emptyList ? View.VISIBLE : View.GONE);
        adapter.setItems(list);
    }

    @Override
    public void onEdit(ApplianceEntity appliance) {
        showApplianceDialog(appliance);
    }

    @Override
    public void onDelete(ApplianceEntity appliance) {
        new AlertDialog.Builder(requireContext(), R.style.Theme_WattWise)
                .setTitle(R.string.confirm_delete_appliance)
                .setPositiveButton(R.string.action_delete, (d, w) ->
                        repository.deleteLocal(appliance, v -> {
                            Toast.makeText(getContext(), R.string.action_delete, Toast.LENGTH_SHORT).show();
                            loadAll();
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showApplianceDialog(@Nullable ApplianceEntity existing) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_appliance, null);
        TextInputEditText editName = view.findViewById(R.id.editName);
        AutoCompleteTextView editType = view.findViewById(R.id.editType);
        TextInputEditText editPower = view.findViewById(R.id.editPower);
        TextInputEditText editKwh = view.findViewById(R.id.editCycleKwh);
        TextInputEditText editMin = view.findViewById(R.id.editCycleMinutes);
        MaterialSwitch switchActive = view.findViewById(R.id.switchActive);

        editType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, TYPE_LABELS));
        editType.setText(null);

        // Rellena los valores por defecto del tipo seleccionado
        editType.setOnItemClickListener((parent, v, position, id) -> applyCatalogDefaults(position, editPower, editKwh, editMin));

        // Relleno previo al editar
        if (existing != null) {
            editName.setText(existing.name);
            int idx = Arrays.asList(TYPE_KEYS).indexOf(existing.type);
            if (idx >= 0) {
                editType.setText(TYPE_LABELS[idx], false);
            }
            if (existing.powerWatts != null) editPower.setText(String.valueOf(existing.powerWatts));
            if (existing.avgCycleKwh != null) editKwh.setText(existing.avgCycleKwh.toPlainString());
            if (existing.estimatedCycleMinutes != null) editMin.setText(String.valueOf(existing.estimatedCycleMinutes));
            switchActive.setChecked(existing.isActive);
        } else {
            switchActive.setChecked(true);
            // Por defecto, lavadora
            editType.setText(TYPE_LABELS[0], false);
            applyCatalogDefaults(0, editPower, editKwh, editMin);
        }

        new AlertDialog.Builder(requireContext(), R.style.Theme_WattWise)
                .setTitle(existing == null ? R.string.title_add_appliance : R.string.title_edit_appliance)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = editName.getText() != null ? editName.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), R.string.error_appliance_fields, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int typeIdx = Arrays.asList(TYPE_LABELS).indexOf(editType.getText().toString());
                    if (typeIdx < 0) {
                        typeIdx = 0;
                    }
                    String type = TYPE_KEYS[typeIdx];
                    int power = safeInt(editPower, 1500);
                    BigDecimal kwh = safeDecimal(editKwh, BigDecimal.ONE);
                    int min = safeInt(editMin, 90);
                    boolean active = switchActive.isChecked();

                    if (existing == null) {
                        repository.createLocal(name, type, power, kwh, min, active, e -> loadAll());
                    } else {
                        existing.name = name;
                        existing.type = type;
                        existing.powerWatts = power;
                        existing.avgCycleKwh = kwh;
                        existing.estimatedCycleMinutes = min;
                        existing.isActive = active;
                        repository.updateLocal(existing, e -> loadAll());
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void applyCatalogDefaults(int typeIndex,
                                      TextInputEditText power,
                                      TextInputEditText kwh,
                                      TextInputEditText min) {
        int pw;
        String k;
        int mi;
        switch (typeIndex) {
            case 0:  pw = 2000; k = "1.000"; mi = 120; break;
            case 1:  pw = 1800; k = "0.900"; mi = 90;  break;
            case 2:  pw = 7400; k = "7.000"; mi = 240; break;
            case 3:  pw = 2500; k = "2.000"; mi = 90;  break;
            case 4:  pw = 1100; k = "5.000"; mi = 300; break;
            case 5:  pw = 2200; k = "1.500"; mi = 120; break;
            default: pw = 1500; k = "1.200"; mi = 90;  break;
        }
        power.setText(String.valueOf(pw));
        kwh.setText(k);
        min.setText(String.valueOf(mi));
    }

    private static int safeInt(TextInputEditText field, int fallback) {
        if (field == null || field.getText() == null) return fallback;
        String v = field.getText().toString().trim();
        if (v.isEmpty()) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BigDecimal safeDecimal(TextInputEditText field, BigDecimal fallback) {
        if (field == null || field.getText() == null) return fallback;
        String v = field.getText().toString().trim();
        if (v.isEmpty()) return fallback;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}