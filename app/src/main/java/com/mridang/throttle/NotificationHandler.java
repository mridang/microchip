package com.mridang.throttle;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Handler class that runs every second to update the notification with the processor usage.
 */
public class NotificationHandler extends Handler {

    /**
     * The object for holding the memory usage information and other stats
     */
    private final ActivityManager.MemoryInfo memInformation = new ActivityManager.MemoryInfo();
    /**
     * The instance of the context of the parent service
     */
    private final Context ctxContext;
    /**
     * The instance of the manager of the activity services
     */
    private final ActivityManager mgrActivity;
    /**
     * The instance of the manager of the notification services
     */
    private final NotificationManager mgrNotifications;
    /**
     * The instance of the notification builder to rebuild the notification
     */
    private final NotificationCompat.Builder notBuilder;
    /**
     * The processor statistics file from which the figures should be read repeatedly
     */
    private RandomAccessFile rafProcessor;
    /**
     * The value of the total processor utilization since the last update
     */
    private Long lngPreviousTotal = 0L;
    /**
     * The value of the idle processor utilization since the last update
     */
    private Long lngPreviousIdle = 0L;
    /**
     * The percentage of the processor utilization since the last update
     */
    private Double dblPercent = 0D;

    /**
     * Simple constructor to initialize the initial value of the previous
     */
    public NotificationHandler(Context ctxContext, NotificationCompat.Builder notBuilder) {

        try {
            this.mgrActivity = (ActivityManager) ctxContext.getSystemService(Context.ACTIVITY_SERVICE);
            this.mgrNotifications = (NotificationManager) ctxContext.getSystemService(Context.NOTIFICATION_SERVICE);
            this.ctxContext = ctxContext;
            this.notBuilder = notBuilder;
            rafProcessor = new RandomAccessFile("/proc/stat", "r");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to open the /proc/stat file");
        }
    }

    /**
     * Handler method that updates the notification icon with the current processor usage. It does
     * this by reading the /proc/stat file and specifically the of the first CPU row as are only
     * concerned with the cumulative processor utilization.
     */
    @Override
    public void handleMessage(Message msgMessage) {

        sendEmptyMessageDelayed(1, 2000L);

        try {

            rafProcessor.seek(0);
            String strLine = rafProcessor.readLine();
            Log.v("HardwareService", strLine);
            String[] lstColumns = strLine.split(" ");

            long lngCurrentIdle = Long.parseLong(lstColumns[5]);
            long lngCurrentTotal = 0L;

            lstColumns[0] = lstColumns[1] = "0";

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

            Log.d("HardwareService", "Current processor usage is " + dblPercent.toString());
            notBuilder.setContentTitle(ctxContext.getResources().getStringArray(R.array.usage)[intLevel]);
            notBuilder.setContentInfo(getUsagePercentage() + "%");
            notBuilder.setContentText(ctxContext.getString(R.string.memory, lngFree, lngTotal));
            mgrNotifications.notify(HardwareService.ID, notBuilder.build());
        } catch (Exception e) {
            Log.e("HardwareService", "Error creating notification for usage " + dblPercent, e);
        }
    }

    /**
     * Closes the processor statistics file from which the figures are be read repeatedly
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
     * Returns the current processor utilization from the processor statistics file
     *
     * @return The double value representing the percentage of processor utilization
     */
    public Integer getUsagePercentage() {
        return dblPercent.intValue();
    }
}
