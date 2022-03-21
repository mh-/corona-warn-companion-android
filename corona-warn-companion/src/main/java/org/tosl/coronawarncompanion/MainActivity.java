/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020-2022 Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
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

import org.osmdroid.config.Configuration;

import org.tosl.coronawarncompanion.barcharts.BarChartSync;
import org.tosl.coronawarncompanion.barcharts.CwcBarChart;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKey;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadCountry;
import org.tosl.coronawarncompanion.dkdownload.DKDownloadUtils;
import org.tosl.coronawarncompanion.gmsreadout.ContactDbOnDisk;
import org.tosl.coronawarncompanion.microgreadout.CctgDbOnDisk;
import org.tosl.coronawarncompanion.ramblereadout.RambleDbOnDisk;
import org.tosl.coronawarncompanion.microgreadout.MicroGDbOnDisk;
import org.tosl.coronawarncompanion.rpis.RpiList;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

import static org.tosl.coronawarncompanion.CWCApplication.sharedPreferences;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.CCTG_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.DEMO_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.NORMAL_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.RAMBLE_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.AppModeOptions.MICROG_MODE;
import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsShouldStop;
import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsRunning;
import static org.tosl.coronawarncompanion.CWCApplication.numDownloadDays;
import static org.tosl.coronawarncompanion.CWCApplication.minNumDownloadDays;
import static org.tosl.coronawarncompanion.CWCApplication.maxNumDownloadDays;
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
    public static final int INTENT_PICK_RAMBLE_FILE = 1;
    static boolean mainActivityShouldBeRecreated = false;
    private static CWCApplication.AppModeOptions desiredAppMode;
    private RpiList rpiList = null;
    private Date maxDate = null;
    private Date minDate = null;
    private int numMatchingThreads;
    DisposableObserver<Matcher.ProgressAndMatchEntryAndDkAndDay> mergedObserver;

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

    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

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
        } if (CWCApplication.appMode == MICROG_MODE) {
            menu.findItem(R.id.microgmode).setChecked(true);
        } if (CWCApplication.appMode == CCTG_MODE) {
            menu.findItem(R.id.cctgmode).setChecked(true);
        }

        for (Country country : Country.values()) {
            if (country.isDownloadKeysFrom()) {
                menu.findItem(country.getId()).setChecked(true);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        List<Integer> countryIds = new ArrayList<>();
        for (Country country : Country.values()) {
            countryIds.add(country.getId());
        }
        if (item.getItemId() == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (item.getItemId() == R.id.set_num_download_days) {
            startActivity(new Intent(this, SetNumberOfDownloadDaysActivity.class));
            return true;
        } else if (item.getItemId() == R.id.normalmode || item.getItemId() == R.id.demomode ||
                item.getItemId() == R.id.ramblemode || item.getItemId() == R.id.microgmode ||
                item.getItemId() == R.id.cctgmode) {
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
            } else if (item.getItemId() == R.id.microgmode) {
                desiredAppMode = MICROG_MODE;
            } else if (item.getItemId() == R.id.cctgmode) {
                desiredAppMode = CCTG_MODE;
            } else {
                desiredAppMode = RAMBLE_MODE;
            }
            if (desiredAppMode != CWCApplication.appMode) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(getString(R.string.saved_app_mode), desiredAppMode.ordinal());
                editor.apply();
            }
            recreateMainActivityOnNextPossibleOccasion();
            return true;
        } else if (item.getItemId() == R.id.osslicenses) {
            startActivity(new Intent(this, DisplayLicensesActivity.class));
            return true;
        } else if (countryIds.contains(item.getItemId())) {
            for (Country country : Country.values()) {
                if (item.getItemId() == country.getId()) {
                    boolean desiredNewState = !country.isDownloadKeysFrom();
                    //noinspection PointlessBooleanExpression
                    if (desiredNewState==true || CWCApplication.getNumberOfActiveCountries() > 1) {
                        item.setChecked(desiredNewState);
                        country.setDownloadKeysFrom(desiredNewState);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(country.getCode(context), desiredNewState);
                        editor.apply();
                        recreateMainActivityOnNextPossibleOccasion();
                        return true;
                    }
                    return false;
                }
            }
            return false;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void recreateMainActivityOnNextPossibleOccasion() {
        if (!backgroundThreadsRunning) {  // recreate() only while background threads are not running
            recreateMainActivityNow();
        } else {
            mainActivityShouldBeRecreated = true;
            backgroundThreadsShouldStop = true;
        }
    }

    private void recreateMainActivityNow() {
        mainActivityShouldBeRecreated = false;
        CWCApplication.appMode = desiredAppMode;
        recreate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // We have received an intent. This is likely a microG database.
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("application/vnd.microg.exposure+sqlite3".equals(type)) {
                // Since loading of DB currently happens in onCreate(), we simply recreate here.
                // Current intent gets 'saved' and passed to onCreate()
                setIntent(intent);

                Log.d(TAG, "Got microG Database Send intent while running! Recreating.");
                recreate();
                return;
            }
        }
        Log.d(TAG, "Got some unknown intent while running! Ignoring.");
    }

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;

        sharedPreferences = this.getPreferences(MODE_PRIVATE);

        // configure osmdroid
        Configuration.getInstance().load(context, sharedPreferences);

        // get App Mode from SharedPreferences
        int appModeOrdinal = sharedPreferences.getInt(getString(R.string.saved_app_mode), NORMAL_MODE.ordinal());
        try {
            CWCApplication.appMode = CWCApplication.AppModeOptions.values()[appModeOrdinal];
        } catch (ArrayIndexOutOfBoundsException e) {
            CWCApplication.appMode = NORMAL_MODE;
        }
        desiredAppMode = CWCApplication.appMode;

        // get Number Of Download Days from SharedPreferences
        numDownloadDays = sharedPreferences.getInt(getString(R.string.saved_num_download_days), 10);
        int previousNumDownloadDays = numDownloadDays;
        if (numDownloadDays > maxNumDownloadDays) numDownloadDays = maxNumDownloadDays;
        if (numDownloadDays < minNumDownloadDays) numDownloadDays = minNumDownloadDays;
        if (previousNumDownloadDays != numDownloadDays) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(getString(R.string.saved_num_download_days), numDownloadDays);
            editor.apply();
        }

        // If the app was opened with a Send intent, parse the database-uri and use it as a microG database
        // instead of using su to copy it from the gms directory
        File databaseFile = null;
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("application/vnd.microg.exposure+sqlite3".equals(type)) {
                // We got a database uri shared with us. Use this one instead of the usual one!
                Uri databaseUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                // clear intent, because onCreate() might get called again
                intent.removeExtra(Intent.EXTRA_STREAM);
                if (databaseUri != null) {
                    Log.d(TAG, "Got database URI via send activity: " + databaseUri.toString());
                    try {
                        // We only get a file descriptor from the uri, so we write it to a local cache file.
                        // Necessary since SQLite wants a file it can open.
                        ParcelFileDescriptor inputPFD = getContentResolver().openFileDescriptor(databaseUri, "r");
                        FileDescriptor fd = inputPFD.getFileDescriptor();
                        databaseFile = File.createTempFile("database.db", null, this.getCacheDir());
                        FileInputStream in = new FileInputStream(fd);
                        FileOutputStream out = new FileOutputStream(databaseFile);
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }

                        // Force switch app to microG mode
                        if (desiredAppMode != MICROG_MODE) {
                            Log.d(TAG, "Force switch to microG Mode!");
                            CharSequence text = getString(R.string.toast_switching_to_microg_mode);
                            Toast.makeText(this.context, text, Toast.LENGTH_LONG).show();

                            desiredAppMode = MICROG_MODE;
                            CWCApplication.appMode = MICROG_MODE;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not open database send via intent! " + e.toString());
                        if (databaseFile != null) {
                            //noinspection ResultOfMethodCallIgnored
                            databaseFile.delete();
                        }
                    }
                }
            }
        }

        // get the active countries from SharedPreferences
        for (Country country : Country.values()) {
            country.setDownloadKeysFrom(sharedPreferences.getBoolean(country.getCode(context), false));
        }
        if (CWCApplication.getNumberOfActiveCountries() < 1) {
            Country.Germany.setDownloadKeysFrom(true);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(Country.Germany.getCode(context), true);
            editor.apply();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (CWCApplication.appMode == NORMAL_MODE) {
                actionBar.setTitle(R.string.title_activity_main);
            } else if (CWCApplication.appMode == DEMO_MODE) {
                actionBar.setTitle(getString(R.string.title_activity_main_demo_prefix) + getString(R.string.title_activity_main));
            } else if (CWCApplication.appMode == RAMBLE_MODE) {
                actionBar.setTitle(getString(R.string.title_activity_main_ramble_version));
            } else if (CWCApplication.appMode == MICROG_MODE) {
                actionBar.setTitle(getString(R.string.title_activity_main_microg_version));
            } else if (CWCApplication.appMode == CCTG_MODE) {
                actionBar.setTitle(getString(R.string.title_activity_main_cctg_version));
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
            continueWhenRpisAreAvailable();
        } else if (CWCApplication.appMode == RAMBLE_MODE) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");  // this is the MIME type of a RaMBLE SQLITE export
            startActivityForResult(intent, INTENT_PICK_RAMBLE_FILE);
        } else if (CWCApplication.appMode == MICROG_MODE) {
            MicroGDbOnDisk microGDbOnDisk = new MicroGDbOnDisk(this);
            rpiList = microGDbOnDisk.getRpisFromContactDB(this, databaseFile);
            continueWhenRpisAreAvailable();
        } else if (CWCApplication.appMode == CCTG_MODE) {
            CctgDbOnDisk cctgDbOnDisk = new CctgDbOnDisk(this);
            rpiList = cctgDbOnDisk.getRpisFromContactDB(this);
            continueWhenRpisAreAvailable();
        } else {
            throw new IllegalStateException();
        }
    }

    @SuppressLint("CheckResult")
    private void continueWhenRpisAreAvailable() {
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
            int timeZoneOffsetSeconds = CWCApplication.getTimeZoneOffsetSeconds();
            Log.d(TAG, "Local TimeZone Offset in seconds: "+ timeZoneOffsetSeconds);
            int currentTimestampLocalTZ = (int) (currentTimeMillis / 1000) + timeZoneOffsetSeconds;
            int daysSinceEpochLocalTZ = currentTimestampLocalTZ / (3600*24);
            for (int day = daysSinceEpochLocalTZ-13; day <= daysSinceEpochLocalTZ; day++) {
                dataPoints1.add(new BarEntry(day, 0));
            }
            chartRpis.setData(dataPoints1, normalBarColor, "RPIs", false, this);
            chartRpis.setFormatAndRefresh(this);
            showExtractionError(rpiList);
            showMatchingNotPossible();
        }

        // 2nd Section: Diagnosis Keys

        Resources res = getResources();
        textViewDks.setText(res.getQuantityString(R.plurals.title_diagnosis_keys_downloading, numDownloadDays, numDownloadDays, CWCApplication.getFlagsString(context)));
        if (CWCApplication.appMode == NORMAL_MODE || CWCApplication.appMode == RAMBLE_MODE ||
                CWCApplication.appMode == MICROG_MODE || CWCApplication.appMode == CCTG_MODE) {
            List<DKDownloadCountry> dkDownloadCountries = new ArrayList<>();

            for (Country country : Country.values()) {
                if (country.isDownloadKeysFrom()) {
                    dkDownloadCountries.add(country.getDkDownloadCountry());
                }
            }

            //noinspection ResultOfMethodCallIgnored
            DKDownloadUtils.getDKsForCountries(context, OK_HTTP_CLIENT, minDate, numDownloadDays, dkDownloadCountries)
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
                processDownloadedDiagnosisKeys(DKDownloadUtils.parseBytesToTeks(context, output.toByteArray(),
                        getString(R.string.country_code_germany)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == INTENT_PICK_RAMBLE_FILE
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                // Perform operations on the document using its URI.
                RambleDbOnDisk rambleDbOnDisk = new RambleDbOnDisk(this, uri);
                // limit RaMBLE encounters to the last 14 days
                rpiList = rambleDbOnDisk.getRpisFromContactDB(
                        getDaysFromMillis(System.currentTimeMillis()) - 14);
                continueWhenRpisAreAvailable();
            }
        }
    }

    private void showExtractionError(RpiList rpiList) {
        if ((CWCApplication.appMode == NORMAL_MODE) || (CWCApplication.appMode == MICROG_MODE) || (CWCApplication.appMode == CCTG_MODE)) {
            if (rpiList == null) {
                textViewExtractionError.setText(R.string.error_no_rpis_normal_mode);
            } else {
                textViewExtractionError.setText(R.string.error_empty_rpis_normal_mode);
            }
        } else if (CWCApplication.appMode == RAMBLE_MODE) {
            if (rpiList == null) {
                textViewExtractionError.setText(R.string.error_no_rpis_ramble_mode);
            } else {
                textViewExtractionError.setText(R.string.error_empty_rpis_ramble_mode);
            }
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

    private void showMatchingError(Throwable e) {
        chartMatches.switchPleaseWaitAnimationOff();
        textViewMatches.setText(getString(R.string.title_matching_error, e.toString()));
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        Toast toast = Toast.makeText(getApplicationContext(), stackTrace, Toast.LENGTH_LONG);
        toast.show();
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && permissions.length == 1 &&
                permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreateMainActivityOnNextPossibleOccasion();
        }
    }


    private void processDownloadedDiagnosisKeys(List<DiagnosisKey> diagnosisKeysList) {
        // Count the downloaded Diagnosis Keys
        Log.d(TAG, "Number of keys that have been downloaded: " + diagnosisKeysList.size());

        TreeMap<Integer, Integer> diagnosisKeyCountMap = new TreeMap<>();  // Key: ENIN (==date), Value: count
        int minENIN = getENINFromDate(minDate);
        int maxENIN = getENINFromDate(maxDate);
        for (int ENIN = minENIN; ENIN <= maxENIN; ENIN += standardRollingPeriod) {
            diagnosisKeyCountMap.put(ENIN, 0);
        }
        int count = 0;
        for (DiagnosisKey diagnosisKeyEntry : diagnosisKeysList) {
            int ENIN = diagnosisKeyEntry.dk.getRollingStartIntervalNumber();
            Integer bin = diagnosisKeyCountMap.floorKey(ENIN);
            if ((bin != null) && (ENIN <= (bin + standardRollingPeriod))) {
                Integer binCount = diagnosisKeyCountMap.get(bin);
                if (binCount != null) {
                    binCount++;
                    diagnosisKeyCountMap.put(bin, binCount);
                }
                count++;
            }
        }

        Resources res = getResources();
        StringBuilder sb = new StringBuilder();
        sb.append(res.getQuantityString(R.plurals.title_diagnosis_keys_downloaded, numDownloadDays, count, numDownloadDays, CWCApplication.getFlagsString(context)));

        int errorCount = DKDownloadUtils.getErrorCount();
        if (errorCount != 0) {
            sb.append(" ");
            sb.append(getResources().getQuantityString(R.plurals.title_diagnosis_keys_downloaded_warning,
                    errorCount, errorCount));
        }

        textViewDks.setText(sb.toString());

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

    private void startMatching(List<DiagnosisKey> diagnosisKeysList) {
        backgroundThreadsRunning = true;  // required so that DEMO_MODE toggle can safely stop the background threads
        backgroundThreadsShouldStop = false;

        DisposableObserver<Matcher.ProgressAndMatchEntryAndDkAndDay> matchingObserver =
                new DisposableObserver<Matcher.ProgressAndMatchEntryAndDkAndDay>() {
            private MatchEntryContent matchEntryContent;
            private int numMatches = 0;
            private int[] progressArray;

            @Override
            protected void onStart() {
                matchEntryContent = new MatchEntryContent();
                progressArray = new int[numMatchingThreads];
            }

            @Override
            public void onNext(@io.reactivex.annotations.NonNull Matcher.ProgressAndMatchEntryAndDkAndDay progressAndMatchEntryAndDkAndDay) {
                // if available, add match
                if (progressAndMatchEntryAndDkAndDay.matchEntryAndDkAndDay != null) {
                    matchEntryContent.matchEntries.add(progressAndMatchEntryAndDkAndDay.matchEntryAndDkAndDay);
                    numMatches = matchEntryContent.matchEntries.getTotalMatchingDkCount();
                }
                // update progress
                progressArray[progressAndMatchEntryAndDkAndDay.threadNumber] = progressAndMatchEntryAndDkAndDay.currentProgress;
                int progressSum = 0;
                for (int i = 0; i < numMatchingThreads; i++) {
                    progressSum += progressArray[i];
                }
                textViewMatches.setText(getResources().getString(R.string.title_matching_not_done_yet_with_progress,
                        progressSum / numMatchingThreads, numMatches));
            }

            @Override
            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                Log.e(TAG, "ERROR during matching!", e);
                showMatchingError(e);
            }

            @Override
            public void onComplete() {
                CWCApplication.setMatchEntryContent(matchEntryContent);
                CWCApplication.setLocationDataAvailable(rpiList.getHaveLocation());
                Log.d(TAG, "Matching finished.");
                backgroundThreadsRunning = false;
                backgroundThreadsShouldStop = false;
                presentMatchResults();
            }
        };

        int dkListLen = diagnosisKeysList.size();
        if ((rpiList != null) && (dkListLen != 0)) {
            int desiredThreads = Runtime.getRuntime().availableProcessors();
            if (desiredThreads < 1) {
                desiredThreads = 1;
            }
            Log.d(TAG, "Matching: Trying to split into " + desiredThreads + " threads.");
            if (desiredThreads > dkListLen) {
                desiredThreads = dkListLen;
                Log.d(TAG, "Matching: Reduced to " + desiredThreads + " threads, because of short list");
            }
            ArrayList<Pair<Integer, Integer>> ranges = new ArrayList<>();
            int lastEndExclusive = 0;
            int newEndExclusive = 0;
            int i = 1;
            while (newEndExclusive < dkListLen) {
                newEndExclusive = dkListLen * i / desiredThreads;
                if (newEndExclusive < lastEndExclusive) {
                    newEndExclusive = lastEndExclusive;
                }
                if (newEndExclusive > dkListLen) {
                    newEndExclusive = dkListLen;
                }
                if (newEndExclusive > lastEndExclusive) {
                    ranges.add(new Pair<>(lastEndExclusive, newEndExclusive));
                    Log.d(TAG, "Matching: Range " + lastEndExclusive + ".." + newEndExclusive);
                }
                lastEndExclusive = newEndExclusive;
                i++;
            }
            numMatchingThreads = ranges.size();
            
            ArrayList<Observable<Matcher.ProgressAndMatchEntryAndDkAndDay>> observables = new ArrayList<>();
            for (int threadNum = 0; threadNum < numMatchingThreads; threadNum++) {
                Matcher matcher = new Matcher(rpiList,
                        diagnosisKeysList.subList(ranges.get(threadNum).first, ranges.get(threadNum).second), threadNum);
                observables.add(
                        matcher.getMatchingObservable()
                                .subscribeOn(Schedulers.computation())
                                .observeOn(AndroidSchedulers.mainThread())
                );
            }
            Observable<Matcher.ProgressAndMatchEntryAndDkAndDay> mergedObservable =
                    Observable.merge(observables);
            mergedObserver = mergedObservable.subscribeWith(matchingObserver);
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

            // now we don't need the RPI list anymore, Garbage Collector may release the memory:
            rpiList = null;

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