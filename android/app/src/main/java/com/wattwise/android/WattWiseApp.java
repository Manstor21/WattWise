package com.wattwise.android;

import android.app.Application;

import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.notification.NotificationHelper;
import com.wattwise.android.notification.PriceCheckWorker;

/**
 * Application entry point: initialises the Retrofit client with the current
 * session (token available to the auth interceptor before any request), creates
 * the notification channel and schedules the 6-hour price-check worker.
 */
public class WattWiseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ApiClient.init(new SessionManager(this));
        NotificationHelper.createChannel(this);
        PriceCheckWorker.schedule(this);
    }
}