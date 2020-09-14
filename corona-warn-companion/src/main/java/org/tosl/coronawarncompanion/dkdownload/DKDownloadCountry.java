package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;
import java.util.Date;
import io.reactivex.Observable;


public interface DKDownloadCountry {
    Observable<byte[]> getDKBytes(Context context, Date minDate);
    String getCountryCode(Context context);
}
