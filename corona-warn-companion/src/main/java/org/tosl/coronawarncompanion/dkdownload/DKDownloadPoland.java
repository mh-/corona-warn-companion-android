package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;
import org.tosl.coronawarncompanion.R;
import java.util.Date;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Url;

public class DKDownloadPoland implements DKDownloadCountry {

    private static final String DK_URL = "https://exp.safesafe.app/";

    interface Api {
        @GET
        Maybe<String> getIndex(@Url String uri);

        @GET
        Maybe<ResponseBody> getFile(@Url String uri);
    }

    private static final Api api;

    static {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        api = retrofit.create(Api.class);
    }

    @Override
    public Observable<byte[]> getDKBytes(Context context, Date minDate) {

        return DKDownloadUtils.wrapRetrofit(context, api.getIndex(DK_URL + "/index.txt"))
                .flatMapObservable(indexString -> Observable.fromArray(indexString.split("\n")))
                .flatMapMaybe(availableFile -> DKDownloadUtils.wrapRetrofit(context, api.getFile(DK_URL + availableFile)))
                .map(ResponseBody::bytes);

    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_poland);
    }
}
