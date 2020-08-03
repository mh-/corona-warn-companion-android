package org.tosl.coronawarncompanion.matcher;

import android.util.Log;
import android.util.Pair;

import androidx.core.util.Consumer;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.matcher.Crypto.createListOfRpisForIntervalRange;
import static org.tosl.coronawarncompanion.matcher.Crypto.decryptAem;
import static org.tosl.coronawarncompanion.matcher.Crypto.deriveAemKey;
import static org.tosl.coronawarncompanion.matcher.Crypto.deriveRpiKey;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;

public class Matcher {

    private static final String TAG = "Matcher";

    private final MatchEntryContent matchEntryContent;

    public static class MatchEntry {
        public final DiagnosisKeysProtos.TemporaryExposureKey diagnosisKey;
        public final byte[] rpi;
        public final ContactRecordsProtos.ContactRecords contactRecords;
        public final int startTimestampLocalTZ;
        public final int endTimestampLocalTZ;
        public final byte[] aemXorBytes;

        public MatchEntry(DiagnosisKeysProtos.TemporaryExposureKey dk, byte[] rpiBytes,
                          ContactRecordsProtos.ContactRecords contactRecords,
                          int startTimestampLocalTZ, int endTimestampLocalTZ, byte[] aemXorBytes) {
            this.diagnosisKey = dk;
            this.rpi = rpiBytes;
            this.contactRecords = contactRecords;
            this.startTimestampLocalTZ = startTimestampLocalTZ;
            this.endTimestampLocalTZ = endTimestampLocalTZ;
            this.aemXorBytes = aemXorBytes;
        }
    }

    private final RpiList rpiList;
    private final LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList;

    public Matcher(RpiList rpis, LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeys, MatchEntryContent matchEntryContent) {
        this.rpiList = rpis;
        this.diagnosisKeysList = diagnosisKeys;
        this.matchEntryContent = matchEntryContent;
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
            LinkedList<Crypto.RpiWithInterval> dkRpisWithIntervals = createListOfRpisForIntervalRange(deriveRpiKey(dk.getKeyData().toByteArray()),
                    dkIntervalNumber, dk.getRollingPeriod());
            for (Crypto.RpiWithInterval dkRpiWithInterval : dkRpisWithIntervals) {
                RpiList.RpiEntry rpiEntry =
                        rpiList.searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(dkRpiWithInterval, getDaysSinceEpochFromENIN(dkIntervalNumber));
                if (rpiEntry != null) {
                    Log.i(TAG, "Match found!");
                    Calendar startDateTimeLocalTZ = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    // UTC because we don't want Calendar to do additional time zone compensation
                    startDateTimeLocalTZ.setTimeInMillis(getMillisFromSeconds(rpiEntry.startTimeStampLocalTZ));
                    int startHourLocalTZ = startDateTimeLocalTZ.get(Calendar.HOUR_OF_DAY);

                    byte[] aemKey = deriveAemKey(dk.getKeyData().toByteArray());
                    byte[] zeroAem = {0x00, 0x00, 0x00, 0x00};
                    byte[] aemXorBytes = decryptAem(aemKey, zeroAem, rpiEntry.rpi);

                    this.matchEntryContent.matchEntries.add(new MatchEntry(dk, dkRpiWithInterval.rpiBytes, rpiEntry.contactRecords,
                            rpiEntry.startTimeStampLocalTZ, rpiEntry.endTimeStampLocalTZ, aemXorBytes),
                            dk,
                            getDaysFromSeconds(rpiEntry.startTimeStampLocalTZ),
                            startHourLocalTZ);
                    numMatches = this.matchEntryContent.matchEntries.getTotalMatchingDkCount();
                }
            }
        }
        Log.d(TAG, "Finished matching...");
    }
}
