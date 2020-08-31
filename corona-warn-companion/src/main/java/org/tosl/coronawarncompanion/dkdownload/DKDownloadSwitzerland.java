package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;

import com.android.volley.RequestQueue;

import org.tosl.coronawarncompanion.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

public class DKDownloadSwitzerland implements DKDownloadCountry {

    private static final String DK_URL = "https://www.pt.bfs.admin.ch/v1/gaen/exposed/";

    @Override
    public Single<List<Pair<URL, String>>> getUrls(Context context, RequestQueue queue, Date minDate) {

        long millisInDay = TimeUnit.HOURS.toMillis(24);

        long minDay = minDate.getTime() / millisInDay;
        long maxDay = System.currentTimeMillis() / millisInDay;

        List<Pair<URL, String>> urlList = new ArrayList<>();

        for (long day = minDay; day <= maxDay; day++) {
            try {
                URL url = new URL(DK_URL + day * millisInDay);
                urlList.add(new Pair<>(url, getCountryCode(context)));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        return Single.just(urlList);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_switzerland);
    }
}
