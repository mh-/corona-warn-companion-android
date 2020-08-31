package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.android.volley.Request;
import com.android.volley.RequestQueue;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysImport;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;

import static org.tosl.coronawarncompanion.dkdownload.Unzip.getUnzippedBytesFromZipFileBytes;

public class DKDownloadUtils {

    private static final String TAG = "DKDownloadUtils";

    public static Single<List<DiagnosisKey>>
    getDKsForCountries(Context context, RequestQueue queue, Date minDate, List<DKDownloadCountry> countries) {
        List<Single<List<Pair<URL, String>>>> singleList = new ArrayList<>();
        for (DKDownloadCountry dkDownloadCountry : countries) {
            Single<List<Pair<URL, String>>> dkDownloadCountryUrls = dkDownloadCountry.getUrls(context, queue, minDate);
            singleList.add(dkDownloadCountryUrls);
        }
        return Single.zip(singleList, results -> {
            List<Pair<URL, String>> urlList = new ArrayList<>();
            for (Object result : results) {
                //noinspection unchecked
                urlList.addAll((List<Pair<URL, String>>) result);
            }
            return urlList;
        }).flatMap(urlsWithCountryCode -> processUrlList(context, queue, urlsWithCountryCode));
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

    private static Single<List<DiagnosisKey>>
    processUrlList(Context context, RequestQueue queue, List<Pair<URL, String>> diagnosisKeysUrlsWithCountryCode) {
        Subject<List<DiagnosisKey>> diagnosisKeysSubject = ReplaySubject.create();

        for (Pair<URL, String> urlWithCountryCode: diagnosisKeysUrlsWithCountryCode) {
            Log.d(TAG, "Going to download: " + urlWithCountryCode);
            ByteArrayRequest byteArrayRequest = new ByteArrayRequest(
                    Request.Method.GET,
                    urlWithCountryCode.first.toString(),
                    fileBytes -> {
                        if (fileBytes.length == 0) {
                            Log.d(TAG, "Download resulted in 0 bytes: " + urlWithCountryCode);
                            diagnosisKeysSubject.onNext(new ArrayList<>());
                        } else {
                            Log.d(TAG, "Download complete: " + urlWithCountryCode);
                            diagnosisKeysSubject.onNext(parseBytesToTeks(context, fileBytes, urlWithCountryCode.second));
                        }
                    },
                    diagnosisKeysSubject::onError);
            queue.add(byteArrayRequest);
        }
        return diagnosisKeysSubject.take(diagnosisKeysUrlsWithCountryCode.size()).doFinally(diagnosisKeysSubject::onComplete).reduce(new ArrayList<>(), (accumulated, current) -> {
            accumulated.addAll(current);
            return accumulated;
        });
    }
}
