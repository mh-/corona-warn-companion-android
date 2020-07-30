package org.tosl.coronawarncompanion.gmsreadout;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.CWCApplication;

import java.util.*;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, DayEntry> dayEntryMap;  // daysSinceEpoch, DayEntry
    private final TreeMap<Integer, Integer> localTimeZoneDailyCountMap;  // daysSinceEpoch, numberOfEntries

    private CWCApplication app;
    private Context context = null;
    int timeZoneOffsetSeconds;

    public static class DayEntry {
        public TreeSet<Integer> rpi32Bits = new TreeSet<>();         // Some of the RPI bytes, used for fast search
        public LinkedList<RpiEntry> rpiEntries = new LinkedList<>(); // RpiEntries for other purposes
    }

    public static class RpiEntry {
        public final byte[] rpi;  // RPI bytes
        public final ContactRecordsProtos.ContactRecords contactRecords;  // list of all ScanRecords

        public RpiEntry(byte[] rpiBytes, ContactRecordsProtos.ContactRecords scanRecords) {
            rpi = rpiBytes;
            contactRecords = scanRecords;
        }
    }

    public RpiList(Context context) {
        dayEntryMap = new TreeMap<>();
        localTimeZoneDailyCountMap = new TreeMap<>();
        this.context = context;
        app = (CWCApplication) context.getApplicationContext();
        timeZoneOffsetSeconds = app.getTimeZoneOffsetSeconds();
    }

    private Integer getIntegerFromFirstBytesOfByteArray(byte[] rpi) {
        return (rpi[0] | (rpi[1]<<8) | (rpi[2]<<16) | (rpi[3]<<24));
    }

    public void addEntry(Integer daysSinceEpoch, RpiEntry entry) {
        DayEntry dayEntry;
        if (!dayEntryMap.containsKey(daysSinceEpoch)) {  // day not yet in list, create new entry
            dayEntry = new DayEntry();
            dayEntryMap.put(daysSinceEpoch, dayEntry);
            //Log.d(TAG, "Added date: " + date);
        }
        dayEntry = dayEntryMap.get(daysSinceEpoch);
        if (dayEntry != null) {
            dayEntry.rpi32Bits.add(getIntegerFromFirstBytesOfByteArray(entry.rpi));
            dayEntry.rpiEntries.add(entry);
            dayEntryMap.put(daysSinceEpoch, dayEntry);
        }

        if (entry.contactRecords.getRecordCount() > 0) {  // this check should be required only for DEMO mode
            int daySinceEpochLocalTimeZone = (entry.contactRecords.getRecord(0).getTimestamp()
                    + timeZoneOffsetSeconds) / (24 * 3600);
            if (!localTimeZoneDailyCountMap.containsKey(daySinceEpochLocalTimeZone)) {  // day not yet in list, create new entry
                localTimeZoneDailyCountMap.put(daySinceEpochLocalTimeZone, 0);
            }
            Integer dailyCount = localTimeZoneDailyCountMap.get(daySinceEpochLocalTimeZone);
            if (dailyCount != null) {
                dailyCount++;
                localTimeZoneDailyCountMap.put(daySinceEpochLocalTimeZone, dailyCount);
            }
        }
    }

    public LinkedList<RpiEntry> getRpiEntriesForDaysSinceEpoch(Integer daysSinceEpoch) {
        DayEntry dayEntry = dayEntryMap.get(daysSinceEpoch);
        if (dayEntry != null) {
            return dayEntry.rpiEntries;
        } else {
            return null;
        }
    }

    public Integer getRpiCountForDaysSinceEpochInLocalTime(Integer daysSinceEpoch) {
        return localTimeZoneDailyCountMap.get(daysSinceEpoch);
    }

    public ContactRecordsProtos.ContactRecords getFirstContactRecordsForDaysSinceEpochAndRpi(Integer daysSinceEpoch, byte[] searchRpi) {
        ContactRecordsProtos.ContactRecords contactRecords = null;
        DayEntry dayEntry = dayEntryMap.get(daysSinceEpoch);
        if (dayEntry != null) {
            if (dayEntry.rpi32Bits.contains(getIntegerFromFirstBytesOfByteArray(searchRpi))) {
                //Log.d(TAG, "Potential match found, based on 32 bits comparison!");
                for (RpiEntry rpiEntry : dayEntry.rpiEntries) {
                    if (Arrays.equals(rpiEntry.rpi, searchRpi)) {
                        //Log.d(TAG, "Match confirmed!");
                        contactRecords = rpiEntry.contactRecords;
                        break;
                    }
                }
                if (contactRecords == null) {
                    //Log.d(TAG, "Match based on 32 bits was not confirmed based on 128 bits.");
                }
            } else {
                //Log.d(TAG, "Match not found during this attempt.");
            }
        }
        return contactRecords;
    }

    public SortedSet<Integer> getAvailableDaysSinceEpoch() {
        return (SortedSet<Integer>) dayEntryMap.keySet();
    }

    public SortedSet<Integer> getAvailableDaysSinceEpochInLocalTimezone() {
        return (SortedSet<Integer>) localTimeZoneDailyCountMap.keySet();
    }

}
