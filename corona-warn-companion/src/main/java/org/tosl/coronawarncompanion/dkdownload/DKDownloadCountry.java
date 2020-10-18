package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;

import java.util.Date;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;


public interface DKDownloadCountry {
    Observable<Pair<byte[], DownloadFileInfo>> getDKBytesAndFileInfo(Context context, OkHttpClient okHttpClient, Date minDate);
    String getCountryCode(Context context);
}
