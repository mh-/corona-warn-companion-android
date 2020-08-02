package org.tosl.coronawarncompanion.matchentries;

import org.tosl.coronawarncompanion.matcher.Matcher;

import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

public class MatchEntryContent {  // organized in a TreeMap of days, which contains Arrays of hours, which contain ArrayLists of MatchEntries.

    @SuppressWarnings("InnerClassMayBeStatic")
    public class HourlyMatchEntries {
        private final ArrayList<Matcher.MatchEntry> list = new ArrayList<>();
        private int hourlyCount = 0;

        public int getHourlyCount() {
            return hourlyCount;
        }

        public ArrayList<Matcher.MatchEntry> getList() {
            return list;
        }

        public void add(Matcher.MatchEntry entry) {
            list.add(entry);
            hourlyCount++;
        }
    }

    public class DailyMatchEntries {
        private final HourlyMatchEntries[] array = new HourlyMatchEntries[24];
        private int dailyCount = 0;

        public DailyMatchEntries(){
            for (int i=0; i<24; i++) {
                array[i] = new HourlyMatchEntries();
            }
        }

        public int getDailyCount() {
            return dailyCount;
        }

        public HourlyMatchEntries getHourlyMatchEntries(int hourLocalTZ) {
            return array[hourLocalTZ];
        }

        public void add(Matcher.MatchEntry entry, Integer hourLocalTZ) {
            array[hourLocalTZ].add(entry);
            dailyCount++;
        }
    }

    public class MatchEntries {
        private final TreeMap<Integer, DailyMatchEntries> map = new TreeMap<>();  // <DaysSinceEpoch, DailyMatchEntries>
        private int totalCount = 0;

        public int getTotalCount() {
            return totalCount;
        }

        public DailyMatchEntries getDailyMatchEntries(Integer daysSinceEpoch) {
            return map.get(daysSinceEpoch);
        }

        public void add(Matcher.MatchEntry entry, Integer daysSinceEpochLocalTZ, Integer hourLocalTZ) {
            if (!map.containsKey(daysSinceEpochLocalTZ)) {
                map.put(daysSinceEpochLocalTZ, new DailyMatchEntries());
            }
            Objects.requireNonNull(map.get(daysSinceEpochLocalTZ)).add(entry, hourLocalTZ);
            totalCount++;
        }
    }

    public final MatchEntries matchEntries = new MatchEntries();

}