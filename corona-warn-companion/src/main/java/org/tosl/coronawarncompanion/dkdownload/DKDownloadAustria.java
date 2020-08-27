package org.tosl.coronawarncompanion.dkdownload;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.AsyncSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class DKDownloadAustria implements DKDownloadCountry {

    private static final String DK_URL = "https://cdn.prod-rca-coronaapp-fd.net";

    @Override
    public Single<List<URL>> getUrls(RequestQueue queue, Date minDate) {
        Subject<List<URL>> availableUrlsSubject = AsyncSubject.create();
        JsonObjectRequest jsonRequest = new JsonObjectRequest(
                Request.Method.GET,
                DK_URL + "/exposures/at/index.json",
                null,
                json -> {
                    List<URL> urlList = new ArrayList<>();
                    try {
                        JSONArray paths = json.getJSONObject("full_14_batch").getJSONArray("batch_file_paths");
                        for (int i=0; i<paths.length(); i++) {
                            URL url = new URL(DK_URL + paths.getString(i).replace("\\", "/"));
                            urlList.add(url);
                        }
                        availableUrlsSubject.onNext(urlList);
                    } catch (JSONException | MalformedURLException e) {
                        availableUrlsSubject.onError(e);
                    }
                    availableUrlsSubject.onComplete();
                },
                availableUrlsSubject::onError
        );
        queue.add(jsonRequest);
        return availableUrlsSubject.first(new ArrayList<>());
    }
}
