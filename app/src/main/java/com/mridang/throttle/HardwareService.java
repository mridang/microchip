package com.mridang.throttle;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * Main service class that monitors the processor usage and updates the notification every second
 */
public class HardwareService extends Service {

    /**
     * The constant defining the identifier of the notification that is to be shown
     */
    public static final int ID = 9001;
    /**
     * The identifier if the component that open from the settings activity
     */
    private static final String CMP = "com.android.settings.Settings$DevelopmentSettingsActivity";
    /**
     * The instance of the handler that updates the notification
     */
    private static NotificationHandler hndNotifier;
    /**
     * The instance of the manager of the notification services
     */
    private static NotificationManager mgrNotifications;
    /**
     * The instance of the notification builder to rebuild the notification
     */
    private static NotificationCompat.Builder notBuilder;
    /**
     * The instance of the binder class used by the activity
     */
    private final IBinder mBinder = new LocalBinder();
    /**
     * The instance of the broadcast receiver to handle power saver mode intents
     */
    private final BroadcastReceiver recSaver = new PowerReceiver();
    /**
     * The instance of the broadcast receiver to handle screen on/off intents
     */
    private BroadcastReceiver recScreen;

    /**
     * Initializes the service by getting instances of service managers and mainly setting up the
     * receiver to receive all the necessary intents that this service is supposed to handle.
     */
    @Override
    public void onCreate() {

        Log.i("HardwareService", "Creating the hardware service");
        super.onCreate();
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Intent ittSettings = new Intent();
        ittSettings.setComponent(new ComponentName("com.android.settings", CMP));
        PendingIntent pitSettings = PendingIntent.getActivity(this, 0, ittSettings, 0);
        notBuilder = new NotificationCompat.Builder(this);
        notBuilder.setSmallIcon(R.drawable.i0);
        notBuilder.setContentIntent(pitSettings);
        notBuilder.setOngoing(true);
        notBuilder.setWhen(0);
        notBuilder.setOnlyAlertOnce(true);
        notBuilder.setPriority(Integer.MAX_VALUE);
        notBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
        setColor(settings.getInt("color", Color.TRANSPARENT));
        visibilityPublic(settings.getBoolean("lockscreen", true));

        Log.d("HardwareService", "Setting up the service manager and the broadcast receiver");
        mgrNotifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        hndNotifier = new NotificationHandler(getApplicationContext(), notBuilder);

        if (settings.getBoolean("enabled", true)) {
            Log.d("HardwareService", "Screen on; showing the notification");
            hndNotifier.sendEmptyMessage(1);
            hndNotifier.sendEmptyMessage(2);
        }
        recScreen = new BroadcastReceiver() {

            /**
             * Handles the screen-on and the screen off intents to enable or disable the notification.
             * We don't want to show the notification if the screen is off.
             */
            @Override
            public void onReceive(Context ctxContext, Intent ittIntent) {

                if (ittIntent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_OFF)) {

                    Log.d("HardwareService", "Screen off; hiding the notification");
                    hndNotifier.removeMessages(1);
                    mgrNotifications.cancel(ID);
                } else {

                    if (settings.getBoolean("enabled", true)) {
                        Log.d("HardwareService", "Screen on; showing the notification");
                        hndNotifier = new NotificationHandler(ctxContext, notBuilder);
                        showNotification();
                    }
                }
            }
        };

        IntentFilter ittScreen = new IntentFilter();
        ittScreen.addAction(Intent.ACTION_SCREEN_ON);
        ittScreen.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(recScreen, ittScreen);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            IntentFilter ittSaver = new IntentFilter();
            ittScreen.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            registerReceiver(recSaver, ittSaver);
        }
    }

    /**
     * Called when the service is being stopped. It doesn't do much except clear the message queue of
     * the handler, hides the notification and unregisters the receivers.
     */
    @Override
    public void onDestroy() {

        Log.d("HardwareService", "Stopping the hardware service");
        unregisterReceiver(recScreen);
        unregisterReceiver(recSaver);
        hndNotifier.removeMessages(1);
        hndNotifier.removeMessages(2);
        mgrNotifications.cancel(ID);
        hndNotifier.destroy();
    }

    /**
     * Helper method that shows the notification by sending the handler a message and building the
     * notification. This is invoked when the preference is toggled.
     */
    public void showNotification() {

        Log.d("HardwareService", "Showing the notification");
        mgrNotifications.notify(ID, notBuilder.build());
        hndNotifier.removeMessages(1);
        hndNotifier.sendEmptyMessage(1);
    }

    /**
     * Helper method that hides the notification by clearing the handler messages and cancelling the
     * notification. This is invoked when the preference is toggled.
     */
    public void hideNotification() {

        Log.d("HardwareService", "Hiding the notification");
        mgrNotifications.cancel(ID);
        hndNotifier.removeMessages(1);
    }

    /**
     * Helper method that toggles the visibility of the notification on the locksreen depending on the
     * value of the preference in the activity
     *
     * @param visibility A boolean value indicating whether the notification should be visible on the
     *                   lockscreen
     */
    public void visibilityPublic(Boolean visibility) {
        if (visibility) {
            notBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        } else {
            notBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
        }
    }

    /**
     * Helper method that sets the background color of the notification icon by parsing the RGB value
     * into an int.
     *
     * @param color The internal int representation of the RGB color to set as the background colour
     */
    public void setColor(Integer color) {
        notBuilder.setColor(color);
    }

    /**
     * Binder method to allow the settings activity to bind to the service so the notification can be
     * configured and updated while the activity is being toggles.
     *
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intReason) {
        return mBinder;
    }

    /**
     * Custom binder class used for allowing the preference activity to bind to this service so that it
     * may be configured on the fly
     */
    public class LocalBinder extends Binder {

        public HardwareService getServerInstance() {
            return HardwareService.this;
        }
    }
}
