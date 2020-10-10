package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;

import org.tosl.coronawarncompanion.R;

import java.util.Date;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class DKDownloadNetherlands implements DKDownloadCountry {
    private static final String TAG = "DKDownloadNetherlands";

    private static final String DK_URL = "https://productie.coronamelder-dist.nl/v2/";

    interface Api {
        @GET("manifest")
        Maybe<ResponseBody> getManifest();

        @GET("exposurekeyset/{id}")
        Maybe<ResponseBody> getDKs(@Path("id") String id);
    }

    static class Manifest {
        @SerializedName("exposureKeySets")
        List<String> ids;

        public List<String> getIds() {
            return ids;
        }
    }

    @Override
    public Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        DKDownloadNetherlands.Api api = retrofit.create(DKDownloadNetherlands.Api.class);

        return DKDownloadUtils.wrapRetrofit(context, api.getManifest())
                .doOnSuccess(response -> Log.d(TAG, "Downloaded manifest"))
                .flatMapObservable(response -> {
                    String manifestString = new String(Unzip.getUnzippedBytesFromZipFileBytes(response.bytes(), "content.bin"));
                    Manifest manifest = new Gson().fromJson(manifestString, Manifest.class);
                    return Observable.fromIterable(manifest.getIds());
                })
                .flatMapMaybe(id -> DKDownloadUtils.wrapRetrofit(context, api.getDKs(id))
                        .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded file: " + id)))
                .map(ResponseBody::bytes);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_netherlands);
    }
}
