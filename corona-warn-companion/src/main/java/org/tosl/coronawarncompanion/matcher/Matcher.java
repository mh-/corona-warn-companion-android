/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
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

package org.tosl.coronawarncompanion.matcher;

import android.util.Log;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.rpis.RpiList;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsShouldStop;
import static org.tosl.coronawarncompanion.matcher.Crypto.decryptAem;
import static org.tosl.coronawarncompanion.matcher.Crypto.deriveAemKey;
import static org.tosl.coronawarncompanion.matcher.Crypto.deriveRpiKey;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;

public class Matcher {

    private static final String TAG = "Matcher";

    public static class MatchEntry {
        public final ContactRecordsProtos.ContactRecords contactRecords;
        public final int startTimestampUTC;
        public final byte[] aemXorBytes;

        public MatchEntry(ContactRecordsProtos.ContactRecords contactRecords,
                          int startTimestampUTC, byte[] aemXorBytes) {
            this.contactRecords = contactRecords;
            this.startTimestampUTC = startTimestampUTC;
            this.aemXorBytes = aemXorBytes;
        }
    }

    public static class MatchEntryAndDkAndDay {
        public MatchEntry matchEntry;
        public DiagnosisKey diagnosisKey;
        public Integer daysSinceEpochLocalTZ;

        public MatchEntryAndDkAndDay(MatchEntry matchEntry, DiagnosisKey diagnosisKey, Integer daysSinceEpochLocalTZ) {
            this.matchEntry = matchEntry;
            this.diagnosisKey = diagnosisKey;
            this.daysSinceEpochLocalTZ = daysSinceEpochLocalTZ;
        }
    }

    private final RpiList rpiList;
    private final List<DiagnosisKey> diagnosisKeysList;
    private final int threadNumber;

    final int timeZoneOffsetSeconds;

    public static class ProgressAndMatchEntryAndDkAndDay {
        public int currentProgress;
        public int threadNumber;
        public MatchEntryAndDkAndDay matchEntryAndDkAndDay;

        public ProgressAndMatchEntryAndDkAndDay(int currentProgress, int threadNumber, MatchEntryAndDkAndDay matchEntryAndDkAndDay) {
            this.currentProgress = currentProgress;
            this.threadNumber = threadNumber;
            this.matchEntryAndDkAndDay = matchEntryAndDkAndDay;
        }
    }

    public Observable<ProgressAndMatchEntryAndDkAndDay> getMatchingObservable() {
        return Observable.create(new ObservableOnSubscribe<ProgressAndMatchEntryAndDkAndDay>() {
            @Override
            public void subscribe(ObservableEmitter<ProgressAndMatchEntryAndDkAndDay> emitter) throws Exception {
                try {
                    Log.d(TAG, "Started matching...");
                    int diagnosisKeysListLength = diagnosisKeysList.size();
                    int currentDiagnosisKey = 0;
                    int lastProgress = 0;
                    int currentProgress;
                    Crypto crypto = new Crypto();
                    for (DiagnosisKey dk : diagnosisKeysList) {
                        if (backgroundThreadsShouldStop || emitter.isDisposed()) {
                            break;
                        }
                        currentDiagnosisKey += 1;
                        currentProgress = (int) (100f * currentDiagnosisKey / diagnosisKeysListLength);
                        if (currentProgress != lastProgress) {
                            lastProgress = currentProgress;
                            emitter.onNext(new ProgressAndMatchEntryAndDkAndDay(currentProgress, threadNumber, null));
                        }
                        ArrayList<Crypto.RpiWithInterval> dkRpisWithIntervals =
                                    crypto.createListOfRpisForIntervalRange(deriveRpiKey(dk.dk.getKeyData().toByteArray()),
                                            dk.dk.getRollingStartIntervalNumber(), dk.dk.getRollingPeriod());
                        for (Crypto.RpiWithInterval dkRpiWithInterval : dkRpisWithIntervals) {
                            if (backgroundThreadsShouldStop || emitter.isDisposed()) {
                                break;
                            }
                            RpiList.RpiEntry rpiEntry =
                                    rpiList.searchForRpiOnDaySinceEpochUTCWith2HoursTolerance(dkRpiWithInterval);
                            if (rpiEntry != null) {
                                Log.d(TAG, "Match found!");
                                byte[] aemKey = deriveAemKey(dk.dk.getKeyData().toByteArray());
                                byte[] zeroAem = {0x00, 0x00, 0x00, 0x00};
                                byte[] aemXorBytes = decryptAem(aemKey, zeroAem, rpiEntry.rpiBytes.getBytes());

                                MatchEntryAndDkAndDay matchEntryAndDkAndDay =
                                        new MatchEntryAndDkAndDay(
                                                new MatchEntry(rpiEntry.contactRecords, rpiEntry.startTimeStampUTC, aemXorBytes),
                                                dk,
                                                getDaysFromSeconds(rpiEntry.startTimeStampUTC + timeZoneOffsetSeconds));
                                if (!emitter.isDisposed()) {
                                    emitter.onNext(new ProgressAndMatchEntryAndDkAndDay(currentProgress, threadNumber, matchEntryAndDkAndDay));
                                }
                            }
                        }
                    }
                    Log.d(TAG, "Finished matching...");
                    emitter.onComplete();
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }
        });
    }

    public Matcher(RpiList rpis, List<DiagnosisKey> diagnosisKeys, int threadNumber) {
        this.rpiList = rpis;
        this.diagnosisKeysList = diagnosisKeys;
        this.threadNumber = threadNumber;
        timeZoneOffsetSeconds = CWCApplication.getTimeZoneOffsetSeconds();
    }
}
