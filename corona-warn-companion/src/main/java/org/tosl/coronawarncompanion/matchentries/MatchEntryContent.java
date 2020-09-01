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

package org.tosl.coronawarncompanion.matchentries;

//import android.util.Log;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;

public class MatchEntryContent {
    // organized in a TreeMap indexed by days,
    // which contains HashMaps indexed by Diagnosis Keys,
    // which contain ArrayLists of MatchEntries.

    //private static final String TAG = "MatchEntryContent";

    public final MatchEntries matchEntries = new MatchEntries();

    public static class MatchEntries {
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

        public void add(Matcher.MatchEntry entry, DiagnosisKey dk,
                        Integer daysSinceEpochLocalTZ) {
            if (!map.containsKey(daysSinceEpochLocalTZ)) {
                map.put(daysSinceEpochLocalTZ, new DailyMatchEntries());
            }
            DailyMatchEntries dailyMatchEntries = map.get(daysSinceEpochLocalTZ);
            if (dailyMatchEntries != null) {
                int previousMatchingDkCount = dailyMatchEntries.getDailyMatchingDkCount();
                dailyMatchEntries.add(entry, dk);
                totalRpiCount++;
                totalMatchingDkCount += (dailyMatchEntries.getDailyMatchingDkCount() - previousMatchingDkCount);
                // Log.d(TAG, "Added entry for day: " + daysSinceEpochLocalTZ +
                //        ", startTimestampUTC: " + entry.startTimestampUTC +
                //        ", previousMatchingDkCount: " + previousMatchingDkCount +
                //        ", dailyMatchEntries.getDailyMatchingDkCount(): " + dailyMatchEntries.getDailyMatchingDkCount() +
                //        ", totalMatchingDkCount: " + totalMatchingDkCount);
            }
        }
    }

    public static class DailyMatchEntries {
        private final HashMap<DiagnosisKey, GroupedByDkMatchEntries> map =
                new HashMap<>();
        private int dailyRpiCount = 0;
        private int dailyMatchingDkCount = 0;

        public int getDailyRpiCount() {
            return dailyRpiCount;
        }

        public int getDailyMatchingDkCount() {
            return dailyMatchingDkCount;
        }

        public HashMap<DiagnosisKey, GroupedByDkMatchEntries> getMap() {
            return map;
        }

        public void add(Matcher.MatchEntry entry, DiagnosisKey dk) {
            if (!map.containsKey(dk)) {
                map.put(dk, new GroupedByDkMatchEntries());
                dailyMatchingDkCount++;
            }
            Objects.requireNonNull(map.get(dk)).add(entry);
            dailyRpiCount++;
        }
    }

    public static class GroupedByDkMatchEntries {
        private final ArrayList<Matcher.MatchEntry> list = new ArrayList<>();
        private int groupedByDkRpiCount = 0;
        private Integer minStartTimeStampUTC = Integer.MAX_VALUE;

        public int getGroupedByDkRpiCount() {
            return groupedByDkRpiCount;
        }

        public Integer getMinStartTimeStampUTC() {
            return minStartTimeStampUTC;
        }

        public ArrayList<Matcher.MatchEntry> getList() {
            return list;
        }

        public void add(Matcher.MatchEntry entry) {
            list.add(entry);
            groupedByDkRpiCount++;
            if (minStartTimeStampUTC > entry.startTimestampUTC) {
                minStartTimeStampUTC = entry.startTimestampUTC;
            }
        }
    }
}