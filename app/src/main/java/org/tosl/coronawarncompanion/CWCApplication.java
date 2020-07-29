package org.tosl.coronawarncompanion;

import android.app.Application;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.util.LinkedList;

public class CWCApplication extends Application {
    private RpiList rpiList = null;
    public RpiList getRpiList() {return rpiList;}
    public void setRpiList(RpiList rpiList) {this.rpiList = rpiList;}

    private LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList = null;
    public LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> getDiagnosisKeysList() {return diagnosisKeysList;}
    public void setDiagnosisKeysList(LinkedList<DiagnosisKeysProtos.TemporaryExposureKey>  diagnosisKeysList) {this.diagnosisKeysList = diagnosisKeysList;}

    private LinkedList<Matcher.MatchEntry> matches = null;
    public LinkedList<Matcher.MatchEntry> getMatches() {return matches;}
    public void setMatches(LinkedList<Matcher.MatchEntry> rpiList) {this.matches = matches;}
}

