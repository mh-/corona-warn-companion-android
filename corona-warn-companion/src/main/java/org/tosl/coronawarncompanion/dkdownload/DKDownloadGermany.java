package org.tosl.coronawarncompanion.dkdownload;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.util.Pair;
import org.tosl.coronawarncompanion.R;
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

public class DKDownloadGermany implements DKDownloadCountry {
    private static final String TAG = "DKDownloadGermany";

    private static final String DK_URL = "https://svc90.main.px.t-online.de/version/v1/diagnosis-keys/country/DE/";

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
    public Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate) {

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
                        .map(DKDownloadGermany::getStringFromDate)
                        .flatMapMaybe(date -> DKDownloadUtils.wrapRetrofit(
                                context, api.getDKsForDate(date))
                                .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded day: " + date)))
                        .concatWith(
                                DKDownloadUtils.wrapRetrofit(
                                        context, api.listHours(datesListCurrentDatePair.second))
                                        .doOnSuccess(list -> Log.d(TAG, "Downloaded hours list: " + list))
                                        .flatMapObservable(hoursListString -> Observable
                                                .fromIterable(Arrays.asList(parseCwsListResponse(hoursListString))))
                                        .flatMapMaybe(hour -> DKDownloadUtils.wrapRetrofit(
                                                context, api.getDKsForDateAndHour(datesListCurrentDatePair.second, hour))
                                                .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded hour: " + hour)))))
                .map(ResponseBody::bytes);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_germany);
    }
}
