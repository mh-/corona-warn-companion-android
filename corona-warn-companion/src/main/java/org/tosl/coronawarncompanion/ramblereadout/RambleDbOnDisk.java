package org.tosl.coronawarncompanion.ramblereadout;

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

import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.hexStringToByteArray;

public class RambleDbOnDisk {

    private static final String TAG = "RambleDbOnDisk";
    private final Context context;
    private final Uri uri;

    public RambleDbOnDisk(Context context, Uri uri) {
        this.context = context;
        this.uri = uri;
        Log.d(TAG, "Selected RaMBLE file: " + uri.toString());
    }

    public RpiList getRpisFromContactDB(Integer minDaysSinceEpochUTC) {
        RpiList rpiList = null;

        InputStream inputStream;
        try {
            inputStream = this.context.getContentResolver().openInputStream(uri);
            File file = File.createTempFile("ramble", "sqlite");

            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buff = new byte[1024];
            int read;
            while ((read = inputStream.read(buff, 0, buff.length)) > 0)
                outputStream.write(buff, 0, read);
            inputStream.close();
            outputStream.close();

            try (SQLiteDatabase rambleDb = SQLiteDatabase.openDatabase(file.getPath(),
                    null, SQLiteDatabase.OPEN_READONLY)) {

                if (rambleDb != null) {
                    Log.d(TAG, "Opened temporary copy of the RaMBLE Database: " + file.getPath());

                    Cursor cursor = rambleDb.rawQuery("SELECT service_data, first_seen, last_seen, id " +
                            "FROM devices WHERE service_uuids='fd6f'", null);

                    rpiList = new RpiList();
                    rpiList.setHaveLocation(false);

                    while (cursor.moveToNext()) {
                        // parse entry from table "devices"
                        String lastSeenStr = cursor.getString(2);
                        if (lastSeenStr == null) {
                            Log.w(TAG, "Warning: Found lastSeenStr == null");
                        } else {
                            int lastSeenTimestamp = 0;
                            try {
                                lastSeenTimestamp = Integer.parseInt(lastSeenStr);
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "Warning: Found lastSeenTimestamp with unparseable number");
                            }
                            // only use entry if it's recent enough
                            if (getDaysFromSeconds(lastSeenTimestamp) >= minDaysSinceEpochUTC) {
                                String serviceRpiAemStr = cursor.getString(0);
                                if (serviceRpiAemStr == null) {
                                    Log.w(TAG, "Warning: Found serviceRpiAemStr == null");
                                } else {
                                    String[] service_data_substrings = serviceRpiAemStr.split(":");
                                    if (service_data_substrings.length != 2) {
                                        Log.w(TAG, "Warning: Found service_data_substrings.length != 2");
                                    } else {
                                        if (!service_data_substrings[0].equalsIgnoreCase("fd6f")) {
                                            Log.w(TAG, "Warning: Found service_data_substrings[0] != fd6f");
                                        } else {
                                            String rpiAemStr = service_data_substrings[1];
                                            if (rpiAemStr.length() != 2 * 16 + 2 * 4) {
                                                Log.w(TAG, "Warning: Found rpiAemStr with incorrect length");
                                            } else {
                                                String rpiStr = rpiAemStr.substring(0, 16 * 2);
                                                byte[] rpiBytes = hexStringToByteArray(rpiStr);
                                                String aemStr = rpiAemStr.substring(16 * 2);
                                                byte[] aemBytes = hexStringToByteArray(aemStr);
                                                String firstSeenStr = cursor.getString(1);
                                                if (firstSeenStr == null) {
                                                    Log.w(TAG, "Warning: Found firstSeenStr == null");
                                                } else {
                                                    int firstSeenTimestamp = 0;
                                                    try {
                                                        firstSeenTimestamp = Integer.parseInt(firstSeenStr);
                                                    } catch (NumberFormatException e) {
                                                        Log.w(TAG, "Warning: Found firstSeenTimestamp with unparseable number");
                                                    }
                                                    if (firstSeenTimestamp != 0) {
                                                        // firstSeenTimestamp might be too old, in case of BDADDR collisions
                                                        // see https://github.com/mh-/corona-warn-companion-android/issues/62
                                                        // Therefore we limit the interval to max. 30 minutes
                                                        if (firstSeenTimestamp < lastSeenTimestamp - 30 * 60) {
                                                            firstSeenTimestamp = lastSeenTimestamp - 30 * 60;
                                                        }
                                                        String idStr = cursor.getString(3);
                                                        if (idStr == null) {
                                                            Log.w(TAG, "Warning: Found idStr == null");
                                                        } else {
                                                            // Log.d(TAG, "Device seen: " + byteArrayToHexString(rpiBytes) + " " + byteArrayToHexString(aemBytes) +
                                                            //        " " + firstSeenTimestamp + "-" + lastSeenTimestamp + " " + idStr);

                                                            // get Scan Records from table "locations"
                                                            ContactRecordsProtos.ContactRecords.Builder contactRecordsBuilder =
                                                                    ContactRecordsProtos.ContactRecords.newBuilder();
                                                            Cursor cursor2 = rambleDb.rawQuery("SELECT timestamp, rssi, longitude, latitude " +
                                                                    "FROM locations WHERE device_id=" + idStr, null);
                                                            while (cursor2.moveToNext()) {
                                                                String timestampStr = cursor2.getString(0);
                                                                if (timestampStr == null) {
                                                                    Log.w(TAG, "Warning: Found timestampStr == null");
                                                                } else {
                                                                    int timestamp = Integer.parseInt(timestampStr);
                                                                    // check if this belongs to the (potentially corrected) time interval:
                                                                    if (timestamp >= firstSeenTimestamp) {
                                                                        String rssiStr = cursor2.getString(1);
                                                                        if (rssiStr == null) {
                                                                            Log.w(TAG, "Warning: Found rssiStr == null");
                                                                        } else {
                                                                            int rssi = Integer.parseInt(rssiStr);
                                                                            //Log.d(TAG, "Scan events: " + timestamp + " " + rssi);

                                                                            String longitudeStr = cursor2.getString(2);
                                                                            String latitudeStr = cursor2.getString(3);

                                                                            if (longitudeStr == null || latitudeStr == null) {
                                                                                Log.w(TAG, "Warning: Found longitudeStr == null || latitudeStr == null");

                                                                                // add scanRecord to contactRecords
                                                                                ContactRecordsProtos.ScanRecord scanRecord = ContactRecordsProtos.ScanRecord.newBuilder()
                                                                                        .setTimestamp(timestamp)
                                                                                        .setRssi(rssi)
                                                                                        .setAem(ByteString.copyFrom(aemBytes))
                                                                                        .build();
                                                                                contactRecordsBuilder.addRecord(scanRecord);
                                                                            } else {
                                                                                //Log.d(TAG, "longitude: " + longitudeStr + " / latitude: " + latitudeStr);

                                                                                // add scanRecord to contactRecords
                                                                                ContactRecordsProtos.ScanRecord scanRecord = ContactRecordsProtos.ScanRecord.newBuilder()
                                                                                        .setTimestamp(timestamp)
                                                                                        .setRssi(rssi)
                                                                                        .setAem(ByteString.copyFrom(aemBytes))
                                                                                        .setLongitude(Double.parseDouble(longitudeStr))
                                                                                        .setLatitude(Double.parseDouble(latitudeStr))
                                                                                        .build();
                                                                                contactRecordsBuilder.addRecord(scanRecord);
                                                                                rpiList.setHaveLocation(true);
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            cursor2.close();

                                                            // store entry (incl. contactRecords) in rpiList
                                                            int daysSinceEpochUTC = getDaysFromSeconds(firstSeenTimestamp);
                                                            rpiList.addEntry(daysSinceEpochUTC, rpiBytes, contactRecordsBuilder.build());
                                                            if (getDaysFromSeconds(lastSeenTimestamp) != daysSinceEpochUTC) {  // extremely unlikely
                                                                rpiList.addEntry(daysSinceEpochUTC + 1, rpiBytes, contactRecordsBuilder.build());
                                                            }
                                                            //Log.d(TAG, "Added entry to rpiList.");
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    cursor.close();
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
