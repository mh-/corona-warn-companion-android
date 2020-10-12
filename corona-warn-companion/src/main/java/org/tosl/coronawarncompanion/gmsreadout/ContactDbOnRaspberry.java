package org.tosl.coronawarncompanion.gmsreadout;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import org.tosl.coronawarncompanion.R;
import org.tosl.coronawarncompanion.rpis.RpiList;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Url;

public class ContactDbOnRaspberry {
    private static final String TAG = "ContactDbOnRaspberry";
    private static final String RASPBERRY_URL = "http://192.168.43.16:7331/";
    private static final Pattern PARTIAl_IP_ADDRESS =
          Pattern.compile("^((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])\\.){0,3}"+
                           "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])){0,1}$");
    private static final String NO_IP = "NO_IP";

    interface Api {
        @GET
        Single<ResponseBody> getRaspberryDb(@Url String url);
    }

    private static Single<String> getIpFromDialog(Context context) {
        Single<String> ipFromDialog = Single.create(s -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.ip_address));
            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_PHONE);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                private String mPreviousText = "";
                @Override
                public void afterTextChanged(Editable s) {
                    if(PARTIAl_IP_ADDRESS.matcher(s).matches()) {
                        mPreviousText = s.toString();
                    } else {
                        s.replace(0, s.length(), mPreviousText);
                    }
                }
            });
            builder.setView(input);
            builder.setPositiveButton("OK", (dialog, which) -> s.onSuccess(input.getText().toString()));
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.setOnDismissListener(dialog -> s.onSuccess(NO_IP));
            builder.show();
        });

        return ipFromDialog.flatMap(ip -> ip.equals(NO_IP) ? Single.error(new RuntimeException("no ip")) : Single.just(ip));
    }

    private static Single<String> getIpFromSharedPreferences(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(context.getString(R.string.raspberry_mode), Context.MODE_PRIVATE);
        String ip = sharedPreferences.getString(context.getString(R.string.raspberry_mode_ip), NO_IP);
        if (ip == null || ip.equals(NO_IP)) {
            return getIpFromDialog(context);
        }
        return Single.just(ip);
    }

    private static Single<ResponseBody> getResponse(Context context, Api api, String ip) {
        return api.getRaspberryDb("http://" + ip + ":7331/get")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> {
                    Toast toast = Toast.makeText(context,
                            context.getResources().getString(R.string.toast_download_error, error.getMessage()), Toast.LENGTH_LONG);
                    toast.show();
                })
                .doOnSuccess(response -> {
                    SharedPreferences sharedPreferences = context.getSharedPreferences(context.getString(R.string.raspberry_mode), Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(context.getString(R.string.raspberry_mode_ip), ip);
                    editor.apply();
                });
    }

    private static Single<RpiList> getRpiList(ResponseBody response) {
        return Single.fromCallable(() -> {
            RpiList rpiList = new RpiList();
            ContactRecordsProtos.RpiDb rpiDb = ContactRecordsProtos.RpiDb.parseFrom(response.bytes());
            for (ContactRecordsProtos.RpiDbEntry rpiDbEntry : rpiDb.getEntryList()) {
                byte[] rpiBytes = new byte[16];
                ByteBuffer keyBuf = rpiDbEntry.getKey().asReadOnlyByteBuffer();
                int daysSinceEpochUTC = keyBuf.getShort();
                keyBuf.get(rpiBytes);
                rpiList.addEntry(daysSinceEpochUTC, rpiBytes, rpiDbEntry.getValue());
            }
            return rpiList;
        });
    }

    public static Single<RpiList> get(Context context, OkHttpClient okHttpClient) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(RASPBERRY_URL)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        Api api = retrofit.create(Api.class);

        return getIpFromSharedPreferences(context).flatMap(ip -> getResponse(context, api, ip)
                .onErrorResumeNext(getIpFromDialog(context).flatMap(newIp -> getResponse(context, api, newIp))))
                .flatMap(ContactDbOnRaspberry::getRpiList);
    }
}
