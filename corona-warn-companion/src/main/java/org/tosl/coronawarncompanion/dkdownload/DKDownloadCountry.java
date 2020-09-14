package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import java.util.Date;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;


public interface DKDownloadCountry {
    Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate);
    String getCountryCode(Context context);
}
