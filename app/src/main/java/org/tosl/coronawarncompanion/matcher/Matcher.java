package org.tosl.coronawarncompanion.matcher;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.core.util.Consumer;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.matcher.crypto.createListOfRpisForIntervalRange;
import static org.tosl.coronawarncompanion.matcher.crypto.deriveRpiKey;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;

public class Matcher {

    private static final String TAG = "Matcher";

    private final CWCApplication app;
    private final Context context;
    private final MatchEntryContent matchEntryContent;

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

    public Matcher(RpiList rpis, LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeys, Context appContext, MatchEntryContent matchEntryContent) {
        this.rpiList = rpis;
        this.diagnosisKeysList = diagnosisKeys;
        this.context = appContext;
        this.matchEntryContent = matchEntryContent;
        this.app = (CWCApplication) context.getApplicationContext();
    }

    public void findMatches(Consumer<Pair<Integer, Integer>> progressCallback) {
        Log.d(TAG, "Started matching...");
        int diagnosisKeysListLength = diagnosisKeysList.size();
        int currentDiagnosisKey = 0;
        int lastProgress = 0;
        int currentProgress;
        int numMatches = 0;
        for (DiagnosisKeysProtos.TemporaryExposureKey dk : diagnosisKeysList) {
            currentDiagnosisKey += 1;
            currentProgress = (int) (100f * currentDiagnosisKey / diagnosisKeysListLength);
            if (currentProgress != lastProgress) {
                lastProgress = currentProgress;
                if (progressCallback != null) {
                    progressCallback.accept(new Pair<>(currentProgress, numMatches));
                }
            }
            int dkIntervalNumber = dk.getRollingStartIntervalNumber();
            LinkedList<crypto.RpiWithInterval> dkRpisWithIntervals = createListOfRpisForIntervalRange(deriveRpiKey(dk.getKeyData().toByteArray()),
                    dkIntervalNumber, dk.getRollingPeriod());
            for (crypto.RpiWithInterval dkRpiWithInterval : dkRpisWithIntervals) {
                RpiList.RpiEntry rpiEntry =
                        rpiList.searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(dkRpiWithInterval, getDaysSinceEpochFromENIN(dkIntervalNumber));
                if (rpiEntry != null) {
                    Log.i(TAG, "Match found!");
                    Calendar startDateTimeLocalTZ = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    // UTC because we don't want Calendar to do additional time zone compensation
                    startDateTimeLocalTZ.setTimeInMillis(getMillisFromSeconds(rpiEntry.startTimeStampLocalTZ));
                    int startHourLocalTZ = startDateTimeLocalTZ.get(Calendar.HOUR_OF_DAY);
                    this.matchEntryContent.matchEntries.add(new MatchEntry(dk, dkRpiWithInterval.rpiBytes, rpiEntry.contactRecords,
                            rpiEntry.startTimeStampLocalTZ, rpiEntry.endTimeStampLocalTZ),
                            getDaysFromSeconds(rpiEntry.startTimeStampLocalTZ),
                            startHourLocalTZ);
                    numMatches++;
                }
            }
        }
        Log.d(TAG, "Finished matching...");
    }
}
