package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.R;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class DKDownloadEnglandAndWales implements DKDownloadCountry {
    private static final String TAG = "DKDownloadEAW";

    private final static int SECONDS_IN_HOUR = 60 * 60;
    private final static int SECONDS_IN_DAY = 24 * SECONDS_IN_HOUR;
    private final static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneId.of("UTC"));
    private static final String DK_URL = "https://distribution-te-prod.prod.svc-test-trace.nhs.uk/";

    interface Api {
        @GET("distribution/daily/{timestamp}")
        Maybe<ResponseBody> getDaily(@Path("timestamp") String timestamp);

        @GET("distribution/two-hourly/{timestamp}")
        Maybe<ResponseBody> getHourly(@Path("timestamp") String timestamp);
    }

    @Override
    public Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate) {

        Calendar firstAvailableDate = Calendar.getInstance();
        TimeZone tz = TimeZone.getTimeZone("UTC");
        firstAvailableDate.setTimeZone(tz);
        firstAvailableDate.add(Calendar.DATE, -13);
        firstAvailableDate.set(Calendar.HOUR_OF_DAY, 0);
        firstAvailableDate.set(Calendar.MINUTE, 0);
        firstAvailableDate.set(Calendar.SECOND, 0);
        firstAvailableDate.set(Calendar.MILLISECOND, 0);
        if (minDate.compareTo(firstAvailableDate.getTime()) < 0) {
            minDate = firstAvailableDate.getTime();
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        Api api = retrofit.create(Api.class);

        List<String> dailyZips = new ArrayList<>();
        List<String> hourlyZips = new ArrayList<>();
        Instant now = Instant.now();
        Instant dailyInstant;
        Instant hourlyInstant;
        for (dailyInstant = minDate.toInstant(), hourlyInstant = minDate.toInstant(); dailyInstant.isBefore(now); hourlyInstant = dailyInstant, dailyInstant = dailyInstant.plusSeconds(SECONDS_IN_DAY)) {
            dailyZips.add(DATE_FORMATTER.format(dailyInstant) + ".zip");
        }
        for (hourlyInstant = hourlyInstant.plusSeconds(2 * SECONDS_IN_HOUR); hourlyInstant.isBefore(now); hourlyInstant = hourlyInstant.plusSeconds(2 * SECONDS_IN_HOUR)) {
            hourlyZips.add(DATE_FORMATTER.format(hourlyInstant) + ".zip");
        }

        return Observable.fromIterable(dailyZips)
                .flatMapMaybe(timestamp -> DKDownloadUtils.wrapRetrofit(context, api.getDaily(timestamp))
                        .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded daily: " + timestamp)))
                .concatWith(Observable.fromIterable(hourlyZips)
                        .flatMapMaybe(timestamp -> DKDownloadUtils.wrapRetrofit(context, api.getHourly(timestamp))
                                .doOnSuccess(responseBody -> Log.d(TAG, "Download two-hourly: " + timestamp))))
                .map(ResponseBody::bytes);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_eaw);
    }
}
