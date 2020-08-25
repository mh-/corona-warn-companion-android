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

package org.tosl.coronawarncompanion.rpis;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matcher.Crypto;

import java.util.*;

import static java.lang.Math.abs;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;

public class RpiList {
    private static final String TAG = "RpiList";

    private final Map<Integer, ListsPerDayUTC> mapOfDaysUTCAndListsOfRPIs;  // daysSinceEpochUTC, ListsPerDayUTC
    private final Map<Integer, Integer> mapOfDailyCountsLocalTZ;  // daysSinceEpochLocalTZ, numberOfEntries

    final int timeZoneOffsetSeconds;

    public static class ListsPerDayUTC {
        public final HashMap<RpiBytes, RpiEntry> rpiEntries = new HashMap<>(2048);     // RpiEntries
        public final HashMap<RpiBytes, RpiEntry> rpiEntriesEarly = new HashMap<>(512); // (same) - first 2 hours of the day only
        public final HashMap<RpiBytes, RpiEntry> rpiEntriesLate = new HashMap<>(512);  // (same) - last 2 hours of the day only
    }

    public static class RpiBytes {
        private final int[] values = {0, 0, 0, 0};

        public RpiBytes(byte[] bytes) {
            values[0] = ((bytes[0] & 0xFF) << 24) |
                    ((bytes[1] & 0xFF) << 16) |
                    ((bytes[2] & 0xFF) << 8) |
                    ((bytes[3] & 0xFF));
            values[1] = ((bytes[4] & 0xFF) << 24) |
                    ((bytes[5] & 0xFF) << 16) |
                    ((bytes[6] & 0xFF) << 8) |
                    ((bytes[7] & 0xFF));
            values[2] = ((bytes[8] & 0xFF) << 24) |
                    ((bytes[9] & 0xFF) << 16) |
                    ((bytes[10] & 0xFF) << 8) |
                    ((bytes[11] & 0xFF));
            values[3] = ((bytes[12] & 0xFF) << 24) |
                    ((bytes[13] & 0xFF) << 16) |
                    ((bytes[14] & 0xFF) << 8) |
                    ((bytes[15] & 0xFF));
        }

        public byte[] getBytes() {
            byte[] bytes = new byte[16];
            bytes[0] =  (byte) ((values[0] & 0xFF000000) >> 24);
            bytes[1] =  (byte) ((values[0] & 0x00FF0000) >> 16);
            bytes[2] =  (byte) ((values[0] & 0x0000FF00) >> 8);
            bytes[3] =  (byte) ((values[0] & 0x000000FF));
            bytes[4] =  (byte) ((values[1] & 0xFF000000) >> 24);
            bytes[5] =  (byte) ((values[1] & 0x00FF0000) >> 16);
            bytes[6] =  (byte) ((values[1] & 0x0000FF00) >> 8);
            bytes[7] =  (byte) ((values[1] & 0x000000FF));
            bytes[8] =  (byte) ((values[2] & 0xFF000000) >> 24);
            bytes[9] =  (byte) ((values[2] & 0x00FF0000) >> 16);
            bytes[10] = (byte) ((values[2] & 0x0000FF00) >> 8);
            bytes[11] = (byte) ((values[2] & 0x000000FF));
            bytes[12] = (byte) ((values[3] & 0xFF000000) >> 24);
            bytes[13] = (byte) ((values[3] & 0x00FF0000) >> 16);
            bytes[14] = (byte) ((values[3] & 0x0000FF00) >> 8);
            bytes[15] = (byte) ((values[3] & 0x000000FF));
            return bytes;
        }

        @Override
        public boolean equals(Object o) {
            // Check if o is an instance of RpiBytes or not
            // "null instanceof [type]" also returns false
            if (!(o instanceof RpiBytes)) {
                return false;
            }
            return Arrays.equals(this.values, ((RpiBytes)o).values);
        }

        @Override
        public int hashCode() {
            return values[0];
        }
    }

    public static class RpiEntry {
        public final RpiBytes rpiBytes;  // RPI bytes
        public final ContactRecordsProtos.ContactRecords contactRecords;  // list of all ScanRecords
        public final int startTimeStampUTC;  // the timestamp of the first ScanRecord in seconds (UTC)

        public RpiEntry(byte[] rpiBytes, ContactRecordsProtos.ContactRecords contactRecords,
                        int startTimeStampUTC) {
            this.rpiBytes = new RpiBytes(rpiBytes);
            this.contactRecords = contactRecords;
            this.startTimeStampUTC = startTimeStampUTC;
        }
    }

    public RpiList() {
        mapOfDaysUTCAndListsOfRPIs = new HashMap<>();
        mapOfDailyCountsLocalTZ = new TreeMap<>();
        timeZoneOffsetSeconds = CWCApplication.getTimeZoneOffsetSeconds();
    }

    public void addEntry(Integer daysSinceEpochUTC, byte[] rpiBytes, ContactRecordsProtos.ContactRecords contactRecords) {
        if (contactRecords.getRecordCount() > 0) {  // this check should be required only for DEMO mode --> ignore entries with empty contactRecords
            // get start and end timestamps of the scan records (UTC)
            int startTimeStampUTC = contactRecords.getRecord(0).getTimestamp();
            int endTimeStampUTC = contactRecords.getRecord(contactRecords.getRecordCount() - 1).getTimestamp();

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(getMillisFromSeconds(startTimeStampUTC));
            boolean early = (calendar.get(Calendar.HOUR_OF_DAY) <= 1);
            calendar.setTimeInMillis(getMillisFromSeconds(endTimeStampUTC));
            boolean late = (calendar.get(Calendar.HOUR_OF_DAY) >= 22);

            // also get the start timestamp in local time zone
            int startTimeStampInLocalTZ = startTimeStampUTC + timeZoneOffsetSeconds;

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
            if (!mapOfDaysUTCAndListsOfRPIs.containsKey(daysSinceEpochUTC)) {  // day not yet in list, create new entry
                listsPerDayUTC = new ListsPerDayUTC();
                mapOfDaysUTCAndListsOfRPIs.put(daysSinceEpochUTC, listsPerDayUTC);
            }
            listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC);

            RpiList.RpiEntry rpiEntry = new RpiList.RpiEntry(rpiBytes, contactRecords,
                    startTimeStampUTC);
            if (listsPerDayUTC != null) {
                listsPerDayUTC.rpiEntries.put(rpiEntry.rpiBytes, rpiEntry);
                if (early) {
                    listsPerDayUTC.rpiEntriesEarly.put(rpiEntry.rpiBytes, rpiEntry);
                }
                if (late) {
                    listsPerDayUTC.rpiEntriesLate.put(rpiEntry.rpiBytes, rpiEntry);
                }
                mapOfDaysUTCAndListsOfRPIs.put(daysSinceEpochUTC, listsPerDayUTC);
            }
        }
    }

    public Integer getRpiCountForDaysSinceEpochLocalTZ(Integer daysSinceEpochLocalTZ) {
        return mapOfDailyCountsLocalTZ.get(daysSinceEpochLocalTZ);
    }

    /*
     Search for an RPI on a specific day,
     but also in the "late" entries (last 2 hours) of the previous day,
     and in the "early" entries (first 2 hours) of the next day.
     */
    public RpiEntry searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(Crypto.RpiWithInterval searchRpiWithInterval,
                                                                      Integer daysSinceEpochUTC) {
        RpiEntry matchingRpiEntry = null;
        if (searchRpiWithInterval != null) {
            RpiBytes rpiBytes = new RpiBytes(searchRpiWithInterval.rpiBytes);

            for (int i=1; i<=3; i++) {  // search in (1) yesterday's "late" list, (2) today's full list, and (3) tomorrow's "early" list
                ListsPerDayUTC listsPerDayUTC = null;
                switch (i) {
                    case 1:
                        listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC - 1);
                        break;
                    case 2:
                        listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC);
                        break;
                    case 3:
                        listsPerDayUTC = mapOfDaysUTCAndListsOfRPIs.get(daysSinceEpochUTC + 1);
                        break;
                }
                if (listsPerDayUTC != null) {
                    RpiEntry rpiEntry = null;
                    switch (i) {
                        case 1:
                            if (listsPerDayUTC.rpiEntriesLate.containsKey(rpiBytes)) {
                                rpiEntry = listsPerDayUTC.rpiEntriesLate.get(rpiBytes);
                            }
                            break;
                        case 2:
                            if (listsPerDayUTC.rpiEntries.containsKey(rpiBytes)) {
                                rpiEntry = listsPerDayUTC.rpiEntries.get(rpiBytes);
                            }
                            break;
                        case 3:
                            if (listsPerDayUTC.rpiEntriesEarly.containsKey(rpiBytes)) {
                                rpiEntry = listsPerDayUTC.rpiEntriesEarly.get(rpiBytes);
                            }
                            break;
                    }
                    if (rpiEntry != null && abs(searchRpiWithInterval.intervalNumber -
                            getENINFromSeconds(rpiEntry.startTimeStampUTC)) <= 6 * 2) {  // max diff: 2 hours
                        //Log.d(TAG, "Match confirmed!");
                        //Log.d(TAG, "ENIN used for RPI generation: "+searchRpiWithInterval.intervalNumber+
                        //        " ("+getDateFromENIN(searchRpiWithInterval.intervalNumber)+")");
                        //Log.d(TAG, "ENIN when scan was recorded:  "+getENINFromSeconds(rpiEntry.startTimeStampUTC)+
                        //        " ("+getDateFromENIN(getENINFromSeconds(rpiEntry.startTimeStampUTC))+")");
                        matchingRpiEntry = rpiEntry;
                        break;
                    }
                }
            }
        }
        return matchingRpiEntry;
    }

    public SortedSet<Integer> getAvailableDaysSinceEpochLocalTZ() {
        return (SortedSet<Integer>) mapOfDailyCountsLocalTZ.keySet();
    }

    public boolean isEmpty() {
        return mapOfDailyCountsLocalTZ.isEmpty();
    }
}
