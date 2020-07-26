package org.tosl.coronawarncompanion.gmsreadout;

import java.util.*;

public class RpiList {
    private static final String TAG = "RpiList";

    private final TreeMap<Integer, LinkedList<RpiEntry>> map;

    public static class RpiEntry {
        public final byte[] rpi = new byte[16];
        public byte[] scanData = null;
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

    public SortedSet<Integer> getDaysSinceEpoch() {

        return (SortedSet<Integer>) map.keySet();
    }
}
