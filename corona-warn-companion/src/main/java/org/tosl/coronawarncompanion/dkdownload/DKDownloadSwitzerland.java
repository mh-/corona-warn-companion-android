package org.tosl.coronawarncompanion.dkdownload;

import com.android.volley.RequestQueue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

public class DKDownloadSwitzerland implements DKDownloadCountry {

    private static final String DK_URL = "https://www.pt-d.bfs.admin.ch/v1/gaen/exposed/";

    @Override
    public Single<List<URL>> getUrls(RequestQueue queue, Date minDate) {

        long millisInDay = TimeUnit.HOURS.toMillis(24);

        long minDay = minDate.getTime() / millisInDay;
        long maxDay = System.currentTimeMillis() / millisInDay;

        List<URL> urlList = new ArrayList<>();

        for (long day = minDay; day <= maxDay; day++) {
            try {
                URL url = new URL(DK_URL + day * millisInDay);
                urlList.add(url);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        return Single.just(urlList);
    }
}
