package org.tosl.coronawarncompanion.gmsreadout;

import android.content.Context;

import org.tosl.coronawarncompanion.CWCApplication;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getSecondsFromDays;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, ListsPerDayUTC> mapOfDaysUTCAndListsOfRPIs;  // daysSinceEpoch, ListsPerDayUTC
    private final TreeMap<Integer, Integer> mapOfDailyCountsLocalTZ;  // daysSinceEpoch, numberOfEntries

    private CWCApplication app;
    private Context context;
    int timeZoneOffsetSeconds;

    public static class ListsPerDayUTC {
        public TreeSet<Integer> rpi32Bits = new TreeSet<>();              // Some of the RPI bytes, used for fast search - all day
        public TreeSet<Integer> rpi32BitsEarly = new TreeSet<>();         // (same) - first 2 hours of the day only
        public TreeSet<Integer> rpi32BitsLate = new TreeSet<>();          // (same) - last 2 hours of the day only
        public LinkedList<RpiEntry> rpiEntries = new LinkedList<>();      // Full RpiEntries
        public LinkedList<RpiEntry> rpiEntriesEarly = new LinkedList<>(); // (same) - first 2 hours of the day only
        public LinkedList<RpiEntry> rpiEntriesLate = new LinkedList<>();  // (same) - last 2 hours of the day only
    }

    public static class RpiEntry {
        public final byte[] rpi;  // RPI bytes
        public final ContactRecordsProtos.ContactRecords contactRecords;  // list of all ScanRecords
        public final int startTimeStampLocalTZ;  // the timestamp of the first ScanRecord in seconds
        public final int endTimeStampLocalTZ;  // the timestamp of the last ScanRecord in seconds

        public RpiEntry(byte[] rpiBytes, ContactRecordsProtos.ContactRecords contactRecords,
                        int startTimeStampLocalTZ, int endTimeStampLocalTZ) {
            this.rpi = rpiBytes;
            this.contactRecords = contactRecords;
            this.startTimeStampLocalTZ = startTimeStampLocalTZ;
            this.endTimeStampLocalTZ = endTimeStampLocalTZ;
        }
    }

    public RpiList(Context context) {
        mapOfDaysUTCAndListsOfRPIs = new TreeMap<>();
        mapOfDailyCountsLocalTZ = new TreeMap<>();
        this.context = context;
        app = (CWCApplication) context.getApplicationContext();
        timeZoneOffsetSeconds = app.getTimeZoneOffsetSeconds();
    }

    private Integer getIntegerFromFirstBytesOfByteArray(byte[] rpi) {
        return (rpi[0] | (rpi[1]<<8) | (rpi[2]<<16) | (rpi[3]<<24));
    }

    public void addEntry(Integer daysSinceEpoch, byte[] rpiBytes, ContactRecordsProtos.ContactRecords contactRecords) {
        boolean early = true;
        boolean late = true;

        // get start and end timestamps of the scan records (local time zone)
        int startTimeStampUTC = getSecondsFromDays(daysSinceEpoch);  // default value, only used if there is no contactRecord
        int endTimeStampUTC = startTimeStampUTC;                   // default value, only used if there is no contactRecord
        if (contactRecords.getRecordCount() > 0) {  // this check should be required only for DEMO mode
            startTimeStampUTC = contactRecords.getRecord(0).getTimestamp();
            endTimeStampUTC = contactRecords.getRecord(contactRecords.getRecordCount()-1).getTimestamp();
            if (TimeUnit.SECONDS.toHours(startTimeStampUTC) >= 2) {
                early = false;
            }
            if (TimeUnit.SECONDS.toHours(endTimeStampUTC) <= 21) {
                late = false;
            }
        }
        int startTimeStampInLocalTZ = startTimeStampUTC + timeZoneOffsetSeconds;
        int endTimeStampInLocalTZ = endTimeStampUTC + timeZoneOffsetSeconds;

        // add to RPI counter per day (local time zone)
        int daysSinceEpochLocalTZ = getDaysFromSeconds(startTimeStampInLocalTZ);
        if (!mapOfDailyCountsLocalTZ.containsKey(daysSinceEpochLocalTZ)) {  // day not yet in list, create new entry
            mapOfDailyCountsLocalTZ.put(daysSinceEpochLocalTZ, 0);
        }
        Integer dailyCount = mapOfDailyCountsLocalTZ.get(daysSinceEpochLocalTZ);
        if (dailyCount != null) {
            dailyCount++;
            mapOfDailyCountsLocalTZ.put(daysSinceEpochLocalTZ, dailyCount);
        }

        // add to the main map (mapOfDaysUTCAndListsOfRPIs)
        ListsPerDayUTC listsPerDayUTC;
        if (!mapOfDaysUTCAndListsOfRPIs.containsKey(daysSinceEpoch)) {  // day not yet in list, create new entry
            listsPerDayUTC = new ListsPerDayUTC();
            mapOfDaysUTCAndListsOfRPIs.put(daysSinceEpoch, listsPerDayUTC);
        }
        listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpoch);

        RpiList.RpiEntry rpiEntry = new RpiList.RpiEntry(rpiBytes, contactRecords, startTimeStampInLocalTZ, endTimeStampInLocalTZ);
        if (listsPerDayUTC != null) {
            listsPerDayUTC.rpi32Bits.add(getIntegerFromFirstBytesOfByteArray(rpiBytes));
            listsPerDayUTC.rpiEntries.add(rpiEntry);
            if (early) {
                listsPerDayUTC.rpi32BitsEarly.add(getIntegerFromFirstBytesOfByteArray(rpiBytes));
                listsPerDayUTC.rpiEntriesEarly.add(rpiEntry);
            }
            if (late) {
                listsPerDayUTC.rpi32BitsLate.add(getIntegerFromFirstBytesOfByteArray(rpiBytes));
                listsPerDayUTC.rpiEntriesLate.add(rpiEntry);
            }
            mapOfDaysUTCAndListsOfRPIs.put(daysSinceEpoch, listsPerDayUTC);
        }
    }

    public LinkedList<RpiEntry> getRpiEntriesForDaysSinceEpoch(Integer daysSinceEpoch) {
        ListsPerDayUTC listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpoch);
        if (listsPerDayUTC != null) {
            return listsPerDayUTC.rpiEntries;
        } else {
            return null;
        }
    }

    public Integer getRpiCountForDaysSinceEpochLocalTZ(Integer daysSinceEpoch) {
        return mapOfDailyCountsLocalTZ.get(daysSinceEpoch);
    }

    /*
     Search for an RPI on a specific day,
     but also in the "late" entries (last 2 hours) of the previous day,
     and in the "early" entries (first 2 hours) of the next day.
     */
    public RpiEntry searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(byte[] searchRpi, Integer daysSinceEpochUTC) {
        RpiEntry foundRpiEntry = null;

        for (int i=1; i<=3; i++) {
            ListsPerDayUTC listsPerDayUTC = null;
            switch (i) {
                case 1:
                    listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC-1);
                    break;
                case 2:
                    listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC);
                    break;
                case 3:
                    listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC+1);
                    break;
            }
            if (listsPerDayUTC != null) {
                // Do a preliminary search on the first 32 bits of the RPI
                boolean preliminaryResultPositive = false;
                switch (i) {
                    case 1:
                        preliminaryResultPositive = listsPerDayUTC.rpi32BitsLate.contains(getIntegerFromFirstBytesOfByteArray(searchRpi));
                        break;
                    case 2:
                        preliminaryResultPositive = listsPerDayUTC.rpi32Bits.contains(getIntegerFromFirstBytesOfByteArray(searchRpi));
                        break;
                    case 3:
                        preliminaryResultPositive = listsPerDayUTC.rpi32BitsEarly.contains(getIntegerFromFirstBytesOfByteArray(searchRpi));
                        break;
                }
                if (preliminaryResultPositive) {
                    //Log.d(TAG, "Potential match found, based on 32 bits comparison!");

                    // Do a full search
                    LinkedList<RpiEntry> searchList = null;
                    switch (i) {
                        case 1:
                            searchList = listsPerDayUTC.rpiEntriesLate;
                            break;
                        case 2:
                            searchList = listsPerDayUTC.rpiEntries;
                            break;
                        case 3:
                            searchList = listsPerDayUTC.rpiEntriesEarly;
                            break;
                    }
                    for (RpiEntry rpiEntry : searchList) {
                        if (Arrays.equals(rpiEntry.rpi, searchRpi)) {
                            //Log.d(TAG, "Match confirmed!");
                            foundRpiEntry = rpiEntry;
                            break;
                        }
                    }
                    if (foundRpiEntry != null) {
                        break;
                    } else {
                        //Log.d(TAG, "Match based on 32 bits was not confirmed based on 128 bits.");
                    }
                } else {
                    //Log.d(TAG, "Match not found during this attempt.");
                }
            }
        }
        return foundRpiEntry;
    }

    public SortedSet<Integer> getAvailableDaysSinceEpoch() {
        return (SortedSet<Integer>) mapOfDaysUTCAndListsOfRPIs.keySet();
    }

    public SortedSet<Integer> getAvailableDaysSinceEpochLocalTZ() {
        return (SortedSet<Integer>) mapOfDailyCountsLocalTZ.keySet();
    }
}
