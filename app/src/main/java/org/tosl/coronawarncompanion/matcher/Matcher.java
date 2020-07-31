package org.tosl.coronawarncompanion.matcher;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;

import java.util.LinkedList;

import static org.tosl.coronawarncompanion.matcher.crypto.createListOfRpisForIntervalRange;
import static org.tosl.coronawarncompanion.matcher.crypto.deriveRpiKey;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.standardRollingPeriod;

public class Matcher {

    private static final String TAG = "Matcher";

    private CWCApplication app;
    private Context context = null;
    int timeZoneOffsetSeconds;

    public static class MatchEntry {
        public final DiagnosisKeysProtos.TemporaryExposureKey diagnosisKey;
        public final byte[] rpi;
        public final ContactRecordsProtos.ContactRecords contactRecords;
        public final int startTimestampLocalTZ;
        public final int endTimestampLocalTZ;

        public MatchEntry(DiagnosisKeysProtos.TemporaryExposureKey dk, byte[] rpiBytes,
                          ContactRecordsProtos.ContactRecords contactRecords,
                          int startTimestampLocalTZ, int endTimestampLocalTZ) {
            this.diagnosisKey = dk;
            this.rpi = rpiBytes;
            this.contactRecords = contactRecords;
            this.startTimestampLocalTZ = startTimestampLocalTZ;
            this.endTimestampLocalTZ = endTimestampLocalTZ;
        }
    }

    private final RpiList rpiList;
    private final LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList;

    public Matcher(RpiList rpis, LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeys, Context appContext) {
        rpiList = rpis;
        diagnosisKeysList = diagnosisKeys;
        context = appContext;
        app = (CWCApplication) context.getApplicationContext();
        timeZoneOffsetSeconds = app.getTimeZoneOffsetSeconds();
    }

    public LinkedList<MatchEntry> findMatches() {
        Log.d(TAG, "Started matching...");
        LinkedList<MatchEntry> matchEntries = new LinkedList<>();
        for (DiagnosisKeysProtos.TemporaryExposureKey dk : diagnosisKeysList) {
            int dkIntervalNumber = dk.getRollingStartIntervalNumber();
            LinkedList<crypto.RpiWithInterval> dkRpisWithIntervals = createListOfRpisForIntervalRange(deriveRpiKey(dk.getKeyData().toByteArray()),
                    dkIntervalNumber, dk.getRollingPeriod());
            for (crypto.RpiWithInterval dkRpiWithInterval : dkRpisWithIntervals) {
                RpiList.RpiEntry rpiEntry =
                        rpiList.searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(dkRpiWithInterval, getDaysSinceEpochFromENIN(dkIntervalNumber));
                if (rpiEntry != null) {
                    Log.i(TAG, "Match found!");
                    matchEntries.add(new MatchEntry(dk, dkRpiWithInterval.rpiBytes, rpiEntry.contactRecords,
                            rpiEntry.startTimeStampLocalTZ, rpiEntry.endTimeStampLocalTZ));
                }
            }
        }
        Log.d(TAG, "Finished matching...");
        return matchEntries;
    }
}
