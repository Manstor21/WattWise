package com.wattwise.android.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.wattwise.android.R;
import com.wattwise.android.ui.MainActivity;

/**
 * Construye y publica notificaciones locales. FCM está deliberadamente NO
 * implementado; todo lo que se entrega aquí se produce en el dispositivo
 * (WorkManager + Room) y pasa por un único canal de la app, que la API 26+
 * exige antes de poder mostrar cualquier notificación.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "price_alerts";
    private static final int NOTIF_OPTIMAL_WINDOW = 1001;
    private static final int NOTIF_LOW_PRICE = 1002;

    private NotificationHelper() {
    }

    /** Se llama desde WattWiseApp.onCreate; seguro en todos los niveles de API que soportamos. */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_desc));
        nm.createNotificationChannel(channel);
    }

    /** Apunta la notificación a la pantalla principal. */
    private static PendingIntent contentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Notificación de «ventana óptima» — programa tu electrodoméstico de
     * {@code start} a {@code end} a {@code priceEur}.
     */
    public static void notifyOptimalWindow(Context context, String startHm, String endHm, String priceEur) {
        String title = context.getString(R.string.notification_optimal_window);
        String body = context.getString(R.string.notification_optimal_body, startHm, endHm, priceEur);
        NotificationCompat.Builder builder = base(context)
                .setSmallIcon(R.drawable.ic_stat_bolt)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_OPTIMAL_WINDOW, builder.build());
        }
    }

    /** Notificación de «precio bajo detectado» para una sola franja CHEAP. */
    public static void notifyLowPrice(Context context, String priceEur, String atHm) {
        String body = context.getString(R.string.notification_low_price_body, priceEur, atHm);
        NotificationCompat.Builder builder = base(context)
                .setSmallIcon(R.drawable.ic_stat_bolt)
                .setContentTitle(context.getString(R.string.notification_low_price))
                .setContentText(body)
                .setAutoCancel(true);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_LOW_PRICE, builder.build());
        }
    }

    private static NotificationCompat.Builder base(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent(context))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION);
    }
}