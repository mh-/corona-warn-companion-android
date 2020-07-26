package org.tosl.coronawarncompanion.gmsreadout;

import java.util.*;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, LinkedList<RpiEntry>> map;  // daysSinceEpoch, listOfRpiEntries

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
        LinkedList<RpiEntry> rpiEntries;
        if (!map.containsKey(daysSinceEpoch)) {
            rpiEntries = new LinkedList<>();
            map.put(daysSinceEpoch, rpiEntries);
            //Log.d(TAG, "Added date: " + date);
        }
        rpiEntries = map.get(daysSinceEpoch);
        if (rpiEntries != null) {
            rpiEntries.add(entry);
        }
    }

    public LinkedList<RpiEntry> getRpiEntriesForDaysSinceEpoch(Integer daysSinceEpoch) {
        return map.get(daysSinceEpoch);
    }

    public byte[] getFirstScanDataForDaysSinceEpochAndRpi(Integer daysSinceEpoch, byte[] searchRpi) {
        //TODO: use another data structure for search (not linked list) -> e.g. add a tree.
        byte[] scanData = null;
        LinkedList<RpiEntry> rpiEntries = map.get(daysSinceEpoch);
        if (rpiEntries != null) {
            for (RpiEntry rpiEntry : rpiEntries) {
                if (Arrays.equals(rpiEntry.rpi, searchRpi)) {
                    scanData = rpiEntry.scanData;
                    break;
                }
            }
        }
        return scanData;
    }

    public SortedSet<Integer> getAvailableDaysSinceEpoch() {
        return (SortedSet<Integer>) map.keySet();
    }
}
