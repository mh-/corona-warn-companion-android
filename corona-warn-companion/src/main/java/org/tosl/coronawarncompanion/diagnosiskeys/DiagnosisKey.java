package org.tosl.coronawarncompanion.diagnosiskeys;

public class DiagnosisKey {
    public DiagnosisKeysProtos.TemporaryExposureKey dk;
    public String countryCode;

    public DiagnosisKey(DiagnosisKeysProtos.TemporaryExposureKey dk, String countryCode) {
        this.dk = dk;
        this.countryCode = countryCode;
    }
}
