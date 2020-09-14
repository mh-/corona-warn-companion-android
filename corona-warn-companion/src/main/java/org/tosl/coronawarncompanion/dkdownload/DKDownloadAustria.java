package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;
import com.google.gson.annotations.SerializedName;
import org.tosl.coronawarncompanion.R;
import java.util.Date;
import java.util.List;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class DKDownloadAustria implements DKDownloadCountry {

    private static final String DK_URL = "https://cdn.prod-rca-coronaapp-fd.net/";

    interface Api {
        @GET("exposures/at/index.json")
        Maybe<Index> getIndex();

        @GET("{path}")
        Maybe<ResponseBody> getFile(@Path("path") String path);
    }

    private final static Api api;

    static {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        api = retrofit.create(Api.class);
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
    public Observable<Pair<byte[], String>> getDKBytes(Context context, Date minDate) {

        return DKDownloadUtils.wrapRetrofit(context, api.getIndex())
                .flatMapObservable(index -> Observable.fromIterable(index.getFull14Batch().getBatchFilePaths()))
                .flatMapMaybe(path -> DKDownloadUtils.wrapRetrofit(context, api.getFile(path)))
                .map(responseBody -> new Pair<>(responseBody.bytes(), getCountryCode(context)));
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_austria);
    }
}
