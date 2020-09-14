package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysImport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

import static org.tosl.coronawarncompanion.dkdownload.Unzip.getUnzippedBytesFromZipFileBytes;

public class DKDownloadUtils {

    private static final String TAG = "DKDownloadUtils";

    public static Single<List<DiagnosisKey>>
    getDKsForCountries(Context context, Date minDate, List<DKDownloadCountry> countries) {
        return Observable
                .concat(
                countries
                        .stream()
                        .map(dkDownloadCountry -> dkDownloadCountry.getDKBytes(context, minDate))
                        .collect(Collectors.toList()))
                .map(bytesCountryPair -> parseBytesToTeks(
                        context, bytesCountryPair.first, bytesCountryPair.second))
                .toList()
                .map(dkListList -> dkListList
                        .stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
    }

    public static List<DiagnosisKey>
    parseBytesToTeks(Context context, byte[] fileBytes, String countryCode) {
        byte[] exportDotBinBytes = {};
        try {
            exportDotBinBytes = getUnzippedBytesFromZipFileBytes(fileBytes, "export.bin");
        } catch (IOException e) {
            e.printStackTrace();
        }

        DiagnosisKeysImport diagnosisKeysImport = new DiagnosisKeysImport(context, exportDotBinBytes, countryCode);
        List<DiagnosisKey> dkList = diagnosisKeysImport.getDiagnosisKeys();
        if (dkList != null) {
            Log.d(TAG, "Number of keys in this file: " + dkList.size());
            return dkList;
        }
        return new ArrayList<>();
    }
}
