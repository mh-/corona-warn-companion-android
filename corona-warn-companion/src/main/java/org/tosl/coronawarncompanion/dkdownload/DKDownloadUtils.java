package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.tosl.coronawarncompanion.R;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysImport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

import static org.tosl.coronawarncompanion.dkdownload.Unzip.getUnzippedBytesFromZipFileBytes;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromDate;
import static org.tosl.coronawarncompanion.tools.Utils.standardRollingPeriod;

public class DKDownloadUtils {

    private static final String TAG = "DKDownloadUtils";

    static private int errorCount = 0;

    public static <T> Maybe<T> wrapRetrofit(Context context, Maybe<T> request) {
        return request
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> {
                    errorCount++;
                    error.printStackTrace();
                    Toast toast = Toast.makeText(context,
                            context.getResources().getString(R.string.toast_download_error, error.getMessage()), Toast.LENGTH_LONG);
                    toast.show();
                })
                .onErrorComplete();
    }

    public static Single<Pair<List<DiagnosisKey>,List<DownloadFileInfo>>>
    getDKsAndFileInfoListForCountries(Context context, OkHttpClient okHttpClient, Date minDate, List<DKDownloadCountry> countries) {
        errorCount = 0;
        return Observable
                .concat(countries
                        .stream()
                        .map(dkDownloadCountry -> dkDownloadCountry
                                .getDKBytesAndFileInfo(context, okHttpClient, minDate)
                                .map(bytesFileInfoPair -> new Pair<>(bytesFileInfoPair, dkDownloadCountry.getCountryCode(context))))
                        .collect(Collectors.toList()))
                .map(bytesFileInfoCountryPair -> new Pair<>(parseBytesToTeks(
                        context, bytesFileInfoCountryPair.first.first, bytesFileInfoCountryPair.second),
                        bytesFileInfoCountryPair.first.second))
                .toList()
                .map(listOfDkListsAndFileInfo -> new Pair<>(
                        listOfDkListsAndFileInfo
                                .stream()
                                .map(pair -> pair.first)
                                .flatMap(List::stream)
                                .filter(dk -> dk.dk.getRollingStartIntervalNumber()
                                        >= getENINFromDate(minDate)-standardRollingPeriod) // -1 day because of the 2h window
                                .collect(Collectors.toList()),
                        listOfDkListsAndFileInfo
                                .stream()
                                .map(pair -> pair.second)
                                .collect(Collectors.toList())));
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

    public static int getErrorCount() {
        return errorCount;
    }
}
