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
        public final int daysSinceEpochInLocalTimeZone;

        public MatchEntry(DiagnosisKeysProtos.TemporaryExposureKey dk, byte[] rpiBytes,
                          ContactRecordsProtos.ContactRecords contactRecords,
                          int daysSinceEpochInLocalTimeZone) {
            this.diagnosisKey = dk;
            this.rpi = rpiBytes;
            this.contactRecords = contactRecords;
            this.daysSinceEpochInLocalTimeZone = daysSinceEpochInLocalTimeZone;
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

    /*
    private static final byte[] testKey = {(byte) 0xff, (byte) 0xc1, (byte) 0xb8, (byte) 0x2c,
            (byte) 0x49, (byte) 0xed, (byte) 0xc0, (byte) 0x65, (byte) 0x4f, (byte) 0xd1,
            (byte) 0xfc, (byte) 0x27, (byte) 0x8b, (byte) 0xfe, (byte) 0x69, (byte) 0xe5};
    */

    public LinkedList<MatchEntry> findMatches() {
        Log.d(TAG, "Started matching...");
        LinkedList<MatchEntry> matchEntries = new LinkedList<>();
        for (DiagnosisKeysProtos.TemporaryExposureKey dk : diagnosisKeysList) {

            /*
            boolean testKeyfound = false;
            if (Arrays.equals(dk.getKeyData().toByteArray(), testKey)) {
                Log.d(TAG, "testKey found in downloaded DKs! enin: "+dk.getRollingStartIntervalNumber()+", " +
                        "rolling period: "+dk.getRollingPeriod());
                testKeyfound = true;
            }
            */

            int dkIntervalNumber = dk.getRollingStartIntervalNumber();
            LinkedList<byte[]> dkRpis = createListOfRpisForIntervalRange(deriveRpiKey(dk.getKeyData().toByteArray()),
                    dkIntervalNumber, dk.getRollingPeriod());
            /*
            if (testKeyfound) {
                Log.d(TAG, "Number of RPIs: "+dkRpis.size());
                for (byte[] dkRpi : dkRpis) {
                    Log.d(TAG, "RPI: "+(byteArrayToHex(dkRpi)));
                }
            }
            */
            for (int intervalNumber = dkIntervalNumber - standardRollingPeriod;
                 intervalNumber <= dkIntervalNumber + standardRollingPeriod;
                 intervalNumber += standardRollingPeriod) {
                for (byte[] dkRpi : dkRpis) {
                    ContactRecordsProtos.ContactRecords contactRecords =
                            rpiList.getFirstContactRecordsForDaysSinceEpochAndRpi(getDaysSinceEpochFromENIN(intervalNumber),
                            dkRpi
                    );
                    if (contactRecords != null) {
                        Log.i(TAG, "Match found!");
                        int daysSinceEpochInLocalTimezone = (contactRecords.getRecord(0).getTimestamp()
                                + timeZoneOffsetSeconds) / (24 * 3600);
                        matchEntries.add(new MatchEntry(dk, dkRpi, contactRecords,daysSinceEpochInLocalTimezone));
                    }
                }
            }
        }
        Log.d(TAG, "Finished matching...");
        return matchEntries;
    }
}
