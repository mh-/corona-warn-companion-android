package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.tosl.coronawarncompanion.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

//public class DKDownloadAustria implements DKDownloadCountry {
//
//    private static final String DK_URL = "https://cdn.prod-rca-coronaapp-fd.net";
//
//    @Override
//    public Single<List<Pair<URL, String>>> getUrls(Context context, RequestQueue queue, Date minDate) {
//        Subject<List<Pair<URL, String>>> availableUrlsSubject = AsyncSubject.create();
//        JsonObjectRequest jsonRequest = new JsonObjectRequest(
//                Request.Method.GET,
//                DK_URL + "/exposures/at/index.json",
//                null,
//                json -> {
//                    List<Pair<URL, String>> urlList = new ArrayList<>();
//                    try {
//                        JSONArray paths = json.getJSONObject("full_14_batch").getJSONArray("batch_file_paths");
//                        for (int i=0; i<paths.length(); i++) {
//                            URL url = new URL(DK_URL + paths.getString(i).replace("\\", "/"));
//                            urlList.add(new Pair<>(url, getCountryCode(context)));
//                        }
//                        availableUrlsSubject.onNext(urlList);
//                    } catch (JSONException | MalformedURLException e) {
//                        availableUrlsSubject.onError(e);
//                    }
//                    availableUrlsSubject.onComplete();
//                },
//                availableUrlsSubject::onError
//        );
//        queue.add(jsonRequest);
//        return availableUrlsSubject.first(new ArrayList<>());
//    }
//
//    @Override
//    public String getCountryCode(Context context) {
//        return context.getResources().getString(R.string.country_code_austria);
//    }
//}
