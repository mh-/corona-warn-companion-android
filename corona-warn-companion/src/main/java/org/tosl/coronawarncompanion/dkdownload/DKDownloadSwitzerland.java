package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.R;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class DKDownloadSwitzerland implements DKDownloadCountry {
    private static final String TAG = "DKDownloadSwitzerland";

    private static final String DK_URL = "https://www.pt.bfs.admin.ch/v1/gaen/exposed/";

    interface Api {
        @GET("{timestamp}")
        Maybe<ResponseBody> getBytes(@Path("timestamp") String timestamp);
    }


    private static List<String> createTimestamps(Date minDate, int maxNumDownloadDays) {
        long millisInDay = TimeUnit.HOURS.toMillis(24);

        long minDay = minDate.getTime() / millisInDay;
        long maxDay = System.currentTimeMillis() / millisInDay;

        long minDownloadDayRequested = maxDay - (maxNumDownloadDays+1);
        if (minDay < minDownloadDayRequested) {
            minDay = minDownloadDayRequested;
        }

        List<String> timestamps = new ArrayList<>();

        for (long day = minDay; day <= maxDay; day++) {
            timestamps.add(String.valueOf(day * millisInDay));
        }

        return timestamps;
    }

    @Override
    public Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate, int maxNumDownloadDays) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        Api api = retrofit.create(Api.class);

        return Observable.fromIterable(createTimestamps(minDate, maxNumDownloadDays))
                .flatMapMaybe(timestamp -> DKDownloadUtils.wrapRetrofit(context, api.getBytes(timestamp))
                        .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded timestamp: " + timestamp)))
                .map(ResponseBody::bytes);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_switzerland);
    }
}
