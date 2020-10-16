package org.tosl.coronawarncompanion;

import android.content.Context;

import org.tosl.coronawarncompanion.dkdownload.DKDownloadAustria;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadBelgium;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadCanada;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadCountry;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadCzechia;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadGermany;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadNetherlands;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadPoland;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadSwitzerland;

public enum Country {

    Austria(R.id.austria, R.string.country_code_austria, R.string.flag_austria, new DKDownloadAustria()),
    Belgium(R.id.belgium, R.string.country_code_belgium, R.string.flag_belgium, new DKDownloadBelgium()),
    Canada(R.id.canada, R.string.country_code_canada, R.string.flag_canada, new DKDownloadCanada()),
    Czechia(R.id.czechia, R.string.country_code_czechia, R.string.flag_czechia, new DKDownloadCzechia()),
    Germany(R.id.germany, R.string.country_code_germany, R.string.flag_germany, new DKDownloadGermany()),
    Netherlands(R.id.netherlands, R.string.country_code_netherlands, R.string.flag_netherlands, new DKDownloadNetherlands()),
    Poland(R.id.poland, R.string.country_code_poland, R.string.flag_poland, new DKDownloadPoland()),
    Switzerland(R.id.switzerland, R.string.country_code_switzerland, R.string.flag_switzerland, new DKDownloadSwitzerland());

    private final int id;
    private final int code;
    private final int flag;
    private final DKDownloadCountry dkDownloadCountry;
    private boolean downloadKeysFrom;

    Country(int id, int code, int flag, DKDownloadCountry dkDownloadCountry) {
        this.id = id;
        this.code = code;
        this.flag = flag;
        this.dkDownloadCountry = dkDownloadCountry;
        this.downloadKeysFrom = false;
    }

    public int getId() {
        return id;
    }

    public String getCode(Context context) {
        return context.getResources().getString(code);
    }

    public String getFlag(Context context) {
        return context.getResources().getString(flag);
    }

    public DKDownloadCountry getDkDownloadCountry() {
        return dkDownloadCountry;
    }

    public boolean isDownloadKeysFrom() {
        return downloadKeysFrom;
    }

    public void setDownloadKeysFrom(boolean downloadKeysFrom) {
        this.downloadKeysFrom = downloadKeysFrom;
    }
}
