package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;
import org.tosl.coronawarncompanion.R;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class DKDownloadSwitzerland implements DKDownloadCountry {

    private static final String DK_URL = "https://www.pt.bfs.admin.ch/v1/gaen/exposed/";

    interface Api {
        @GET("{timestamp}")
        Maybe<ResponseBody> getBytes(@Path("timestamp") String timestamp);
    }

    private static final Api api;

    static {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        api = retrofit.create(Api.class);
    }

    @Override
    public Observable<Pair<byte[], String>> getDKBytes(Context context, Date minDate) {

        long millisInDay = TimeUnit.HOURS.toMillis(24);

        long minDay = minDate.getTime() / millisInDay;
        long maxDay = System.currentTimeMillis() / millisInDay;

        List<String> timestamps = new ArrayList<>();

        for (long day = minDay; day <= maxDay; day++) {
            timestamps.add(String.valueOf(day * millisInDay));
        }

        return Observable.fromIterable(timestamps)
                .flatMapMaybe(timestamp -> api
                        .getBytes(timestamp)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                )
                .map(responseBody -> new Pair<>(responseBody.bytes(), getCountryCode(context)));
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_switzerland);
    }
}
