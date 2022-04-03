/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020-2022  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tosl.coronawarncompanion;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;

import java.util.TimeZone;

public class CWCApplication extends Application {

    public static SharedPreferences sharedPreferences;

    public enum AppModeOptions {NORMAL_MODE, DEMO_MODE, RAMBLE_MODE, MICROG_MODE, CCTG_MODE}
    public static AppModeOptions appMode = AppModeOptions.NORMAL_MODE;

    static final int maxNumDownloadDays = 10;
    static final int minNumDownloadDays = 1;
    static int numDownloadDays = maxNumDownloadDays;
    public static boolean mainActivityShouldBeRecreatedAnyway = false;
    public static boolean firstRunOfMainActivity = true;

    private static MatchEntryContent matchEntryContent = null;
    public static MatchEntryContent getMatchEntryContent() {return matchEntryContent;}
    public static void setMatchEntryContent(
            MatchEntryContent myMatchEntryContent) {matchEntryContent = myMatchEntryContent;
    }

    private static boolean locationDataAvailable = false;
    public static boolean getLocationDataAvailable() {
        return locationDataAvailable;
    }
    public static void setLocationDataAvailable(boolean locationDataAvailable) {
        CWCApplication.locationDataAvailable = locationDataAvailable;
    }

    private static int timeZoneOffsetSeconds;
    public static int getTimeZoneOffsetSeconds() {return timeZoneOffsetSeconds;}

    public static boolean backgroundThreadsRunning = false;
    public static boolean backgroundThreadsShouldStop = false;

    public static int getNumberOfActiveCountries() {
        int num = 0;
        for (Country country : Country.values()) {
            if (country.isDownloadKeysFrom()) num++;
        }
        return num;
    }
    public static String getFlagsString(Context context) {
        StringBuilder sb = new StringBuilder();
        for (Country country : Country.values()) {
            if (country.isDownloadKeysFrom()) {
                sb.append(country.getFlag(context));
            }
        }
        return sb.toString();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        timeZoneOffsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;
    }
}
