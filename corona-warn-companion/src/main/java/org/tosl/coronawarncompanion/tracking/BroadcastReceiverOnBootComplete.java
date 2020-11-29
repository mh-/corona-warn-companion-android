package org.tosl.coronawarncompanion.tracking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BroadcastReceiverOnBootComplete extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences sharedPref = context.getSharedPreferences(
                    "corona_warn_app_tracking", Context.MODE_PRIVATE);
            boolean isTrackingEnabled =  sharedPref.getBoolean("TrackingEnabled", false);
            if (isTrackingEnabled) {
                Log.d("Tracking enabled","true");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(new Intent(context, LocationService.class));
                } else {
                    context.startService(new Intent(context, LocationService.class));
                }
            }
            else {
                Log.d("Tracking enabled","false");

            }
        }
    }
}