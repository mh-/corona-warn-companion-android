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

package org.tosl.coronawarncompanion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import org.tosl.coronawarncompanion.barcharts.BarChartSync;
import org.tosl.coronawarncompanion.barcharts.CwcBarChart;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadAustria;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadCountry;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadGermany;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadPoland;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadSwitzerland;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadUtils;
import org.tosl.coronawarncompanion.gmsreadout.ContactDbOnDisk;
import org.tosl.coronawarncompanion.ramblereadout.RambleDbOnDisk;
import org.tosl.coronawarncompanion.rpis.RpiList;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;

import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.DEMO_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.NORMAL_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.RAMBLE_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsShouldStop;
import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsRunning;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromMillis;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromDate;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromDays;
import static org.tosl.coronawarncompanion.tools.Utils.resolveColorAttr;
import static org.tosl.coronawarncompanion.tools.Utils.standardRollingPeriod;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String EXTRA_MESSAGE_DAY = "org.tosl.coronawarncompanion.DAY_MESSAGE";
    public static final String EXTRA_MESSAGE_COUNT = "org.tosl.coronawarncompanion.COUNT_MESSAGE";
    private static boolean mainActivityShouldBeRecreated = false;
    private static CWCApplication.AppModeOptions desiredAppMode;
    private RpiList rpiList = null;
    private Date maxDate = null;
    private Date minDate = null;

    @SuppressWarnings("SpellCheckingInspection")
    private final int normalBarColor = Color.parseColor("#8CEAFF");
    private final int matchBarColor = Color.parseColor("red");

    private CwcBarChart chartRpis;
    private CwcBarChart chartDks;
    private CwcBarChart chartMatches;
    private TextView textViewRpis;
    private TextView textViewDks;
    private TextView textViewMatches;
    private TextView textViewExtractionError;
    private TextView textViewDownloadError;

    private Context context;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);
        if (CWCApplication.appMode == NORMAL_MODE) {
            menu.findItem(R.id.normalmode).setChecked(true);
        } else if (CWCApplication.appMode == DEMO_MODE) {
            menu.findItem(R.id.demomode).setChecked(true);
        } if (CWCApplication.appMode == RAMBLE_MODE) {
            menu.findItem(R.id.ramblemode).setChecked(true);
        }
        if (CWCApplication.downloadKeysFromAustria) menu.findItem(R.id.austria).setChecked(true);
        if (CWCApplication.downloadKeysFromGermany) menu.findItem(R.id.germany).setChecked(true);
        if (CWCApplication.downloadKeysFromPoland) menu.findItem(R.id.poland).setChecked(true);
        if (CWCApplication.downloadKeysFromSwitzerland) menu.findItem(R.id.switzerland).setChecked(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (item.getItemId() == R.id.normalmode || item.getItemId() == R.id.demomode ||
                item.getItemId() == R.id.ramblemode) {
            if (backgroundThreadsShouldStop) {
                // user has to wait a little bit longer
                CharSequence text = getString(R.string.error_app_mode_switching_not_possible);
                Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                return false;
            }
            item.setChecked(true);
            if (item.getItemId() == R.id.normalmode) {
                desiredAppMode = NORMAL_MODE;
            } else if (item.getItemId() == R.id.demomode) {
                desiredAppMode = DEMO_MODE;
            } else {
                desiredAppMode = RAMBLE_MODE;
            }
            if (desiredAppMode != CWCApplication.appMode) {
                SharedPreferences sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(getString(R.string.saved_app_mode), desiredAppMode.ordinal());
                editor.apply();
            }
            recreateMainActivityOnNextPossibleOccasion();
            return true;
        } else if (item.getItemId() == R.id.osslicenses) {
            startActivity(new Intent(this, DisplayLicensesActivity.class));
            return true;
        } else if (item.getItemId() == R.id.austria) {
            boolean desiredNewState = !CWCApplication.downloadKeysFromAustria;
            //noinspection PointlessBooleanExpression
            if (desiredNewState==true || CWCApplication.getNumberOfActiveCountries() > 1) {
                item.setChecked(desiredNewState);
                CWCApplication.downloadKeysFromAustria = desiredNewState;
                SharedPreferences sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(getString(R.string.saved_austria_active), desiredNewState);
                editor.apply();
                recreateMainActivityOnNextPossibleOccasion();
                return true;
            }
            return false;
        } else if (item.getItemId() == R.id.germany) {
            boolean desiredNewState = !CWCApplication.downloadKeysFromGermany;
            //noinspection PointlessBooleanExpression
            if (desiredNewState==true || CWCApplication.getNumberOfActiveCountries() > 1) {
                item.setChecked(desiredNewState);
                CWCApplication.downloadKeysFromGermany = desiredNewState;
                SharedPreferences sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(getString(R.string.saved_germany_active), desiredNewState);
                editor.apply();
                recreateMainActivityOnNextPossibleOccasion();
                return true;
            }
            return false;
        } else if (item.getItemId() == R.id.poland) {
            boolean desiredNewState = !CWCApplication.downloadKeysFromPoland;
            //noinspection PointlessBooleanExpression
            if (desiredNewState==true || CWCApplication.getNumberOfActiveCountries() > 1) {
                item.setChecked(desiredNewState);
                CWCApplication.downloadKeysFromPoland = desiredNewState;
                SharedPreferences sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(getString(R.string.saved_poland_active), desiredNewState);
                editor.apply();
                recreateMainActivityOnNextPossibleOccasion();
                return true;
            }
            return false;
        } else if (item.getItemId() == R.id.switzerland) {
            boolean desiredNewState = !CWCApplication.downloadKeysFromSwitzerland;
            //noinspection PointlessBooleanExpression
            if (desiredNewState==true || CWCApplication.getNumberOfActiveCountries() > 1) {
                item.setChecked(desiredNewState);
                CWCApplication.downloadKeysFromSwitzerland = desiredNewState;
                SharedPreferences sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(getString(R.string.saved_switzerland_active), desiredNewState);
                editor.apply();
                recreateMainActivityOnNextPossibleOccasion();
                return true;
            }
            return false;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void recreateMainActivityOnNextPossibleOccasion() {
        if (backgroundThreadsRunning) {  // don't do recreate() while background threads are running
            mainActivityShouldBeRecreated = true;
            backgroundThreadsShouldStop = true;
        } else {
            recreateMainActivityNow();
        }
    }

    private void recreateMainActivityNow() {
        mainActivityShouldBeRecreated = false;
        CWCApplication.appMode = desiredAppMode;
        recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;

        SharedPreferences sharedPreferences = this.getPreferences(MODE_PRIVATE);

        // get App Mode from SharedPreferences
        int appModeOrdinal = sharedPreferences.getInt(getString(R.string.saved_app_mode), NORMAL_MODE.ordinal());
        try {
            CWCApplication.appMode = CWCApplication.AppModeOptions.values()[appModeOrdinal];
        } catch (ArrayIndexOutOfBoundsException e) {
            CWCApplication.appMode = NORMAL_MODE;
        }
        desiredAppMode = CWCApplication.appMode;

        // get the active countries from SharedPreferences
        CWCApplication.downloadKeysFromAustria = sharedPreferences.getBoolean(getString(R.string.saved_austria_active), false);
        CWCApplication.downloadKeysFromGermany = sharedPreferences.getBoolean(getString(R.string.saved_germany_active), true);
        CWCApplication.downloadKeysFromPoland = sharedPreferences.getBoolean(getString(R.string.saved_poland_active), false);
        CWCApplication.downloadKeysFromSwitzerland = sharedPreferences.getBoolean(getString(R.string.saved_switzerland_active), false);
        if (CWCApplication.getNumberOfActiveCountries() < 1) {
            CWCApplication.downloadKeysFromGermany = true;
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (CWCApplication.appMode == NORMAL_MODE) {
                actionBar.setTitle(R.string.title_activity_main);
            } else if (CWCApplication.appMode == DEMO_MODE) {
                actionBar.setTitle(getString(R.string.title_activity_main_demo_prefix) + getString(R.string.title_activity_main));
            } else if (CWCApplication.appMode == RAMBLE_MODE) {
                actionBar.setTitle(getString(R.string.title_activity_main_ramble_version));
            } else {
                throw new IllegalStateException();
            }
        }

        if (CWCApplication.appMode == DEMO_MODE) {
            Log.i(TAG, "--- DEMO MODE ---");
        } else if (CWCApplication.appMode == RAMBLE_MODE) {
            Log.i(TAG, "--- RAMBLE MODE ---");
        }

        if(backgroundThreadsRunning) {
            // reCreate() was called, e.g. by switching from portrait to landscape, etc.
            backgroundThreadsShouldStop = true;
            while(backgroundThreadsRunning) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        int timeZoneOffsetSeconds = CWCApplication.getTimeZoneOffsetSeconds();
        Log.d(TAG, "Local TimeZone Offset in seconds: "+ timeZoneOffsetSeconds);

        long todayLastMidnightInMillis = getMillisFromDays(getDaysFromMillis(System.currentTimeMillis()));
        maxDate = new Date(todayLastMidnightInMillis);
        minDate = new Date(todayLastMidnightInMillis - getMillisFromDays(14));

        textViewRpis = findViewById(R.id.textViewRpis);
        textViewDks = findViewById(R.id.textViewDks);
        textViewMatches = findViewById(R.id.textViewMatches);
        textViewExtractionError = findViewById(R.id.textViewExtractionError);
        textViewDownloadError = findViewById(R.id.textViewDownloadError);
        BarChartSync barChartSync = new BarChartSync();
        chartRpis = new CwcBarChart(findViewById(R.id.chartRpis), findViewById(R.id.progressBarRpis), barChartSync, this);
        chartDks = new CwcBarChart(findViewById(R.id.chartDks), findViewById(R.id.progressBarDks), barChartSync, this);
        chartMatches = new CwcBarChart(findViewById(R.id.chartMatches), findViewById(R.id.progressBarMatches), barChartSync, this);
        chartMatches.getBarChart().setOnChartValueSelectedListener(new Chart3ValueSelectedListener());

        // 1st Section: Get RPIs from database (requires root), or from demo database, or from RaMBLE

        if (CWCApplication.appMode == NORMAL_MODE || CWCApplication.appMode == DEMO_MODE) {
            ContactDbOnDisk contactDbOnDisk = new ContactDbOnDisk(this);
            rpiList = contactDbOnDisk.getRpisFromContactDB();
        } else if (CWCApplication.appMode == RAMBLE_MODE) {
            RambleDbOnDisk rambleDbOnDisk = new RambleDbOnDisk(this);
            rpiList = rambleDbOnDisk.getRpisFromContactDB(this);
        } else {
            throw new IllegalStateException();
        }

        if ((rpiList != null) && (!rpiList.isEmpty())) {  // check that getting the RPIs didn't fail, e.g. because we didn't get root rights
            SortedSet<Integer> rpiListDaysSinceEpochLocalTZ = rpiList.getAvailableDaysSinceEpochLocalTZ();
            List<BarEntry> dataPoints1 = new ArrayList<>();

            int count = 0;
            for (Integer daysSinceEpochLocalTZ : rpiListDaysSinceEpochLocalTZ) {
                int numEntries = rpiList.getRpiCountForDaysSinceEpochLocalTZ(daysSinceEpochLocalTZ);
                //Log.d(TAG, "Datapoint: " + daysSinceEpochLocalTZ + ": " + numEntries);
                dataPoints1.add(new BarEntry(daysSinceEpochLocalTZ, numEntries));
                count += numEntries;
            }

            // set date label formatter
            String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
            DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            // UTC because we don't want DateFormat to do additional time zone compensation

            minDate = new Date(getMillisFromDays(rpiListDaysSinceEpochLocalTZ.first()));
            String minDateStr = dateFormat.format(minDate);
            maxDate = new Date(getMillisFromDays(rpiListDaysSinceEpochLocalTZ.last()));
            String maxDateStr = dateFormat.format(maxDate);

            textViewRpis.setText(getString(R.string.title_rpis_extracted, count, minDateStr, maxDateStr));

            chartRpis.setData(dataPoints1, normalBarColor, "RPIs", false, this);
            chartRpis.setFormatAndRefresh(this);

        } else {  // getting the RPIs failed, e.g. because we didn't get root rights
            List<BarEntry> dataPoints1 = new ArrayList<>();
            long currentTimeMillis = System.currentTimeMillis();
            int currentTimestampLocalTZ = (int) (currentTimeMillis / 1000) + timeZoneOffsetSeconds;
            int daysSinceEpochLocalTZ = currentTimestampLocalTZ / (3600*24);
            for (int day = daysSinceEpochLocalTZ-13; day <= daysSinceEpochLocalTZ; day++) {
                dataPoints1.add(new BarEntry(day, 0));
            }
            chartRpis.setData(dataPoints1, normalBarColor, "RPIs", false, this);
            chartRpis.setFormatAndRefresh(this);
            showExtractionError();
            showMatchingNotPossible();
        }

        // 2nd Section: Diagnosis Keys

        textViewDks.setText(getString(R.string.title_diagnosis_keys_downloading, CWCApplication.getFlagsString(context)));
        if (CWCApplication.appMode == NORMAL_MODE || CWCApplication.appMode == RAMBLE_MODE) {
            RequestQueue queue = Volley.newRequestQueue(this);
            List<DKDownloadCountry> dkDownloadCountries = new ArrayList<>();

            if (CWCApplication.downloadKeysFromAustria) dkDownloadCountries.add(new DKDownloadAustria());
            if (CWCApplication.downloadKeysFromGermany) dkDownloadCountries.add(new DKDownloadGermany());
            if (CWCApplication.downloadKeysFromPoland) dkDownloadCountries.add(new DKDownloadPoland());
            if (CWCApplication.downloadKeysFromSwitzerland) dkDownloadCountries.add(new DKDownloadSwitzerland());

            //noinspection ResultOfMethodCallIgnored
            DKDownloadUtils.getDKsForCountries(context, queue, minDate, dkDownloadCountries)
                    .subscribe(this::processDownloadedDiagnosisKeys, error -> {
                        Log.e(TAG, "Error downloading diagnosis keys: " + error);
                        showDownloadError();
                        showMatchingNotPossible();
                    });
        } else if (CWCApplication.appMode == DEMO_MODE) {
            try {
                InputStream inputStream = getAssets().open("demo_dks.zip");
                byte[] buffer = new byte[100000];
                int bytesRead;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                processDownloadedDiagnosisKeys(DKDownloadUtils.parseBytesToTeks(context, output.toByteArray()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalStateException();
        }
    }

    private void showExtractionError() {
        if (CWCApplication.appMode == NORMAL_MODE) {
            textViewExtractionError.setText(R.string.error_no_rpis_normal_mode);
        } else if (CWCApplication.appMode == RAMBLE_MODE) {
            textViewExtractionError.setText(R.string.error_no_rpis_ramble_mode);
        } else {
            throw new IllegalStateException();
        }
        textViewExtractionError.setBackgroundColor(resolveColorAttr(android.R.attr.colorBackground, context));
        textViewRpis.setText(getString(R.string.title_no_rpis_extracted));
        chartRpis.switchPleaseWaitAnimationOff();
    }

    private void showDownloadError() {
        textViewDownloadError.setText(R.string.error_download);
        textViewDownloadError.setBackgroundColor(resolveColorAttr(android.R.attr.colorBackground, context));
        textViewDks.setText(getString(R.string.title_diagnosis_keys_download_failed));
        chartDks.switchPleaseWaitAnimationOff();
    }

    private void showMatchingNotPossible() {
        textViewMatches.setText(getString(R.string.title_matching_not_possible));
        chartMatches.switchPleaseWaitAnimationOff();
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0 && permissions.length == 1 &&
                permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            recreateMainActivityOnNextPossibleOccasion();
        }
    }


    private void processDownloadedDiagnosisKeys(List<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList) {
        // Count the downloaded Diagnosis Keys
        Log.d(TAG, "Number of keys that have been downloaded: " + diagnosisKeysList.size());

        TreeMap<Integer, Integer> diagnosisKeyCountMap = new TreeMap<>();  // Key: ENIN (==date), Value: count
        int minENIN = getENINFromDate(minDate);
        int maxENIN = getENINFromDate(maxDate);
        for (int ENIN = minENIN; ENIN <= maxENIN; ENIN += standardRollingPeriod) {
            diagnosisKeyCountMap.put(ENIN, 0);
        }
        int count = 0;
        for (DiagnosisKeysProtos.TemporaryExposureKey diagnosisKeyEntry : diagnosisKeysList) {
            int ENIN = diagnosisKeyEntry.getRollingStartIntervalNumber();
            Integer bin = diagnosisKeyCountMap.floorKey(ENIN);
            if (bin != null) {
                Integer binCount = diagnosisKeyCountMap.get(bin);
                if (binCount != null) {
                    binCount++;
                    diagnosisKeyCountMap.put(bin, binCount);
                }
                count++;
            }
        }

        textViewDks.setText(getString(R.string.title_diagnosis_keys_downloaded, count, CWCApplication.getFlagsString(context)));

        List<BarEntry> dataPoints2 = new ArrayList<>();

        for (Integer ENIN : diagnosisKeyCountMap.keySet()) {
            Integer numEntriesInteger = diagnosisKeyCountMap.get(ENIN);
            int numEntries = 0;
            if (numEntriesInteger != null) {
                numEntries = numEntriesInteger;
            }
            //Log.d(TAG, "Datapoint: " + ENIN + ": " + numEntries);
            dataPoints2.add(new BarEntry(getDaysSinceEpochFromENIN(ENIN), numEntries));
        }

        chartDks.setData(dataPoints2, normalBarColor,"DKs", false, this);
        chartDks.setFormatAndRefresh(this);

        if ((rpiList != null) && (!rpiList.isEmpty()) && (count > 0)) {
            textViewMatches.setText(getString(R.string.title_matching_not_done_yet));
            startMatching(diagnosisKeysList);
        } else {
            showMatchingNotPossible();
        }
    }

    public Handler uiThreadHandler;
    public HandlerThread backgroundMatcher;

    private void startMatching(List<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList) {
        backgroundThreadsRunning = true;  // required so that DEMO_MODE toggle can safely stop the background threads
        backgroundThreadsShouldStop = false;

        uiThreadHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@SuppressWarnings("NullableProblems") Message inputMessage) {
                Log.d(TAG, "Message received: Matching finished.");
                presentMatchResults();
            }
        };

        backgroundMatcher = new HandlerThread("BackgroundMatcher");
        backgroundMatcher.start();
        Handler backgroundThreadHandler = new Handler(backgroundMatcher.getLooper());
        backgroundThreadHandler.post(new BackgroundMatching(this, diagnosisKeysList));
    }

    private class BackgroundMatching implements Runnable {
        private final MainActivity mainActivity;
        private final List<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList;

        BackgroundMatching(MainActivity theMainActivity,
                           List<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList) {
            mainActivity = theMainActivity;
            this.diagnosisKeysList = diagnosisKeysList;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);

            if ((rpiList != null) && (diagnosisKeysList.size() != 0)) {
                MatchEntryContent matchEntryContent = new MatchEntryContent();
                Matcher matcher = new Matcher(rpiList, diagnosisKeysList, matchEntryContent);
                matcher.findMatches(
                        progress -> runOnUiThread(
                                () -> textViewMatches.setText(getResources().getString(R.string.
                                        title_matching_not_done_yet_with_progress, progress.first, progress.second))));
                Log.d(TAG, "Finished matching, sending the message...");
                CWCApplication.setMatchEntryContent(matchEntryContent);
            }
            backgroundThreadsRunning = false;
            backgroundThreadsShouldStop = false;
            Message completeMessage = mainActivity.uiThreadHandler.obtainMessage();
            completeMessage.sendToTarget();
        }
    }

    private void presentMatchResults() {
        MatchEntryContent matchEntryContent = CWCApplication.getMatchEntryContent();
        if ((rpiList != null) && (matchEntryContent != null)) {
            int numberOfMatches = 0;
            if (matchEntryContent.matchEntries != null) {
                numberOfMatches = matchEntryContent.matchEntries.getTotalMatchingDkCount();
            }
            Resources res = getResources();
            if (numberOfMatches > 0) {
                textViewMatches.setText(res.getQuantityString(R.plurals.title_number_of_matches_found, numberOfMatches, numberOfMatches));
                textViewMatches.setTextColor(matchBarColor);
            } else {
                textViewMatches.setText(R.string.title_no_matches_found);
            }
            Log.d(TAG, "Number of matches: " + numberOfMatches);

            List<BarEntry> dataPoints3 = new ArrayList<>();
            SortedSet<Integer> rpiListDaysSinceEpochLocalTZ = rpiList.getAvailableDaysSinceEpochLocalTZ();
            int total = 0;
            for (Integer daysSinceEpochLocalTZ : rpiListDaysSinceEpochLocalTZ) {
                int dailyCount = 0;
                if (matchEntryContent.matchEntries != null) {
                    MatchEntryContent.DailyMatchEntries dailyMatchEntries = matchEntryContent.matchEntries.getDailyMatchEntries(daysSinceEpochLocalTZ);
                    if (dailyMatchEntries != null) {
                        dailyCount = dailyMatchEntries.getDailyMatchingDkCount();
                    }
                }
                //Log.d(TAG, "Datapoint: " + daysSinceEpochLocalTZ + ": " + count);
                dataPoints3.add(new BarEntry(daysSinceEpochLocalTZ, dailyCount));
                total += dailyCount;
            }
            Log.d(TAG, "Number of matches displayed: " + total);

            chartMatches.setData(dataPoints3, matchBarColor, "Matches", true, this);
            chartMatches.setFormatAndRefresh(this);

            // End of this path.
            // From now on, the user can scroll the charts,
            // or tap on a match to reach the DisplayDetailsActivity.

            if (mainActivityShouldBeRecreated) {
                recreateMainActivityNow();
            }
        } else {
            showMatchingNotPossible();
        }
    }

    // global variables
    protected static Entry entry;
    protected static Highlight highlight;

    class Chart3ValueSelectedListener implements OnChartValueSelectedListener {
        @Override
        public void onValueSelected(Entry e, Highlight h) {
            // set global variables
            entry = e;
            highlight = h;

            if (e == null)
                return;
            int y = (int) e.getY();
            if (y > 0) {
                int x = (int)e.getX();
                Log.d(TAG, "Detected selection "+x+" ("+y+")");
                Intent intent = new Intent(getApplicationContext(), DisplayDetailsActivity.class);
                intent.putExtra(EXTRA_MESSAGE_DAY, String.valueOf(x));
                intent.putExtra(EXTRA_MESSAGE_COUNT, String.valueOf(y));
                startActivity(intent);
                chartMatches.getBarChart().highlightValues(null);
            }
        }

        @Override
        public void onNothingSelected() {
            onValueSelected(entry, highlight);
        }
    }
}