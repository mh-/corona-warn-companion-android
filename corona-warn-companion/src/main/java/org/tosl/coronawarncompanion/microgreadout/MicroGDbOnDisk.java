package org.tosl.coronawarncompanion.microgreadout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.rpis.RpiList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.tosl.coronawarncompanion.gmsreadout.ContactDbOnDisk.deleteDir;
import static org.tosl.coronawarncompanion.gmsreadout.Sudo.sudo;
import static org.tosl.coronawarncompanion.tools.Utils.byteArrayToHexString;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromMillis;

public class MicroGDbOnDisk {

    private static final String TAG = "MicroGDbOnDisk";

    @SuppressLint("SdCardPath")
    private static final String gmsPathStr = "/data/data/com.google.android.gms/databases";
    private static final String dbName = "exposure.db";
    private static final String dbNameModifier = "_";
    private static final String dbNameModified = dbName+dbNameModifier;
    private static String cachePathStr = "";
    private static File cacheDir = null;

    private final Context context;

    public MicroGDbOnDisk(Context context) {
        this.context = context;
    }

    public void copyFromGMS() {
        // Copy the microG GMS database to local app cache
        Log.d(TAG, "Trying to copy microG database");
        cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();
        }
        assert cacheDir != null;
        cachePathStr = cacheDir.getPath();

        try {
            File testFile = File.createTempFile("test_file", null, cacheDir);
            FileOutputStream stream = new FileOutputStream(testFile);
            try {
                stream.write("test".getBytes());
                // get owner of testFile
                String owner = sudo("ls -l "+testFile+"|head -n 1|cut -d \" \" -f 3").replace("\n", "");
                Log.d(TAG, "Cache file owner: "+owner);
                // get group of testFile
                String group = sudo("ls -l "+testFile+"|head -n 1|cut -d \" \" -f 4").replace("\n", "");
                Log.d(TAG, "Cache file group: "+group);
                // get context of testFile
                String context = sudo("ls -Z "+testFile+"|head -n 1|cut -d \" \" -f 1").replace("\n", "");
                Log.d(TAG, "Cache file context: "+context);

                // First rename the database, then copy it, then rename to the original name
                String result = sudo(
                        "rm "+cachePathStr+"/"+dbNameModified,
                        "mv "+gmsPathStr+"/"+dbName+" "+gmsPathStr+"/"+dbNameModified,
                        "cp "+gmsPathStr+"/"+dbNameModified+" "+cachePathStr+"/",
                        "mv "+gmsPathStr+"/"+dbNameModified+" "+gmsPathStr+"/"+dbName,
                        "ls -lZ "+cachePathStr+"/"+dbNameModified
                );
                Log.d(TAG, "Result from trying to copy database: "+result);
                if (result.length() < 10) {
                    Log.e(TAG, "ERROR: Super User rights not granted!");
                }

                // set owner and context (required for Android 11), and group as well
                result = sudo(
                        "chown -R "+owner+" "+cachePathStr+"/"+dbNameModified,
                        "chgrp -R "+group+" "+cachePathStr+"/"+dbNameModified,
                        "chcon -R "+context+" "+cachePathStr+"/"+dbNameModified,
                        "ls -lZ "+cachePathStr+"/"+dbNameModified
                );
                Log.d(TAG, "Result from trying to set owner, group and context: "+result);
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public RpiList getRpisFromContactDB(Activity activity, File databaseFile) {
        RpiList rpiList = null;

        String databasePath = "";
        // Only copy from gms if databaseFile is null, else directly read from there
        if (databaseFile == null) {
            copyFromGMS();
            databasePath = cachePathStr + "/" + dbNameModified;
        } else {
            databasePath = databaseFile.getPath();
        }

        Log.d(TAG, "Loading microG " + databasePath);

        try (SQLiteDatabase microGDb = SQLiteDatabase.openDatabase(databasePath,
                null, SQLiteDatabase.OPEN_READONLY)) {

            if (microGDb != null) {
                Log.d(TAG, "Opened microG Database: " + databasePath);

                Cursor cursor = microGDb.rawQuery("SELECT rpi, aem, timestamp, rssi, duration "+
                        "FROM advertisements", null);

                rpiList = new RpiList();

                while (cursor.moveToNext()) {
                    // parse entry from table "advertisements"
                    byte[] rpiBytes = cursor.getBlob(0);
                    if (rpiBytes == null) {
                        Log.w(TAG, "Warning: Found rpiBytes == null");
                    } else {
                        byte[] aemBytes = cursor.getBlob(1);
                        if (aemBytes == null) {
                            Log.w(TAG, "Warning: Found aemBytes == null");
                        } else {
                            long timestampMs = cursor.getLong(2);
                            int rssi = (int) cursor.getLong(3);
                            int duration = cursor.getInt(4);

                            Log.d(TAG, "Scan read: " + byteArrayToHexString(rpiBytes) + " " + byteArrayToHexString(aemBytes) +
                                    " RSSI: " + rssi + ", Timestamp: " + timestampMs + ", Duration: " + duration);

                            // limit RSSI, which could be a very large number, because of this bug: https://github.com/microg/android_packages_apps_GmsCore/issues/1230
                            if (rssi < -200L) rssi = -200;
                            if (rssi > +200L) rssi = +200;

                            // add scanRecord to contactRecords
                            ContactRecordsProtos.ContactRecords.Builder contactRecordsBuilder =
                                    ContactRecordsProtos.ContactRecords.newBuilder();
                            @SuppressWarnings("deprecation") ContactRecordsProtos.ScanRecord scanRecord = ContactRecordsProtos.ScanRecord.newBuilder()
                                    .setTimestamp((int)(timestampMs/1000L))
                                    .setRssi(rssi)
                                    .setAem(ByteString.copyFrom(aemBytes))
                                    .build();
                            contactRecordsBuilder.addRecord(scanRecord);
                            //noinspection deprecation
                            scanRecord = ContactRecordsProtos.ScanRecord.newBuilder()
                                    .setTimestamp((int)((timestampMs+duration)/1000L))
                                    .setRssi(rssi)
                                    .setAem(ByteString.copyFrom(aemBytes))
                                    .build();
                            contactRecordsBuilder.addRecord(scanRecord);

                            // store entry (incl. contactRecords) in rpiList
                            int daysSinceEpochUTC = getDaysFromMillis(timestampMs);
                            rpiList.addEntry(daysSinceEpochUTC, rpiBytes, contactRecordsBuilder.build());
                            if (getDaysFromMillis(timestampMs+duration) != daysSinceEpochUTC) {  // extremely unlikely
                                rpiList.addEntry(daysSinceEpochUTC + 1, rpiBytes, contactRecordsBuilder.build());
                            }
                        }
                    }
                }
                cursor.close();
            }
            Log.d(TAG, "Successfully parsed microG database.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        deleteDir(cacheDir);
        return rpiList;
    }
}