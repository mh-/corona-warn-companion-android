package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.R;
import java.util.Date;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Url;

public class DKDownloadCzechia implements DKDownloadCountry {
    private static final String TAG = "DKDownloadCzechia";

    private static final String DK_URL = "https://storage.googleapis.com/exposure-notification-export-qhqcx/";

    interface Api {
        @GET("erouska/index.txt")
        Maybe<String> getIndex();

        @GET
        Maybe<ResponseBody> getFile(@Url String uri);
    }

    @Override
    public Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate, int maxNumDownloadDays) {

        // TODO: maxNumDownloadDays not yet used.

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        Api api = retrofit.create(Api.class);

        return DKDownloadUtils.wrapRetrofit(context, api.getIndex())
                .doOnSuccess(indexString -> Log.d(TAG, "Downloaded index"))
                .flatMapObservable(indexString -> Observable.fromArray(indexString.split("\n")))
                .flatMapMaybe(availableFile -> DKDownloadUtils.wrapRetrofit(context, api.getFile(availableFile))
                        .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded file: " + availableFile)))
                .map(ResponseBody::bytes);

    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_czechia);
    }
}
