// Based on https://github.com/uhengart/retrieve-canadian-diagnosis-keys (MIT License, Copyright (c) 2020 Urs Hengartner)

package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Log;

import org.tosl.coronawarncompanion.R;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DKDownloadCanada implements DKDownloadCountry {
    private static final String TAG = "DKDownloadCanada";

    // retrieved from COVID Alert APK
    private static final String MCC_CODE = "302";
    private static final String DK_URL = "https://retrieval.covid-notification.alpha.canada.ca/retrieve/";
    private static final String HMAC_KEY = "3631313045444b345742464633504e44524a3457494855505639593136464a3846584d4c59334d30";

    // See https://github.com/cds-snc/covid-shield-server/pull/176
    private static final String LAST_14_DAYS_PERIOD = "00000";

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    interface Api {
        @GET("{mcc}/{interval}/{hmac}")
        Maybe<ResponseBody> getDKs(@Path("mcc") String mcc, @Path("interval") String interval, @Path("hmac") String hmac);
    }

    private static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] hexToBytes(String s)
    {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    @Override
    public Observable<byte[]> getDKBytes(Context context, OkHttpClient okHttpClient, Date minDate) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DK_URL)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        DKDownloadCanada.Api api = retrofit.create(DKDownloadCanada.Api.class);
        String hmac;

        try {
            String message = MCC_CODE + ":" + LAST_14_DAYS_PERIOD + ":" + System.currentTimeMillis() / 1000 / 3600;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(hexToBytes(HMAC_KEY), "HmacSHA256");
            mac.init(secretKeySpec);
            hmac = bytesToHex(mac.doFinal(message.getBytes()));
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return Observable.error(e);
        }

        return DKDownloadUtils.wrapRetrofit(context, api.getDKs(MCC_CODE, LAST_14_DAYS_PERIOD, hmac))
                .doOnSuccess(response -> Log.d(TAG, "downloaded DKs"))
                .flatMapObservable(response -> Observable.just(response.bytes()));
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_canada);
    }
}
