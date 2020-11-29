/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tosl.coronawarncompanion;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.tosl.coronawarncompanion.tracking.LocationService;
import org.tosl.coronawarncompanion.tracking.TrackingDBHelper;
import org.tosl.coronawarncompanion.tracking.TrackingData;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


public class TrackingActivity extends AppCompatActivity implements
        GoogleMap.OnMarkerClickListener,
        OnMapReadyCallback {
    private TrackingDBHelper dbHelper;
    private GoogleMap mMap;

    public boolean isLocationEnabledOrNot(Context context) {
        LocationManager locationManager = null;
        locationManager =
                (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER);
    }

    public void showAlertLocation(Context context, String title, String message, String btnText) {
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(Dialog.BUTTON_POSITIVE,"OK", new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface d, int arg1) {
                alertDialog.dismiss();
                context.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));

            };

    });
        alertDialog.show();
}

public void requestPermission(){
    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {


        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                200);
    }
}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                "corona_warn_app_tracking", Context.MODE_PRIVATE);
        boolean isTrackingEnabled =  sharedPref.getBoolean("TrackingEnabled", false);
        ((ToggleButton)findViewById(R.id.toggle_button_tracking)).setChecked(isTrackingEnabled);
        Calendar calendar = Calendar.getInstance();
        Date currentTime = calendar.getTime();
        calendar.add(Calendar.HOUR,-1);
        Date currentTime2 = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        ((EditText)findViewById(R.id.tracking_time)).setText(sdf.format(currentTime2)+" - "+sdf.format(currentTime));
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        dbHelper = new TrackingDBHelper(this);
        if (!isLocationEnabledOrNot(this)) {
            showAlertLocation(
                    this,
                    getResources().getString(R.string.title_enable_gps),
                    getResources().getString(R.string.info_enable_gps),
                    "OK"
            );
        }
        requestPermission();
        ((ToggleButton)findViewById(R.id.toggle_button_tracking)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

                    SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                            "corona_warn_app_tracking", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean("TrackingEnabled", b);
                    editor.apply();
                    if (b) {
                        startService(new Intent(TrackingActivity.this, LocationService.class));
                    }
                    else {
                        Intent intent = new Intent("Service_Receiver");
                        intent.putExtra("kill_service",true);
                        sendBroadcast(intent);
                    }
            }
        });
        findViewById(R.id.getPosition).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                InputMethodManager inputManager = (InputMethodManager) TrackingActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(TrackingActivity.this.getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
                String dateString = ((EditText) findViewById(R.id.tracking_time)).getText().toString();
                dateString = dateString.replace(" - ","-");
                String[] dateStringArray = dateString.split("-");
                if (dateStringArray.length == 1) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                    long minDiff = -1;
                    String resultTime = "";
                    String resultLongitude = "";
                    String resultLatitude = "";
                    try {
                        Date searchTime = sdf.parse(dateString);
                        SQLiteDatabase db = dbHelper.getReadableDatabase();

                        String[] projection = {
                                BaseColumns._ID,
                                TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE,
                                TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE,
                                TrackingData.TrackingEntry.COLUMN_NAME_TIME
                        };

                        String sortOrder =
                                BaseColumns._ID + " DESC";

                        Cursor cursor = db.query(
                                TrackingData.TrackingEntry.TABLE_NAME,   // The table to query
                                projection,             // The array of columns to return (pass null to get all)
                                null,              // The columns for the WHERE clause
                                null,          // The values for the WHERE clause
                                null,                   // don't group the rows
                                null,                   // don't filter by row groups
                                sortOrder               // The sort order
                        );
                        while (cursor.moveToNext()) {
                            String time = cursor.getString(
                                    cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_TIME));
                            String longitude = cursor.getString(
                                    cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE));
                            String latitude = cursor.getString(
                                    cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE));
                            Date entryTime = sdf.parse(time);
                            if (minDiff == -1) {
                                minDiff = Math.abs(entryTime.getTime() - searchTime.getTime());
                                resultTime = time;
                                resultLatitude = latitude;
                                resultLongitude = longitude;
                            } else {
                                if (Math.abs(entryTime.getTime() - searchTime.getTime()) < minDiff) {
                                    minDiff = Math.abs(entryTime.getTime() - searchTime.getTime());
                                    resultTime = time;
                                    resultLatitude = latitude;
                                    resultLongitude = longitude;
                                }
                            }
                        }
                        mMap.clear();
                        CameraUpdate center =
                                CameraUpdateFactory.newLatLng(new LatLng(Double.parseDouble(resultLatitude), Double.parseDouble(resultLongitude)));
                        CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);

                        mMap.moveCamera(center);
                        mMap.animateCamera(zoom);
                        mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(Double.parseDouble(resultLatitude), Double.parseDouble(resultLongitude)))
                                .title(resultTime));
                    } catch (ParseException e) {
                        Toast.makeText(TrackingActivity.this, R.string.wrong_date_format, Toast.LENGTH_LONG).show();
                    }
                } else {
                    String resultTime = "";
                    String resultLongitude = "";
                    String resultLatitude = "";
                    String previousResultTime = "";
                    String previousResultLongitude = "";
                    String previousResultLatitude = "";

                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                    try {
                        Date startTime = sdf.parse(dateStringArray[0]);
                        Date endTime = sdf.parse(dateStringArray[1]);
                        if (endTime.getTime() < startTime.getTime()){
                            Toast.makeText(TrackingActivity.this, R.string.endtime_before_starttime,Toast.LENGTH_LONG).show();
                        }
                        else {
                            SQLiteDatabase db = dbHelper.getReadableDatabase();

                            String[] projection = {
                                    BaseColumns._ID,
                                    TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE,
                                    TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE,
                                    TrackingData.TrackingEntry.COLUMN_NAME_TIME
                            };

                            String sortOrder =
                                    BaseColumns._ID + " DESC";

                            Cursor cursor = db.query(
                                    TrackingData.TrackingEntry.TABLE_NAME,   // The table to query
                                    projection,             // The array of columns to return (pass null to get all)
                                    null,              // The columns for the WHERE clause
                                    null,          // The values for the WHERE clause
                                    null,                   // don't group the rows
                                    null,                   // don't filter by row groups
                                    sortOrder               // The sort order
                            );
                            mMap.clear();

                            while (cursor.moveToNext()) {
                                String time = cursor.getString(
                                        cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_TIME));
                                String longitude = cursor.getString(
                                        cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE));
                                String latitude = cursor.getString(
                                        cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE));
                                Date entryTime = sdf.parse(time);
                                if ((entryTime.getTime() <= endTime.getTime()) && (entryTime.getTime() >= startTime.getTime())) {

                                    resultTime = time;
                                    resultLatitude = latitude;
                                    resultLongitude = longitude;

                                    if (!previousResultTime.equals("")) {
                                        Polyline line = mMap.addPolyline(new PolylineOptions()
                                                .add(new LatLng(Double.parseDouble(resultLatitude), Double.parseDouble(resultLongitude)), new LatLng(Double.parseDouble(previousResultLatitude), Double.parseDouble(previousResultLongitude)))
                                                .width(5)
                                                .color(Color.RED));

                                    }

                                    previousResultTime = time;
                                    previousResultLatitude = latitude;
                                    previousResultLongitude = longitude;
                                }
                            }
                            if (!resultTime.equals("")) {
                                CameraUpdate center =
                                        CameraUpdateFactory.newLatLng(new LatLng(Double.parseDouble(resultLatitude), Double.parseDouble(resultLongitude)));
                                CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);

                                mMap.moveCamera(center);
                                mMap.animateCamera(zoom);
                            }

                        }
                } catch (ParseException e) {
                    Toast.makeText(TrackingActivity.this, R.string.wrong_date_format, Toast.LENGTH_LONG).show();
                }
                }
            }
        });
        findViewById(R.id.showTrackingData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(TrackingActivity.this);
                builder.setTitle(R.string.trackingdata);
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String[] projection = {
                        BaseColumns._ID,
                        TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE,
                        TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE,
                        TrackingData.TrackingEntry.COLUMN_NAME_TIME
                };



// How you want the results sorted in the resulting Cursor
                String sortOrder =
                        BaseColumns._ID + " DESC";

                Cursor cursor = db.query(
                        TrackingData.TrackingEntry.TABLE_NAME,   // The table to query
                        projection,             // The array of columns to return (pass null to get all)
                        null,              // The columns for the WHERE clause
                        null,          // The values for the WHERE clause
                        null,                   // don't group the rows
                        null,                   // don't filter by row groups
                        sortOrder               // The sort order
                );
                List<String> entries = new ArrayList<String>();
                while(cursor.moveToNext()) {
                    String time = cursor.getString(
                            cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_TIME));
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                    long diffMinutes = 0;
                    try {
                        Date entryTime = sdf.parse(time);
                        Date currentTime = Calendar.getInstance().getTime();
                        long diffSeconds = (currentTime.getTime()-entryTime.getTime())/1000;
                        diffMinutes = diffSeconds / 60;

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    String entry = cursor.getString(
                            cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_LATITUDE))+
                            ":"+cursor.getString(
                            cursor.getColumnIndexOrThrow(TrackingData.TrackingEntry.COLUMN_NAME_LONGITUDE))+
                            ": "+ getResources().getString(R.string.before)+" "+diffMinutes+" "+ getResources().getString(R.string.minutes);
                    entries.add(entry);
                }

                cursor.close();

                String[] items = entries.toArray(new String[0]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                    });
                builder.show();
            }
        });
        // Action Bar:
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_activity_tracking);
        }

    }
    public void startLocationService(){

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

}