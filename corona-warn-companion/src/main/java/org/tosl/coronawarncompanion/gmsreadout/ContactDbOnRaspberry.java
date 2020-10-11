package org.tosl.coronawarncompanion.gmsreadout;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;

import org.tosl.coronawarncompanion.R;
import org.tosl.coronawarncompanion.rpis.RpiList;

import java.nio.ByteBuffer;

import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;

public class ContactDbOnRaspberry {
    private static final String TAG = "ContactDbOnRaspberry";
    private static final String RASPBERRY_URL = "http://192.168.43.16:7331/";

    interface Api {
        @GET("get")
        Maybe<ResponseBody> getRaspberryDb();
    }

    public static Single<RpiList> get(Context context, OkHttpClient okHttpClient) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(RASPBERRY_URL)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        ContactDbOnRaspberry.Api api = retrofit.create(ContactDbOnRaspberry.Api.class);

        return api.getRaspberryDb()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> {
                    Log.e(TAG, "Error during downloading DB from raspberry: " + error);
                    Toast toast = Toast.makeText(context,
                            context.getResources().getString(R.string.toast_download_error, error.getMessage()), Toast.LENGTH_LONG);
                    toast.show();
                })
                .onErrorComplete()
                .flatMapSingle(response -> {
                    RpiList rpiList = new RpiList();
                    ContactRecordsProtos.RpiDb rpiDb;
                    try {
                        rpiDb = ContactRecordsProtos.RpiDb.parseFrom(response.bytes());
                    } catch (InvalidProtocolBufferException e) {
                        return Single.error(e);
                    }
                    for (ContactRecordsProtos.RpiDbEntry rpiDbEntry : rpiDb.getEntryList()) {
                        byte[] rpiBytes = new byte[16];
                        ByteBuffer keyBuf = rpiDbEntry.getKey().asReadOnlyByteBuffer();
                        int daysSinceEpochUTC = keyBuf.getShort();
                        keyBuf.get(rpiBytes);
                        rpiList.addEntry(daysSinceEpochUTC, rpiBytes, rpiDbEntry.getValue());
                    }
                    return Single.just(rpiList);
                });
    }
}
