package com.wattwise.android.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.wattwise.android.R;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.util.Prefs;

/**
 * Host for the four-tab navigation (Dashboard, Recommendations, Appliances,
 * Settings). Also owns the POST_NOTIFICATIONS runtime request (API 33+) and the
 * shared "go to login" exit path used after a 401.
 */
public class MainActivity extends AppCompatActivity {

    private Fragment dashboard;
    private Fragment recommendations;
    private Fragment appliances;
    private Fragment settings;

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            dashboard = new DashboardFragment();
            recommendations = new RecommendationsFragment();
            appliances = new AppliancesFragment();
            settings = new SettingsFragment();
        } else {
            dashboard = fm.findFragmentByTag("dashboard");
            recommendations = fm.findFragmentByTag("recommendations");
            appliances = fm.findFragmentByTag("appliances");
            settings = fm.findFragmentByTag("settings");
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                show(dashboard, "dashboard");
                return true;
            } else if (id == R.id.nav_recommendations) {
                show(recommendations, "recommendations");
                return true;
            } else if (id == R.id.nav_appliances) {
                show(appliances, "appliances");
                return true;
            } else if (id == R.id.nav_settings) {
                show(settings, "settings");
                return true;
            }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_dashboard);
    }

    private void show(Fragment fragment, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        for (Fragment f : fm.getFragments()) {
            if (f.isAdded()) {
                tx.hide(f);
            }
        }
        if (fragment == null) {
            return;
        }
        if (fragment.isAdded()) {
            tx.show(fragment);
        } else {
            tx.add(R.id.mainContainer, fragment, tag);
        }
        tx.commitNow();
    }

    /**
     * POST_NOTIFICATIONS is a runtime permission on API 33+. We ask once per
     * install; denial just means notifications stay silent.
     */
    private void requestNotificationPermissionIfNeeded() {
        Prefs prefs = new Prefs(this);
        if (Build.VERSION.SDK_INT < 33 || prefs.notificationPermissionRequested()) {
            return;
        }
        prefs.setNotificationPermissionRequested(true);

        notificationPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                        granted -> { /* result is best-effort; nothing to react to */ });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Shared exit: wipe the session and relaunch the login screen. */
    public static void clearSessionAndGoToLogin(AppCompatActivity activity) {
        new SessionManager(activity).clear();
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}