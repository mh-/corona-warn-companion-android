package org.tosl.coronawarncompanion.gmsreadout;

import java.util.*;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, DayEntry> map;  // daysSinceEpoch, DayEntry

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

    public RpiList() {
        map = new TreeMap<>();
    }

    private Integer getIntegerFromFirstBytesOfByteArray(byte[] rpi) {
        return (rpi[0] | (rpi[1]<<8) | (rpi[2]<<16) | (rpi[3]<<24));
    }

    public void addEntry(Integer daysSinceEpoch, RpiEntry entry) {
        DayEntry dayEntry;
        if (!map.containsKey(daysSinceEpoch)) {  // day not yet in list, create new entry
            dayEntry = new DayEntry();
            map.put(daysSinceEpoch, dayEntry);
            //Log.d(TAG, "Added date: " + date);
        }
        dayEntry = map.get(daysSinceEpoch);
        if (dayEntry != null) {
            dayEntry.rpi32Bits.add(getIntegerFromFirstBytesOfByteArray(entry.rpi));
            dayEntry.rpiEntries.add(entry);
        }
    }

    public LinkedList<RpiEntry> getRpiEntriesForDaysSinceEpoch(Integer daysSinceEpoch) {
        DayEntry dayEntry = map.get(daysSinceEpoch);
        if (dayEntry != null) {
            return dayEntry.rpiEntries;
        } else {
            return null;
        }
    }

    public ContactRecordsProtos.ContactRecords getFirstContactRecordsForDaysSinceEpochAndRpi(Integer daysSinceEpoch, byte[] searchRpi) {
        ContactRecordsProtos.ContactRecords contactRecords = null;
        DayEntry dayEntry = map.get(daysSinceEpoch);
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
        return (SortedSet<Integer>) map.keySet();
    }
}
