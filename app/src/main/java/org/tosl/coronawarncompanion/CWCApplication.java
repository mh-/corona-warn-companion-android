package org.tosl.coronawarncompanion;

import android.app.Application;
import android.content.Context;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;

import java.util.ArrayList;
import java.util.TimeZone;

public class CWCApplication extends Application {

    public static boolean DEMO_MODE = true;  // Set this to true to enable app-wide DEMO MODE

    private RpiList rpiList = null;
    public RpiList getRpiList() {return rpiList;}
    public void setRpiList(RpiList rpiList) {this.rpiList = rpiList;}

    private ArrayList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList = null;
    public ArrayList<DiagnosisKeysProtos.TemporaryExposureKey> getDiagnosisKeysList() {return diagnosisKeysList;}
    public void setDiagnosisKeysList(ArrayList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList) {this.diagnosisKeysList = diagnosisKeysList;}

    private MatchEntryContent matchEntryContent = null;
    public MatchEntryContent getMatchEntryContent() {return matchEntryContent;}
    public void setMatchEntryContent(MatchEntryContent matchEntryContent) {this.matchEntryContent = matchEntryContent;}

    private int timeZoneOffsetSeconds;
    public int getTimeZoneOffsetSeconds() {return timeZoneOffsetSeconds;}

    private static Context context;
    public static Context getAppContext() {
        return CWCApplication.context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        timeZoneOffsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;
        //TODO: refactor this, time zones can be handled by DateFormat, Calendar, etc., instead of manually adding Offset
        CWCApplication.context = getApplicationContext();
    }
}
