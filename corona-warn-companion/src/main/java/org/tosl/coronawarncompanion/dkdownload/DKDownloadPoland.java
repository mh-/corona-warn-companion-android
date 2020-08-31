package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;

import org.tosl.coronawarncompanion.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.AsyncSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class DKDownloadPoland implements DKDownloadCountry {

    private static final String DK_URL = "https://exp.safesafe.app/";

    @Override
    public Single<List<Pair<URL, String>>> getUrls(Context context, RequestQueue queue, Date minDate) {
        Subject<List<Pair<URL, String>>> availableUrlsSubject = AsyncSubject.create();
        StringRequest stringRequest = new StringRequest(
                Request.Method.GET,
                DK_URL + "/index.txt",
                availableFilesStr -> {
                    String[] availableFiles = availableFilesStr.split("\n");
                    List<Pair<URL, String>> availableUrls = new ArrayList<>();
                    for (String availableFile : availableFiles) {
                        try {
                            URL url = new URL(DK_URL + availableFile);
                            availableUrls.add(new Pair<>(url, getCountryCode(context)));
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                    availableUrlsSubject.onNext(availableUrls);
                    availableUrlsSubject.onComplete();
                },
                availableUrlsSubject::onError
        );
        queue.add(stringRequest);
        return availableUrlsSubject.first(new ArrayList<>());
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_poland);
    }
}
