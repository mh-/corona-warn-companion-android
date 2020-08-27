package org.tosl.coronawarncompanion.dkdownload;

import com.android.volley.RequestQueue;

import java.net.URL;
import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

public interface DKDownloadCountry {
    Single<List<URL>> getUrls(RequestQueue queue, Date minDate, Date maxDate);
}
