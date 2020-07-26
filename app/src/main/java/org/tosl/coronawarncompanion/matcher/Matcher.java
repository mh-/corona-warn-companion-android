package org.tosl.coronawarncompanion.matcher;

import android.util.Log;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;

import java.util.LinkedList;

import static org.tosl.coronawarncompanion.matcher.crypto.createListOfRpisForIntervalRange;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.standardRollingPeriod;

public class Matcher {

    private static final String TAG = "Matcher";

    public static class MatchEntry {
        public final DiagnosisKeysProtos.TemporaryExposureKey diagnosisKey;
        public final byte[] rpi;
        public final byte[] scanData;
        public MatchEntry(DiagnosisKeysProtos.TemporaryExposureKey dk, byte[] rpiBytes, byte[] scanDataBytes) {
            diagnosisKey = dk;
            rpi = rpiBytes;
            scanData = scanDataBytes;
        }
    }

    private final RpiList rpiList;
    private final LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList;

    public Matcher(RpiList rpis, LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeys) {
        rpiList = rpis;
        diagnosisKeysList = diagnosisKeys;
    }

    public LinkedList<MatchEntry> findMatches() {
        Log.d(TAG, "Started matching...");
        LinkedList<MatchEntry> matchEntries = new LinkedList<>();
        for (DiagnosisKeysProtos.TemporaryExposureKey dk : diagnosisKeysList) {
            int dkIntervalNumber = dk.getRollingStartIntervalNumber();
            LinkedList<byte[]> dkRpis = createListOfRpisForIntervalRange(dk.getKeyData().toByteArray(),
                    dkIntervalNumber, dk.getRollingPeriod());
            for (int intervalNumber = dkIntervalNumber - standardRollingPeriod;
                 intervalNumber <= dkIntervalNumber + standardRollingPeriod;
                 intervalNumber += standardRollingPeriod) {
                for (byte[] dkRpi : dkRpis) {
                    byte[] scanData = rpiList.getFirstScanDataForDaysSinceEpochAndRpi(getDaysSinceEpochFromENIN(intervalNumber), dkRpi);
                    if (scanData != null) {
                        Log.i(TAG, "Match found!");
                        matchEntries.add(new MatchEntry(dk, dkRpi, scanData));
                    }
                }
            }
        }
        Log.d(TAG, "Finished matching...");
        return matchEntries;
    }
}
