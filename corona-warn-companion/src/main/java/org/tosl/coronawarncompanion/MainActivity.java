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

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
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

import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import org.tosl.coronawarncompanion.barcharts.BarChartSync;
import org.tosl.coronawarncompanion.barcharts.CwcBarChart;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysImport;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.dkdownload.DKDownload;
import org.tosl.coronawarncompanion.gmsreadout.ContactDbOnDisk;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;

import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsShouldStop;
import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsRunning;
import static org.tosl.coronawarncompanion.dkdownload.Unzip.getUnzippedBytesFromZipFileBytes;
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
    private static boolean demoModeShouldToggle = false;
    private RpiList rpiList = null;
    private final long todayLastMidnightInMillis = getMillisFromDays(getDaysFromMillis(System.currentTimeMillis()));
    private Date maxDate = new Date(todayLastMidnightInMillis);
    private Date minDate = new Date(todayLastMidnightInMillis - getMillisFromDays(14));
    private Date currentDate;  // usually the same as maxDate

    private DKDownload diagnosisKeysDownload;
    private final LinkedList<URL> diagnosisKeysUrls = new LinkedList<>();
    private final ArrayList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList = new ArrayList<>();
    private final int normalBarColor = Color.parseColor("#8CEAFF");
    private final int matchBarColor = Color.parseColor("red");

    private BarChartSync barChartSync;
    private CwcBarChart chart1;
    private CwcBarChart chart2;
    private CwcBarChart chart3;
    private TextView textView1;
    private TextView textView2;
    private TextView textView3;
    private TextView textViewError;
    private Context context;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.demomode:
                if (backgroundThreadsShouldStop) {
                    // user has to wait a little bit longer
                    CharSequence text = getString(R.string.error_demo_mode_switching_not_possible);
                    Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    return false;
                }
                if (backgroundThreadsRunning) {  // don't do recreate() while background threads are running
                    demoModeShouldToggle = true;
                    backgroundThreadsShouldStop = true;
                } else {
                    toggleDemoMode();
                }
                return true;
            case R.id.osslicenses:
                startActivity(new Intent(this, DisplayLicensesActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void toggleDemoMode() {
        demoModeShouldToggle = false;
        CWCApplication.DEMO_MODE = !CWCApplication.DEMO_MODE;
        recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;

        boolean DEMO_MODE = CWCApplication.DEMO_MODE;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (!DEMO_MODE) {
                actionBar.setTitle(R.string.title_activity_main);
            } else {
                actionBar.setTitle(getString(R.string.title_activity_main_demo_prefix)+
                        getString(R.string.title_activity_main));
            }
        }

        if (DEMO_MODE) {
            Log.i(TAG, "--- DEMO MODE ---");
        }

        if(backgroundThreadsRunning) {
            // reCreate() was called, e.g. by switching from portrait to landscape, etc.
            backgroundThreadsShouldStop = true;
            while(backgroundThreadsRunning) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        int timeZoneOffsetSeconds = CWCApplication.getTimeZoneOffsetSeconds();
        Log.d(TAG, "Local TimeZone Offset in seconds: "+ timeZoneOffsetSeconds);

        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);
        textView3 = findViewById(R.id.textView3);
        textViewError = findViewById(R.id.textViewError);
        barChartSync = new BarChartSync();
        chart1 = new CwcBarChart(findViewById(R.id.chart1), findViewById(R.id.progressBar1), barChartSync, this);
        chart2 = new CwcBarChart(findViewById(R.id.chart2), findViewById(R.id.progressBar2), barChartSync, this);
        chart3 = new CwcBarChart(findViewById(R.id.chart3), findViewById(R.id.progressBar3), barChartSync, this);
        chart3.getBarChart().setOnChartValueSelectedListener(new Chart3ValueSelectedListener());

        // 1st Section: Get RPIs from database (requires root)

        ContactDbOnDisk contactDbOnDisk = new ContactDbOnDisk(this);
        rpiList = contactDbOnDisk.getRpisFromContactDB(DEMO_MODE);

        if (rpiList != null) {  // check that getting the RPIs didn't fail, e.g. because we didn't get root rights
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

            textView1.setText(getString(R.string.title_rpis_extracted, count, minDateStr, maxDateStr));

            chart1.setData(dataPoints1, normalBarColor, "RPIs", false, this);
            chart1.setFormatAndRefresh(this);


            // 2nd Section: Diagnosis Keys

            if (!DEMO_MODE) {
                diagnosisKeysDownload = new DKDownload(this);
                diagnosisKeysDownload.availableDatesRequest(new availableDatesResponseCallbackCommand(),
                        new errorResponseCallbackCommand());
                // (the rest is done asynchronously in callback functions)
            } else {
                try {
                    InputStream inputStream = getAssets().open("demo_dks.zip");
                    byte[] buffer = new byte[100000];
                    int bytesRead;
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    DKDownload.FileResponse response = new DKDownload.FileResponse();
                    response.url = new URL("https://tosl.org/demo_dks.zip");
                    response.fileBytes = output.toByteArray();

                    new processUrlListCallbackCommand().execute(response);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {  // getting the RPIs failed, e.g. because we didn't get root rights
            List<BarEntry> dataPoints1 = new ArrayList<>();
            long currentTimeMillis = System.currentTimeMillis();
            int currentTimestampLocalTZ = (int) (currentTimeMillis / 1000) + timeZoneOffsetSeconds;
            int daysSinceEpochLocalTZ = currentTimestampLocalTZ / (3600*24);
            for (int day = daysSinceEpochLocalTZ-13; day <= daysSinceEpochLocalTZ; day++) {
                dataPoints1.add(new BarEntry(day, 0));
            }
            chart1.setData(dataPoints1, normalBarColor, "RPIs", false, this);
            chart1.setFormatAndRefresh(this);
            chart2.switchProgressBarOff();
            chart3.switchProgressBarOff();
            textViewError.setText(R.string.error_no_rpis);
            textViewError.setBackgroundColor(resolveColorAttr(android.R.attr.colorBackground, context));
        }
    }

    public class errorResponseCallbackCommand implements DKDownload.CallbackCommand {
        public void execute(Object data) {
            textViewError.setText(R.string.error_download);
            textViewError.setBackgroundColor(resolveColorAttr(android.R.attr.colorBackground, context));
        }
    }

    public class availableDatesResponseCallbackCommand implements DKDownload.CallbackCommand {
        public void execute(Object data) {
            // get Daily Diagnosis Keys URLs for the previous days
            @SuppressWarnings("unchecked") LinkedList<Date> availableDates = (LinkedList<Date>) data;
            for (Date date : availableDates) {
                if (date.compareTo(minDate) >= 0) {  // date >= minDate
                    diagnosisKeysUrls.add(diagnosisKeysDownload.getDailyDKsURLForDate(date));
                }
            }
            // get Hourly Diagnosis Keys URLs for the current day
            Calendar c = Calendar.getInstance();
            c.setTime(availableDates.getLast());
            c.add(Calendar.DATE, 1);
            currentDate = c.getTime();
            diagnosisKeysDownload.availableHoursForDateRequest(currentDate, new availableHoursResponseCallbackCommand());
        }
    }

    public class availableHoursResponseCallbackCommand implements DKDownload.CallbackCommand {
        public void execute(Object data) {
            // get Hourly Diagnosis Keys URLs for the current day
            @SuppressWarnings("unchecked") LinkedList<String> availableHours = (LinkedList<String>) data;
            for (String hour : availableHours) {
                diagnosisKeysUrls.add(diagnosisKeysDownload.getHourlyDKsURLForDateAndHour(currentDate, hour));
            }
            // Now we have all Diagnosis Keys URLs, let's process them
            processUrlList();
        }
    }

    private void processUrlList() {
        boolean atLeastOneDownloadStarted = false;
        for (URL url : diagnosisKeysUrls) {
            Log.d(TAG, "Going to download: " + url);
            diagnosisKeysDownload.dkFileRequest(url, new processUrlListCallbackCommand(),
                    new errorResponseCallbackCommand());
            atLeastOneDownloadStarted = true;
        }
        if (!atLeastOneDownloadStarted) {
            processDownloadedDiagnosisKeys();
        }
    }

    public class processUrlListCallbackCommand implements DKDownload.CallbackCommand {
        public void execute(Object data) {
            DKDownload.FileResponse fileResponse = (DKDownload.FileResponse) data;
            Log.d(TAG, "Download complete: " + fileResponse.url);

            // unzip the data
            byte[] exportDotBinBytes = {};
            try {
                exportDotBinBytes = getUnzippedBytesFromZipFileBytes(fileResponse.fileBytes, "export.bin");
            } catch (IOException e) {
                e.printStackTrace();
            }

            DiagnosisKeysImport diagnosisKeysImport = new DiagnosisKeysImport(exportDotBinBytes, getApplicationContext());
            List<DiagnosisKeysProtos.TemporaryExposureKey> dkList = diagnosisKeysImport.getDiagnosisKeys();
            if (dkList != null) {
                Log.d(TAG, "Number of keys in this file: " + dkList.size());
                diagnosisKeysList.addAll(dkList);
            }

            diagnosisKeysUrls.remove(fileResponse.url);
            Log.d(TAG, "Downloads left: " + diagnosisKeysUrls.size());
            if (diagnosisKeysUrls.size() == 0) {  // all files have been downloaded
                processDownloadedDiagnosisKeys();
            }
        }
    }

    private void processDownloadedDiagnosisKeys() {
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

        textView2.setText(getString(R.string.title_diagnosis_keys_downloaded, count));

        List<BarEntry> dataPoints2 = new ArrayList<>();

        for (Integer ENIN : diagnosisKeyCountMap.keySet()) {
            //noinspection ConstantConditions
            int numEntries = diagnosisKeyCountMap.get(ENIN);
            //Log.d(TAG, "Datapoint: " + ENIN + ": " + numEntries);
            dataPoints2.add(new BarEntry(getDaysSinceEpochFromENIN(ENIN), numEntries));
        }

        chart2.setData(dataPoints2, normalBarColor,"DKs", false, this);
        chart2.setFormatAndRefresh(this);

        textView3.setText(getString(R.string.title_matching_not_done_yet));
        startMatching();
    }

    public Handler uiThreadHandler;
    public HandlerThread backgroundMatcher;

    private void startMatching() {
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
        backgroundThreadHandler.post(new BackgroundMatching(this));
    }

    private class BackgroundMatching implements Runnable {
        private final MainActivity mainActivity;

        BackgroundMatching(MainActivity theMainActivity) {
            mainActivity = theMainActivity;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);

            if ((rpiList != null) && (diagnosisKeysList.size() != 0)) {
                MatchEntryContent matchEntryContent = new MatchEntryContent();
                Matcher matcher = new Matcher(rpiList, diagnosisKeysList, matchEntryContent);
                matcher.findMatches(
                        progress -> runOnUiThread(
                                () -> textView3.setText(getResources().getString(R.string.
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
        if ((rpiList != null) && (diagnosisKeysList != null)) {
            MatchEntryContent matchEntryContent = CWCApplication.getMatchEntryContent();
            int numberOfMatches = matchEntryContent.matchEntries.getTotalMatchingDkCount();
            Resources res = getResources();
            if (numberOfMatches > 0) {
                textView3.setText(res.getQuantityString(R.plurals.title_number_of_matches_found, numberOfMatches, numberOfMatches));
                textView3.setTextColor(matchBarColor);
            } else {
                textView3.setText(R.string.title_no_matches_found);
            }
            Log.d(TAG, "Number of matches: " + numberOfMatches);

            List<BarEntry> dataPoints3 = new ArrayList<>();
            SortedSet<Integer> rpiListDaysSinceEpochLocalTZ = rpiList.getAvailableDaysSinceEpochLocalTZ();
            int total = 0;
            for (Integer daysSinceEpochLocalTZ : rpiListDaysSinceEpochLocalTZ) {
                int dailyCount = 0;
                MatchEntryContent.DailyMatchEntries dailyMatchEntries = matchEntryContent.matchEntries.getDailyMatchEntries(daysSinceEpochLocalTZ);
                if (dailyMatchEntries != null) {
                    dailyCount = dailyMatchEntries.getDailyMatchingDkCount();
                }
                //Log.d(TAG, "Datapoint: " + daysSinceEpochLocalTZ + ": " + count);
                dataPoints3.add(new BarEntry(daysSinceEpochLocalTZ, dailyCount));
                total += dailyCount;
            }
            Log.d(TAG, "Number of matches displayed: " + total);

            chart3.setData(dataPoints3, matchBarColor, "Matches", true, this);
            chart3.setFormatAndRefresh(this);

            // End of this path.
            // From now on, the user can scroll the charts,
            // or tap on a match to reach the DisplayDetailsActivity.

            if (demoModeShouldToggle) {
                toggleDemoMode();
            }
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
                chart3.getBarChart().highlightValues(null);
            }
        }

        @Override
        public void onNothingSelected() {
            onValueSelected(entry, highlight);
        }
    }
}