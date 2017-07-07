package com.vaibhav.test;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Created by Vaibhav Barad on 5/7/17.
 */

/**
 * BroadcastReceiver to react to System boot and then to start a Alarm manager to periodically
 * start the service to keep it alive.*/

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final int PERIOD = 300000;  // 5 minutes

    @Override
    public void onReceive(Context context, Intent intent) {

        /*Create a pending intent of OnAlarmReceiver to start the service and pass this to the
        AlarmManager to be called after the stated period thereby keeping the service alive*/
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, OnAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0,
                i, 0);

        mgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60000,
                PERIOD,
                pi);
        context.startService(new Intent(context, LocationWriteService.class));
    }
}
