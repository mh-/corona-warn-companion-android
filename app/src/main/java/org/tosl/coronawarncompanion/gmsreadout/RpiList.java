package org.tosl.coronawarncompanion.gmsreadout;

import java.util.*;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, DayEntry> map;  // daysSinceEpoch, DayEntry

    public static class DayEntry {
        public HashSet<byte[]> rpis = new HashSet<>();               // RPI bytes for fast search
        public LinkedList<RpiEntry> rpiEntries = new LinkedList<>(); // RpiEntries for other purposes
    }

    public static class RpiEntry {
        public final byte[] rpi;        // RPI bytes
        public final byte[] scanData;   // ProtoBuf-encoded list of all scans

        public RpiEntry(byte[] rpiBytes, byte[] scanDataBytes) {
            rpi = rpiBytes;
            scanData = scanDataBytes;
        }
    }

    public RpiList() {
        map = new TreeMap<>();
    }

    public void addEntry(Integer daysSinceEpoch, RpiEntry entry) {
        DayEntry dayEntry;
        if (!map.containsKey(daysSinceEpoch)) {
            dayEntry = new DayEntry();
            map.put(daysSinceEpoch, dayEntry);
            //Log.d(TAG, "Added date: " + date);
        }
        dayEntry = map.get(daysSinceEpoch);
        if (dayEntry != null) {
            dayEntry.rpis.add(entry.rpi);
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

    public byte[] getFirstScanDataForDaysSinceEpochAndRpi(Integer daysSinceEpoch, byte[] searchRpi) {
        byte[] scanData = null;
        DayEntry dayEntry = map.get(daysSinceEpoch);
        if (dayEntry != null) {
            if (dayEntry.rpis.contains(searchRpi)) {
                for (RpiEntry rpiEntry : dayEntry.rpiEntries) {
                    if (Arrays.equals(rpiEntry.rpi, searchRpi)) {
                        scanData = rpiEntry.scanData;
                        break;
                    }
                }
            }
        }
        return scanData;
    }

    public SortedSet<Integer> getAvailableDaysSinceEpoch() {
        return (SortedSet<Integer>) map.keySet();
    }
}
