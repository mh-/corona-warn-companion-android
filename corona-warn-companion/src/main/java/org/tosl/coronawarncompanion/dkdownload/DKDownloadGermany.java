package org.tosl.coronawarncompanion.dkdownload;

import android.content.Context;

import org.tosl.coronawarncompanion.R;

public class DKDownloadGermany extends DKDownloadSAP {

    private static final String DK_URL = "https://svc90.main.px.t-online.de/version/v1/diagnosis-keys/country/DE/";

    public DKDownloadGermany() {
        super(DK_URL);
    }

    @Override
    public String getCountryCode(Context context) {
        return context.getResources().getString(R.string.country_code_germany);
    }
}
