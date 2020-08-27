package org.tosl.coronawarncompanion.dkdownload;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;

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
    public Single<List<URL>> getUrls(RequestQueue queue, Date minDate) {
        Subject<List<URL>> availableUrlsSubject = AsyncSubject.create();
        StringRequest stringRequest = new StringRequest(
                Request.Method.GET,
                DK_URL + "/index.txt",
                availableFilesStr -> {
                    String[] availableFiles = availableFilesStr.split("\n");
                    List<URL> availableUrls = new ArrayList<>();
                    for (String availableFile : availableFiles) {
                        try {
                            URL url = new URL(DK_URL + availableFile);
                            availableUrls.add(url);
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
}
