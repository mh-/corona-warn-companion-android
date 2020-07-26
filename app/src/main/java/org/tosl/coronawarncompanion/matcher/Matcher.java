package org.tosl.coronawarncompanion.matcher;

import android.util.Log;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;

import java.util.LinkedList;

public class Matcher {

    private static final String TAG = "Matcher";

    private final RpiList rpiList;
    private final LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList;

    public Matcher(RpiList rpis, LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeys) {
        rpiList = rpis;
        diagnosisKeysList = diagnosisKeys;
    }

    public void findMatches() {
        Log.d(TAG, "Started matching...");
    }
}
