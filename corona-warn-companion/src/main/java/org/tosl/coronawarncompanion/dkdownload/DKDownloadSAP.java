package org.tosl.coronawarncompanion.dkdownload;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.util.Pair;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromDate;

public abstract class DKDownloadSAP implements DKDownloadCountry {
    private final String TAG;
    private final String DK_URL;

    interface Api {
        @GET("date")
        Maybe<String> listDates();

        @GET("date/{date}/hour")
        Maybe<String> listHours(@Path("date") String date);

        @GET("date/{date}")
        Maybe<ResponseBody> getDKsForDate(@Path("date") String date);

        @GET("date/{date}/hour/{hour}")
        Maybe<ResponseBody> getDKsForDateAndHour(@Path("date") String date, @Path("hour") String hour);
    }

    public DKDownloadSAP(String dkUrl) {
        this.TAG = this.getClass().getName();
        this.DK_URL = dkUrl;
    }

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private static String[] parseCwsListResponse(String str) {
        String reducedStr = str.replace("\"","");
        reducedStr = reducedStr.replace("[","");
        reducedStr = reducedStr.replace("]","");
        return reducedStr.split(",");
    }

    private static String getStringFromDate(Date date) {
        StringBuffer stringBuffer = new StringBuffer();
        return dateFormatter.format(date, stringBuffer, new FieldPosition(0)).toString();
    }

    private static String currentDate(List<String> datesList) throws ParseException {
        if (datesList.size() == 0) {
            throw new RuntimeException("Germany: No dates to download");
        }
        String lastDateString = datesList.get(datesList.size()-1);
        Date lastDate = dateFormatter.parse(lastDateString);
        Calendar c = Calendar.getInstance();
        if (lastDate == null) {
            throw new RuntimeException("Germany: Could not parse date: " + lastDateString);
        }
        c.setTime(lastDate);
        c.add(Calendar.DATE, 1);
        Date currentDate = c.getTime();
        return getStringFromDate(currentDate);
    }

    @Override
    public Observable<Pair<byte[], DownloadFileInfo>> getDKBytesAndFileInfo(Context context, OkHttpClient okHttpClient, Date minDate) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        Api api = retrofit.create(Api.class);

        return DKDownloadUtils.wrapRetrofit(context, api.listDates())
                .doOnSuccess(list -> Log.d(TAG, "retrieved dates: " + list))
                .map(datesListString -> Arrays.asList(parseCwsListResponse(datesListString)))
                .map(datesList -> new Pair<>(datesList, currentDate(datesList)))
                .flatMapObservable(datesListCurrentDatePair -> Observable.fromIterable(datesListCurrentDatePair.first)
                        .map(dateFormatter::parse)
                        .filter(date -> date.compareTo(minDate) > 0)
                        .map(DKDownloadSAP::getStringFromDate)
                        .flatMapMaybe(dateStringISO8601 -> DKDownloadUtils.wrapRetrofit(
                                context, api.getDKsForDate(dateStringISO8601))
                                .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded day: " + dateStringISO8601))
                                .map(responseBody -> new Pair<>(responseBody.bytes(),
                                        new DownloadFileInfo(getCountryCode(context), dateStringISO8601,
                                                getDaysSinceEpochFromDate(dateFormatter.parse(dateStringISO8601))))))
                        .concatWith(DKDownloadUtils.wrapRetrofit(
                                context, api.listHours(datesListCurrentDatePair.second))
                                .doOnSuccess(list -> Log.d(TAG, "Downloaded hours list: " + list))
                                .flatMapObservable(hoursListString -> Observable
                                        .fromIterable(Arrays.asList(parseCwsListResponse(hoursListString))))
                                .flatMapMaybe(hour -> DKDownloadUtils.wrapRetrofit(
                                        context, api.getDKsForDateAndHour(datesListCurrentDatePair.second, hour))
                                        .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded hour: " + hour))
                                        .map(responseBody -> new Pair<>(responseBody.bytes(),
                                                new DownloadFileInfo(getCountryCode(context), datesListCurrentDatePair.second,
                                                        getDaysSinceEpochFromDate(dateFormatter.parse(datesListCurrentDatePair.second))))))));
    }
}
