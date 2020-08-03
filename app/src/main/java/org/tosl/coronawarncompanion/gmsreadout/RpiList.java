package org.tosl.coronawarncompanion.gmsreadout;

import android.util.Log;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.matcher.Crypto;

import java.util.*;

import static java.lang.Math.abs;
import static org.tosl.coronawarncompanion.tools.Utils.getDateFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, ListsPerDayUTC> mapOfDaysUTCAndListsOfRPIs;  // daysSinceEpoch, ListsPerDayUTC
    private final TreeMap<Integer, Integer> mapOfDailyCountsLocalTZ;  // daysSinceEpoch, numberOfEntries

    private final CWCApplication app;
    final int timeZoneOffsetSeconds;

    public static class ListsPerDayUTC {
        public final TreeSet<Integer> rpi32Bits = new TreeSet<>();              // Some of the RPI bytes, used for fast search - all day
        public final TreeSet<Integer> rpi32BitsEarly = new TreeSet<>();         // (same) - first 2 hours of the day only
        public final TreeSet<Integer> rpi32BitsLate = new TreeSet<>();          // (same) - last 2 hours of the day only
        public final ArrayList<RpiEntry> rpiEntries = new ArrayList<>();        // Full RpiEntries
        public final ArrayList<RpiEntry> rpiEntriesEarly = new ArrayList<>();   // (same) - first 2 hours of the day only
        public final ArrayList<RpiEntry> rpiEntriesLate = new ArrayList<>();    // (same) - last 2 hours of the day only
    }

    public static class RpiEntry {
        public final byte[] rpi;  // RPI bytes
        public final ContactRecordsProtos.ContactRecords contactRecords;  // list of all ScanRecords
        public final int startTimeStampUTC;  // the timestamp of the first ScanRecord in seconds (UTC)
        public final int startTimeStampLocalTZ;  // the timestamp of the first ScanRecord in seconds (local time zone)
        public final int endTimeStampLocalTZ;  // the timestamp of the last ScanRecord in seconds (local time zone)

        public RpiEntry(byte[] rpiBytes, ContactRecordsProtos.ContactRecords contactRecords, int startTimeStampUTC,
                        int startTimeStampLocalTZ, int endTimeStampLocalTZ) {
            this.rpi = rpiBytes;
            this.contactRecords = contactRecords;
            this.startTimeStampUTC = startTimeStampUTC;
            this.startTimeStampLocalTZ = startTimeStampLocalTZ;
            this.endTimeStampLocalTZ = endTimeStampLocalTZ;
        }
    }

    public RpiList() {
        mapOfDaysUTCAndListsOfRPIs = new TreeMap<>();
        mapOfDailyCountsLocalTZ = new TreeMap<>();
        app = (CWCApplication) CWCApplication.getAppContext();
        timeZoneOffsetSeconds = app.getTimeZoneOffsetSeconds();
    }

    private Integer getIntegerFromFirstBytesOfByteArray(byte[] rpi) {
        return (rpi[0] | (rpi[1]<<8) | (rpi[2]<<16) | (rpi[3]<<24));
    }

    public void addEntry(Integer daysSinceEpoch, byte[] rpiBytes, ContactRecordsProtos.ContactRecords contactRecords) {
        if (contactRecords.getRecordCount() > 0) {  // this check should be required only for DEMO mode --> ignore entries with empty contactRecords
            boolean early = true;
            boolean late = true;

            // get start and end timestamps of the scan records (UTC)
            int startTimeStampUTC = contactRecords.getRecord(0).getTimestamp();
            int endTimeStampUTC = contactRecords.getRecord(contactRecords.getRecordCount() - 1).getTimestamp();

            Calendar startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startTime.setTimeInMillis(getMillisFromSeconds(startTimeStampUTC));
            int startHour = startTime.get(Calendar.HOUR_OF_DAY);
            Calendar endTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endTime.setTimeInMillis(getMillisFromSeconds(endTimeStampUTC));
            int endHour = endTime.get(Calendar.HOUR_OF_DAY);

            if (startHour >= 2) {
                early = false;
            }
            if (endHour <= 21) {
                late = false;
            }

            // also get them in local time zone
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

            RpiList.RpiEntry rpiEntry = new RpiList.RpiEntry(rpiBytes, contactRecords,
                    startTimeStampUTC, startTimeStampInLocalTZ, endTimeStampInLocalTZ);
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
    }

    public ArrayList<RpiEntry> getRpiEntriesForDaysSinceEpoch(Integer daysSinceEpoch) {
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
    public RpiEntry searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(Crypto.RpiWithInterval searchRpiWithInterval,
                                                                      Integer daysSinceEpochUTC) {
        RpiEntry matchingRpiEntry = null;

        for (int i=1; i<=3; i++) {  // search in (1) yesterday's "late" list, (2) today's full list, and (3) tomorrow's "early" list
            ListsPerDayUTC listsPerDayUTC = null;
            switch (i) {
                case 1: listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC-1); break;
                case 2: listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC); break;
                case 3: listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC+1); break;
            }
            if (listsPerDayUTC != null) {
                // Do a preliminary search on the first 32 bits of the RPI
                boolean preliminaryResultPositive = false;
                switch (i) {
                    case 1: preliminaryResultPositive = listsPerDayUTC.rpi32BitsLate.
                            contains(getIntegerFromFirstBytesOfByteArray(searchRpiWithInterval.rpiBytes)); break;
                    case 2: preliminaryResultPositive = listsPerDayUTC.rpi32Bits.
                            contains(getIntegerFromFirstBytesOfByteArray(searchRpiWithInterval.rpiBytes)); break;
                    case 3: preliminaryResultPositive = listsPerDayUTC.rpi32BitsEarly.
                            contains(getIntegerFromFirstBytesOfByteArray(searchRpiWithInterval.rpiBytes)); break;
                }
                if (preliminaryResultPositive) {
                    //Log.d(TAG, "Potential match found, based on 32 bits comparison!");

                    // Do a full search
                    ArrayList<RpiEntry> searchList = null;
                    switch (i) {
                        case 1: searchList = listsPerDayUTC.rpiEntriesLate; break;
                        case 2: searchList = listsPerDayUTC.rpiEntries; break;
                        case 3: searchList = listsPerDayUTC.rpiEntriesEarly; break;
                    }
                    for (RpiEntry rpiEntry : searchList) {
                        if (Arrays.equals(rpiEntry.rpi, searchRpiWithInterval.rpiBytes)) {
                            //Log.d(TAG, "RPI match confirmed! "+byteArrayToHex(rpiEntry.rpi));
                            if (abs(searchRpiWithInterval.intervalNumber -
                                    getENINFromSeconds(rpiEntry.startTimeStampUTC)) <= 6*2) {  // max diff: 2 hours
                                //Log.d(TAG, "Match fully confirmed!");
                                //Log.d(TAG, "ENIN used for RPI generation: "+searchRpiWithInterval.intervalNumber+
                                //        " ("+getDateFromENIN(searchRpiWithInterval.intervalNumber)+")");
                                //Log.d(TAG, "ENIN when scan was recorded:  "+getENINFromSeconds(rpiEntry.startTimeStampUTC)+
                                //        " ("+getDateFromENIN(getENINFromSeconds(rpiEntry.startTimeStampUTC))+")");
                                matchingRpiEntry = rpiEntry;
                                break;
                            } else {
                                Log.i(TAG, "Match could not be confirmed because time offset was too large!");
                                Log.i(TAG, "ENIN used for RPI generation: "+searchRpiWithInterval.intervalNumber+
                                        " ("+getDateFromENIN(searchRpiWithInterval.intervalNumber)+")");
                                Log.i(TAG, "ENIN when scan was recorded:  "+getENINFromSeconds(rpiEntry.startTimeStampUTC)+
                                        " ("+getDateFromENIN(getENINFromSeconds(rpiEntry.startTimeStampUTC))+")");
                            }
                        }
                    }
                    if (matchingRpiEntry != null) {
                        break;
                    } else {
                        //Log.d(TAG, "Match based on 32 bits was not confirmed based on 128 bits.");
                    }
                } else {
                    //Log.d(TAG, "Match not found during this attempt.");
                }
            }
        }
        return matchingRpiEntry;
    }

    public SortedSet<Integer> getAvailableDaysSinceEpoch() {
        return (SortedSet<Integer>) mapOfDaysUTCAndListsOfRPIs.keySet();
    }

    public SortedSet<Integer> getAvailableDaysSinceEpochLocalTZ() {
        return (SortedSet<Integer>) mapOfDailyCountsLocalTZ.keySet();
    }
}
