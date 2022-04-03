package org.tosl.coronawarncompanion.microgreadout;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.rpis.RpiList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromMillis;

// Reads a CCTG / microg database file that the user should have copied
// from /data/data/de.corona.tracing/databases/exposure.db
// to a temporary location of their choice.
// User can e.g. do
// adb shell su -c cp /data/data/de.corona.tracing/databases/exposure.db /storage/emulated/0/Download/

public class CctgDbOnDisk {
    private static final String TAG = "CctgDbOnDisk";
    private final Context context;
    private final Uri uri;

    public CctgDbOnDisk(Context context, Uri uri) {
        this.context = context;
        this.uri = uri;
        Log.d(TAG, "Selected CCTG file: " + uri.toString());
    }

    public RpiList getRpisFromContactDB() {
        RpiList rpiList = null;

        InputStream inputStream;
        try {
            inputStream = this.context.getContentResolver().openInputStream(uri);
            File file = File.createTempFile("cctg", "sqlite");

            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buff = new byte[1024];
            int read;
            while ((read = inputStream.read(buff, 0, buff.length)) > 0)
                outputStream.write(buff, 0, read);
            inputStream.close();
            outputStream.close();

            try (SQLiteDatabase microGDb = SQLiteDatabase.openDatabase(file.getPath(),
                    null, SQLiteDatabase.OPEN_READONLY)) {

                if (microGDb != null) {
                    Log.d(TAG, "Opened temporary copy of the CCTG Database: " + file.getPath());

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

                                //Log.d(TAG, "Scan read: " + byteArrayToHexString(rpiBytes) + " " + byteArrayToHexString(aemBytes) +
                                //        " RSSI: " + rssi + ", Timestamp: " + timestampMs + ", Duration: " + duration);

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
                    microGDb.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rpiList;
    }
}
