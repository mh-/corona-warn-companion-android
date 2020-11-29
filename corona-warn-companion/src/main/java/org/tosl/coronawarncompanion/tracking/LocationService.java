package org.tosl.coronawarncompanion.tracking;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class LocationService extends Service {
    private int counter = 0;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private Timer timer = null;
    private TimerTask timerTask = null;
    TrackingDBHelper dbHelper;
    boolean killService = false;
    BroadcastReceiver service_broadcast_receiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent arg1) {
            killService = true;
            LocationService.this.stopSelf();
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new TrackingDBHelper(this);
        registerReceiver(service_broadcast_receiver, new IntentFilter("Service_Receiver"));
        Log.i("Location Service","started service");
        Toast.makeText(this,"Started tracking",Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            createNotificationChannel();
        } else {
            String NOTIFICATION_CHANNEL_ID = "org.tosl.coronawarncompanion";
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setContentTitle("App is running count::" + counter)
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            startForeground(
                    1,
                    notification
            );
        }
        requestLocationUpdates();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public void createNotificationChannel() {
        String NOTIFICATION_CHANNEL_ID = "org.tosl.coronawarncompanion";
        String channelName = "Location Background Service";
        NotificationChannel chan = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_NONE
        );
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running count::" + counter)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        NotificationManager manager = (NotificationManager)
                (getSystemService(Context.NOTIFICATION_SERVICE));
        manager.createNotificationChannel(chan);
        startForeground(200, notification);
    }

    public void requestLocationUpdates() {
        LocationRequest request = new LocationRequest();
        request.setInterval(60000);
        request.setFastestInterval(60000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        FusedLocationProviderClient client  =
                LocationServices.getFusedLocationProviderClient(this);

        int permission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        );


        LocationCallback locationCallback = new LocationCallback() {


            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    counter++;
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    Log.i("Location Service", "long:"+longitude+":lat:"+latitude+":"+counter);
                    insertNewLocation(longitude,latitude);
                }
            }
        };
        if (permission == PackageManager.PERMISSION_GRANTED) { // Request location updates and when an update is
            // received, store the location in Firebase
            client.requestLocationUpdates(request,locationCallback, null);
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startTimer();
        return START_STICKY;
    }

    public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void insertNewLocation(double longitude, double latitude){
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        String time = java.text.DateFormat.getDateTimeInstance().format(new Date());
        values.put(TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE, ""+longitude);
        values.put(TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE, ""+latitude);
        values.put(TrackingData.TrackingEntry.COLUMN_NAME_TIME, ""+time);

        long newRowId = db.insert(TrackingData.TrackingEntry.TABLE_NAME, null, values);
        Log.i("Location Service","inserted new row:"+newRowId);
    }

    public void startTimer() {
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                int count = counter++;
                if (latitude != 0.0 && longitude != 0.0) {
                    Log.i(
                            "Location::",
                            latitude + ":::" + longitude + "Count" +
                                    count
                    );
                }
            }
        };
        timer.schedule(
                timerTask,
                0,
                1000
        );//1 * 60 * 1000 1 minute
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!killService) {
            stoptimertask();
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("restartservice");
            broadcastIntent.setClass(this, RestartBackgroundService.class);
            this.sendBroadcast(broadcastIntent);
        }
        else {
            unregisterReceiver(service_broadcast_receiver);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
