package com.wattwise.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.wattwise.android.R;
import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.remote.dto.AuthResponse;
import com.wattwise.android.data.remote.dto.LoginRequest;
import com.wattwise.android.data.remote.dto.RegisterRequest;
import com.wattwise.android.fcm.FcmTokenRegistrar;
import com.wattwise.android.sync.SyncWorker;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla de inicio de sesión / registro. Las credenciales se validan en el cliente,
 * se envían al backend y, en caso de éxito, el JWT + la identidad del usuario se
 * persisten con EncryptedSharedPreferences antes de navegar a la pantalla principal.
 */
public class LoginActivity extends AppCompatActivity {

    private boolean registerMode;

    private MaterialButtonToggleGroup modeToggle;
    private View inputEmail;
    private MaterialButton btnAuth;
    private View progress;
    private View txtError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (new SessionManager(this).hasSession()) {
            goToMain();
            return;
        }

        modeToggle = findViewById(R.id.authModeToggle);
        inputEmail = findViewById(R.id.inputEmail);
        btnAuth = findViewById(R.id.btnAuth);
        progress = findViewById(R.id.authProgress);
        txtError = findViewById(R.id.txtAuthError);

        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            registerMode = checkedId == R.id.modeRegister;
            inputEmail.setVisibility(registerMode ? View.VISIBLE : View.GONE);
            btnAuth.setText(registerMode
                    ? R.string.action_register
                    : R.string.action_login);
        });
        modeToggle.check(R.id.modeLogin);

        btnAuth.setOnClickListener(v -> onAuthClick());
    }

    private void onAuthClick() {
        String identifier = textOf(R.id.editUsername);
        String password = textOf(R.id.editPassword);

        execute(R.id.editUsername);
        execute(R.id.editPassword);
        if (registerMode) {
            String email = textOf(R.id.editEmail);
            execute(R.id.editEmail);
            if (TextUtils.isEmpty(identifier)) {
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError("E-mail no válido");
                return;
            }
            if (password.length() < 8) {
                showError(getString(R.string.error_min_password));
                return;
            }
            doRegister(identifier, email, password);
        } else {
            if (TextUtils.isEmpty(identifier) || TextUtils.isEmpty(password)) {
                return;
            }
            doLogin(identifier, password);
        }
    }

    private void doLogin(String identifier, String password) {
        setBusy(true);
        ApiClient.api().login(new LoginRequest(identifier, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call,
                                           @NonNull Response<AuthResponse> response) {
                        setBusy(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getUser() != null) {
                            onAuthenticated(response.body());
                        } else if (response.code() == 401 || response.code() == 403) {
                            showError(getString(R.string.error_credentials));
                        } else {
                            showError(getString(R.string.error_generic));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        setBusy(false);
                        showError(getString(R.string.error_network));
                    }
                });
    }

    private void doRegister(String username, String email, String password) {
        setBusy(true);
        ApiClient.api().register(new RegisterRequest(username, email, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call,
                                           @NonNull Response<AuthResponse> response) {
                        setBusy(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getUser() != null) {
                            onAuthenticated(response.body());
                        } else {
                            showError(com.wattwise.android.data.remote.ApiErrorParser.message(response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        setBusy(false);
                        showError(getString(R.string.error_network));
                    }
                });
    }

    private void onAuthenticated(AuthResponse response) {
        SessionManager session = new SessionManager(this);
        session.saveSession(
                response.getToken(),
                response.getUser().getId(),
                response.getUser().getUsername(),
                response.getUser().getEmail());

        // Dispara un sync para que recomendaciones/aparatos estén listos cuando se abra Main.
        SyncWorker.enqueue(this);
        // Registra el token FCM del dispositivo (si se recibió sin sesión, queda pendiente).
        FcmTokenRegistrar.registerPending(this);
        goToMain();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private String textOf(int id) {
        android.widget.EditText field = findViewById(id);
        return field == null ? "" : field.getText().toString().trim();
    }

    private void execute(int id) {
        android.widget.EditText field = findViewById(id);
        field.setError(null);
    }

    private void showError(String message) {
        txtError.setVisibility(View.VISIBLE);
        ((android.widget.TextView) txtError).setText(message == null ? getString(R.string.error_generic) : message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean busy) {
        btnAuth.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
    }
}