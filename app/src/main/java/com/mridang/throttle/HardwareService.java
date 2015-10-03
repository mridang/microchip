package com.mridang.throttle;

import android.app.ActivityManager;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 Main service class that monitors the processor usage and updates the notification every second
 */
public class HardwareService extends Service {

	/**
	 Custom binder class used for allowing the preference activity to bind to this service so that it
	 may be configured on the fly
	 */
	public class LocalBinder extends Binder {

		public HardwareService getServerInstance() {
			return HardwareService.this;
		}
	}

	/**
	 The instance of the binder class used by the activity
	 */
	private final IBinder mBinder = new LocalBinder();
	/**
	 The constant defining the identifier of the notification that is to be shown
	 */
	private static final int ID = 9001;
	/**
	 The identifier if the component that open from the settings activity
	 */
	private static final String CMP = "com.android.settings.Settings$DevelopmentSettingsActivity";

	/**
	 The handler class that runs every second to update the notification with the processor usage.
	 */
	private static class NotificationHandler extends Handler {

		/**
		 The processor statistics file from which the figures should be read repeatedly
		 */
		private RandomAccessFile rafProcessor;
		/**
		 The value of the total processor utilization since the last update
		 */
		private Long lngPreviousTotal = 0L;
		/**
		 The value of the idle processor utilization since the last update
		 */
		private Long lngPreviousIdle = 0L;
		/**
		 The percentage of the processor utilization since the last update
		 */
		private Double dblPercent = 0D;
		/**
		 The object for holding the memory usage information and other stats
		 */
		private final ActivityManager.MemoryInfo memInformation = new ActivityManager.MemoryInfo();
		/**
		 The instance of the context of the parent service
		 */
		private final Context ctxContext;

		/**
		 Simple constructor to initialize the initial value of the previous
		 */
		public NotificationHandler(Context ctxContext) {

			try {
				this.ctxContext = ctxContext;
				rafProcessor = new RandomAccessFile("/proc/stat", "r");
			} catch (FileNotFoundException e) {
				throw new RuntimeException("Unable to open the /proc/stat file");
			}
		}

		/**
		 Handler method that updates the notification icon with the current processor usage. It does
		 this by reading the /proc/stat file and specifically the of the first CPU row as are only
		 concerned with the cumulative processor utilization.
		 */
		@Override
		public void handleMessage(Message msgMessage) {

			HardwareService.hndNotifier.sendEmptyMessageDelayed(1, 2000L);

			try {

				rafProcessor.seek(0);
				String[] lstColumns = rafProcessor.readLine().split(" ");

				long lngCurrentIdle = Long.parseLong(lstColumns[5]);
				long lngCurrentTotal = 0L;

				lstColumns[0] = "0";
				lstColumns[1] = "0";

				for (String strStatistic : lstColumns) {
					lngCurrentTotal = lngCurrentTotal + Integer.parseInt(strStatistic);
				}
				long lngDifferenceIdle = lngCurrentIdle - lngPreviousIdle;
				long lngDifferenceTotal = lngCurrentTotal - lngPreviousTotal;

				lngPreviousIdle = lngCurrentIdle;
				lngPreviousTotal = lngCurrentTotal;

				long lngUsageDelta = lngDifferenceTotal - lngDifferenceIdle;
				dblPercent = 100.0 * (lngUsageDelta / (lngDifferenceTotal + 0.01));
				notBuilder.setSmallIcon(R.drawable.i0 + (int) (dblPercent / 10));

				mgrActivity.getMemoryInfo(memInformation);
				long lngFree = memInformation.availMem / 1048576L;
				long lngTotal = memInformation.totalMem / 1048576L;
				int intLevel = (int) ((100.0 * (lngFree / (lngTotal + 0.01))) / 25);

				HardwareService.notBuilder.setContentTitle(ctxContext.getResources().getStringArray(R.array.usage)[intLevel]);
				HardwareService.notBuilder.setContentInfo(hndNotifier.getUsagePercentage() + "%");
				HardwareService.notBuilder.setContentText(ctxContext.getString(R.string.memory, lngFree, lngTotal));
				HardwareService.mgrNotifications.notify(ID, HardwareService.notBuilder.build());
				Log.v("HardwareService", "Current processor usage is " + dblPercent.toString());
			} catch (Exception e) {
				Log.e("HardwareService", "Error creating notification for usage " + dblPercent, e);
			}
		}

		/**
		 Closes the processor statistics file from which the figures are be read repeatedly
		 */
		public void closeFile() {

			if (rafProcessor != null) {
				try {
					rafProcessor.close();
				} catch (IOException e) {
					Log.w("HardwareService", "Unable to successfully close the file");
				}
			}
		}

		/**
		 Returns the current processor utilization from the processor statistics file

		 @return The double value representing the percentage of processor utilization
		 */
		public Integer getUsagePercentage() {
			return dblPercent.intValue();
		}
	}

	/**
	 The instance of the handler that updates the notification
	 */
	private static NotificationHandler hndNotifier;
	/**
	 The instance of the manager of the notification services
	 */
	private static NotificationManager mgrNotifications;
	/**
	 The instance of the manager of the activity services
	 */
	private static ActivityManager mgrActivity;
	/**
	 The instance of the notification builder to rebuild the notification
	 */
	private static NotificationCompat.Builder notBuilder;
	/**
	 The instance of the broadcast receiver to handle intents
	 */
	private BroadcastReceiver recScreen;

	/**
	 Initializes the service by getting instances of service managers and mainly setting up the
	 receiver to receive all the necessary intents that this service is supposed to handle.
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
		mgrActivity = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		hndNotifier = new NotificationHandler(getApplicationContext());

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
			public void onReceive(Context ctcContext, Intent ittIntent) {

				if (ittIntent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_OFF)) {

					Log.d("HardwareService", "Screen off; hiding the notification");
					hndNotifier.removeMessages(1);
					mgrNotifications.cancel(ID);
				} else {

					if (settings.getBoolean("enabled", true)) {
						Log.d("HardwareService", "Screen on; showing the notification");
						showNotification();
					}
				}
			}
		};

		IntentFilter ittScreen = new IntentFilter();
		ittScreen.addAction(Intent.ACTION_SCREEN_ON);
		ittScreen.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(recScreen, ittScreen);
	}

	/**
	 Called when the service is being stopped. It doesn't do much except clear the message queue of
	 the handler, hides the notification and unregisters the receivers.
	 */
	@Override
	public void onDestroy() {

		Log.d("HardwareService", "Stopping the hardware service");
		unregisterReceiver(recScreen);
		hndNotifier.removeMessages(1);
		hndNotifier.removeMessages(2);
		mgrNotifications.cancel(ID);
		hndNotifier.closeFile();
	}

	/**
	 Helper method that shows the notification by sending the handler a message and building the
	 notification. This is invoked when the preference is toggled.
	 */
	public void showNotification() {

		Log.d("HardwareService", "Showing the notification");
		mgrNotifications.notify(ID, notBuilder.build());
		hndNotifier.removeMessages(1);
		hndNotifier.sendEmptyMessage(1);
	}

	/**
	 Helper method that hides the notification by clearing the handler messages and cancelling the
	 notification. This is invoked when the preference is toggled.
	 */
	public void hideNotification() {

		Log.d("HardwareService", "Hiding the notification");
		mgrNotifications.cancel(ID);
		hndNotifier.removeMessages(1);
	}

	/**
	 Helper method that toggles the visibility of the notification on the locksreen depending on the
	 value of the preference in the activity

	 @param visibility A boolean value indicating whether the notification should be visible on the
	 lockscreen
	 */
	public void visibilityPublic(Boolean visibility) {
		if (visibility) {
			notBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		} else {
			notBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
		}
	}

	/**
	 Helper method that sets the background color of the notification icon by parsing the RGB value
	 into an int.

	 @param color The internal int representation of the RGB color to set as the background colour
	 */
	public void setColor(Integer color) {
		notBuilder.setColor(color);
	}

	/**
	 Binder method to allow the settings activity to bind to the service so the notification can be
	 configured and updated while the activity is being toggles.

	 @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent intReason) {
		return mBinder;
	}
}
