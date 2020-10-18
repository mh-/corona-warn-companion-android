package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.google.gson.annotations.SerializedName;
import org.tosl.coronawarncompanion.R;
import java.util.Date;
import java.util.List;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class DKDownloadAustria implements DKDownloadCountry {
    private static final String TAG = "DKDownloadAustria";

    private static final String DK_URL = "https://cdn.prod-rca-coronaapp-fd.net/";

    interface Api {
        @GET("exposures/at/index.json")
        Maybe<Index> getIndex();

        @GET("{path}")
        Maybe<ResponseBody> getFile(@Path("path") String path);
    }

    static class Full14Batch {
        @SerializedName("batch_file_paths")
        List<String> batchFilePaths;

        public List<String> getBatchFilePaths() {
            return batchFilePaths;
        }
    }

    static class Index {
        @SerializedName("full_14_batch")
        Full14Batch full14Batch;

        public Full14Batch getFull14Batch() {
            return full14Batch;
        }
    }

    @Override
    public Observable<Pair<byte[], DownloadFileInfo>> getDKBytesAndFileInfo(Context context, OkHttpClient okHttpClient, Date minDate) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        Api api = retrofit.create(Api.class);

        return DKDownloadUtils.wrapRetrofit(context, api.getIndex())
                .doOnSuccess(index -> Log.d(TAG, "Downloaded index"))
                .flatMapObservable(index -> Observable.fromIterable(index.getFull14Batch().getBatchFilePaths()))
                .flatMapMaybe(path -> DKDownloadUtils.wrapRetrofit(context, api.getFile(path))
                        .doOnSuccess(responseBody -> Log.d(TAG, "Downloaded file: " + path))
                        .map(responseBody -> new Pair<>(responseBody.bytes(),
                                new DownloadFileInfo(getCountryCode(context), path, 0))));
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_austria);
    }
}
