package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;

import org.tosl.coronawarncompanion.R;

public class DKDownloadBelgium extends DKDownloadSAP {
    private static final String DK_URL = "https://c19distcdn-prd.ixor.be/version/v1/diagnosis-keys/country/BE/";

    public DKDownloadBelgium() {
        super(DK_URL);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_germany);
    }
}
