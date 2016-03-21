package com.mridang.throttle;

import android.app.Application;

import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
        resNotifTickerText = R.string.crash_notif_ticker_text,
        resNotifTitle = R.string.crash_notif_title,
        resNotifText = R.string.crash_notif_text,
        mailTo = "mridang.agarwalla+throttle@gmail.com"
)
public class ThrottleApplication extends Application {
}
