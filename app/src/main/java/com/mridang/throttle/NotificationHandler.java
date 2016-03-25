package com.mridang.throttle;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat.Builder;

/**
 * Handler class that runs every second to update the notification with the processor usage.
 */
public class NotificationHandler extends Handler {

    /**
     * The object for holding the memory usage information and other stats
     */
    private final MemoryInfo memInformation = new MemoryInfo();
    /**
     * The instance of the context of the parent service
     */
    private final Context ctxContext;
    /**
     * The instance of the manager of the activity services
     */
    private final ActivityManager mgrActivity;
    /**
     * The instance of the manager of the processor usage
     */
    private final ProcessorManager mgrProcessor;
    /**
     * The instance of the manager of the clock frequency
     */
    private final FrequencyManager mgrFrequency;
    /**
     * The instance of the manager of the notification services
     */
    private final NotificationManager mgrNotifications;
    /**
     * The instance of the notification builder to rebuild the notification
     */
    private final Builder notBuilder;

    /**
     * Simple constructor to initialize the initial value of the previous
     */
    public NotificationHandler(Context ctxContext, Builder notBuilder) {
        this.mgrActivity = (ActivityManager) ctxContext.getSystemService(Context.ACTIVITY_SERVICE);
        this.mgrNotifications = (NotificationManager) ctxContext.getSystemService(Context.NOTIFICATION_SERVICE);
        this.mgrProcessor = new ProcessorManager();
        this.mgrFrequency = new FrequencyManager();
        this.ctxContext = ctxContext;
        this.notBuilder = notBuilder;
    }

    /**
     * Handler method that updates the notification icon with the current processor usage. It does
     * this by reading the /proc/stat file and specifically the of the first CPU row as are only
     * concerned with the cumulative processor utilization.
     */
    @Override
    public void handleMessage(Message msgMessage) {
        sendEmptyMessageDelayed(1, 2000L);

        mgrActivity.getMemoryInfo(memInformation);
        long lngFree = memInformation.availMem / 1048576L;
        long lngTotal = memInformation.totalMem / 1048576L;
        int intLevel = (int) ((100.0 * (lngFree / (lngTotal + 0.01))) / 25);
        Double dblPercent = mgrProcessor.getUsage();
        String strClock = mgrFrequency.getFrequency();

        notBuilder.setSmallIcon(R.drawable.i0 + (int) (dblPercent / 10));
        notBuilder.setContentTitle(ctxContext.getResources().getStringArray(R.array.usage)[intLevel]);
        notBuilder.setContentInfo(dblPercent.intValue() + "% @ " + strClock);
        notBuilder.setContentText(ctxContext.getString(R.string.memory, lngFree, lngTotal));
        mgrNotifications.notify(HardwareService.ID, notBuilder.build());
    }

    public void destroy() {
        mgrProcessor.destroy();
        mgrFrequency.destroy();
    }
}
