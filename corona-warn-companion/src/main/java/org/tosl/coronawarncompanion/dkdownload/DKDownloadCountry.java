package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;

import com.android.volley.RequestQueue;

import java.net.URL;
import java.util.Date;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Observable;


public interface DKDownloadCountry {
    Observable<Pair<byte[], String>> getDKBytes(Context context, Date minDate);
    String getCountryCode(Context context);
}
