package org.tosl.coronawarncompanion.matchentries;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;

public class DkAndMatchEntries implements Comparable<DkAndMatchEntries> {
    public final DiagnosisKey dk;
    public final MatchEntryContent.GroupedByDkMatchEntries matchEntries;

    public DkAndMatchEntries(DiagnosisKey dk, MatchEntryContent.GroupedByDkMatchEntries matchEntries) {
        this.dk = dk;
        this.matchEntries = matchEntries;
    }

    @Override
    public int compareTo(DkAndMatchEntries o) {
        return this.matchEntries.getMinStartTimeStampUTC().compareTo(o.matchEntries.getMinStartTimeStampUTC());
    }
}
