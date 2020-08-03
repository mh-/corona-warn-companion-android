package org.tosl.coronawarncompanion.matchentries;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;

public class MatchEntryContent {
    // organized in a TreeMap indexed by days,
    // which contains HashMaps indexed by Diagnosis Keys,
    // which contain ArrayLists of MatchEntries.

    public class MatchEntries {
        private final TreeMap<Integer, DailyMatchEntries> map = new TreeMap<>();  // <DaysSinceEpoch, DailyMatchEntries>
        private int totalRpiCount = 0;
        private int totalMatchingDkCount = 0;

        public int getTotalRpiCount() {
            return totalRpiCount;
        }

        public int getTotalMatchingDkCount() {
            return totalMatchingDkCount;
        }

        public DailyMatchEntries getDailyMatchEntries(Integer daysSinceEpoch) {
            return map.get(daysSinceEpoch);
        }

        public void add(Matcher.MatchEntry entry, DiagnosisKeysProtos.TemporaryExposureKey dk,
                        Integer daysSinceEpochLocalTZ, Integer hourLocalTZ) {
            if (!map.containsKey(daysSinceEpochLocalTZ)) {
                map.put(daysSinceEpochLocalTZ, new DailyMatchEntries());
            }
            DailyMatchEntries dailyMatchEntries = map.get(daysSinceEpochLocalTZ);
            if (dailyMatchEntries != null) {
                int previousMatchingDkCount = dailyMatchEntries.getDailyMatchingDkCount();
                dailyMatchEntries.add(entry, dk, hourLocalTZ);
                totalRpiCount++;
                totalMatchingDkCount += (dailyMatchEntries.getDailyMatchingDkCount() - previousMatchingDkCount);
            }
        }
    }

    public class DailyMatchEntries {
        private final HashMap<DiagnosisKeysProtos.TemporaryExposureKey, GroupedByDkMatchEntries> map =
                new HashMap<>();
        private int dailyRpiCount = 0;
        private int dailyMatchingDkCount = 0;

        public int getDailyRpiCount() {
            return dailyRpiCount;
        }

        public int getDailyMatchingDkCount() {
            return dailyMatchingDkCount;
        }

        public HashMap<DiagnosisKeysProtos.TemporaryExposureKey, GroupedByDkMatchEntries> getMap() {
            return map;
        }

        public void add(Matcher.MatchEntry entry, DiagnosisKeysProtos.TemporaryExposureKey dk, Integer hourLocalTZ) {
            if (!map.containsKey(dk)) {
                map.put(dk, new GroupedByDkMatchEntries());
                dailyMatchingDkCount++;
            }
            Objects.requireNonNull(map.get(dk)).add(entry, hourLocalTZ);
            dailyRpiCount++;
        }
    }

    public class GroupedByDkMatchEntries {
        private final ArrayList<Matcher.MatchEntry> list = new ArrayList<>();
        private int groupedByDkRpiCount = 0;

        public int getGroupedByDkRpiCount() {
            return groupedByDkRpiCount;
        }

        public ArrayList<Matcher.MatchEntry> getList() {
            return list;
        }

        public void add(Matcher.MatchEntry entry, Integer hourLocalTZ) {
            list.add(entry);
            groupedByDkRpiCount++;
        }
    }

    public final MatchEntries matchEntries = new MatchEntries();
}