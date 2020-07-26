package org.tosl.coronawarncompanion.diagnosiskeys;

import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.tosl.coronawarncompanion.BuildConfig;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class DiagnosisKeysImport {

    private static final String TAG = "DiagnosisKeys";

    private DiagnosisKeysProtos.TemporaryExposureKeyExport dkImport = null;

    public DiagnosisKeysImport(byte[] exportDotBin) {
        String header = "EK Export v1    ";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        if (BuildConfig.DEBUG && headerBytes.length != 16) {
            throw new AssertionError("Invalid header string in source code!");
        }
        if (Arrays.equals(Arrays.copyOf(exportDotBin, 16), headerBytes)) {
            try {
                dkImport = DiagnosisKeysProtos.TemporaryExposureKeyExport.parseFrom(Arrays.copyOfRange(exportDotBin, 16, exportDotBin.length));
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        } else {
            //TODO
            Log.e(TAG, "Invalid Header: export.bin does not start with 'EK Export v1'");
        }
    }

    public List<DiagnosisKeysProtos.TemporaryExposureKey> getDiagnosisKeys() {
        return dkImport.getKeysList();
    }
}
