package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import android.util.Pair;
import java.util.Date;
import io.reactivex.Observable;


public interface DKDownloadCountry {
    Observable<Pair<byte[], String>> getDKBytes(Context context, Date minDate);
    String getCountryCode(Context context);
}
