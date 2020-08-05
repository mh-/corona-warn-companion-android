package org.tosl.coronawarncompanion;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

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

import static org.tosl.coronawarncompanion.dkdownload.Unzip.getUnzippedBytesFromZipFileBytes;
import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromMillis;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromDate;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromDays;
import static org.tosl.coronawarncompanion.tools.Utils.standardRollingPeriod;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String EXTRA_MESSAGE_DAY = "org.tosl.coronawarncompanion.DAY_MESSAGE";
    public static final String EXTRA_MESSAGE_COUNT = "org.tosl.coronawarncompanion.COUNT_MESSAGE";
    private static boolean DEMO_MODE;
    CWCApplication app = null;
    private int timeZoneOffsetSeconds;
    private RpiList rpiList = null;
    private final long todayLastMidnightInMillis = getMillisFromDays(getDaysFromMillis(System.currentTimeMillis()));
    private Date maxDate = new Date(todayLastMidnightInMillis);
    private Date minDate = new Date(todayLastMidnightInMillis - getMillisFromDays(14));
    private Date currentDate;  // usually the same as maxDate

    private DKDownload diagnosisKeysDownload;
    private final LinkedList<URL> diagnosisKeysUrls = new LinkedList<>();
    private ArrayList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList = null;

    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int matchBarColor = Color.parseColor("#FF0000");

    private BarChart chart1;
    private BarChart chart2;
    private BarChart chart3;
    private TextView textView1;
    private TextView textView2;
    private TextView textView3;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.demomode:
                CWCApplication.DEMO_MODE = !CWCApplication.DEMO_MODE;

                // recreate();

                // TODO!
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DEMO_MODE = CWCApplication.DEMO_MODE;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (!DEMO_MODE) {
                actionBar.setTitle("Corona Warn Companion");
            } else {
                actionBar.setTitle("Corona Warn Companion DEMO");
            }
        }

        if (DEMO_MODE) {
            Log.i(TAG, "--- DEMO MODE ---");
        }

        app = (CWCApplication) getApplicationContext();
        timeZoneOffsetSeconds = app.getTimeZoneOffsetSeconds();
        Log.d(TAG, "Local TimeZone Offset in seconds: "+ timeZoneOffsetSeconds);

        // 1st Section: Get RPIs from database (requires root)

        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);
        textView3 = findViewById(R.id.textView3);
        chart1 = findViewById(R.id.chart1);
        chart1.setNoDataText(getResources().getString(R.string.please_wait));
        chart1.setNoDataTextColor(Color.parseColor("black"));
        chart2 = findViewById(R.id.chart2);
        chart2.setNoDataText(getResources().getString(R.string.please_wait));
        chart2.setNoDataTextColor(Color.parseColor("black"));
        chart3 = findViewById(R.id.chart3);
        chart3.setNoDataText(getResources().getString(R.string.please_wait));
        chart3.setNoDataTextColor(Color.parseColor("black"));
        chart1.setOnChartGestureListener(new Chart1GestureListener());
        chart2.setOnChartGestureListener(new Chart2GestureListener());
        chart3.setOnChartGestureListener(new Chart3GestureListener());
        chart3.setOnChartValueSelectedListener(new Chart3ValueSelectedListener());

        rpiList = app.getRpiList();
        if (rpiList == null) {
            ContactDbOnDisk contactDbOnDisk = new ContactDbOnDisk();
            rpiList = contactDbOnDisk.getRpisFromContactDB(DEMO_MODE);
            app.setRpiList(rpiList);
        }

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

            textView1.setText(getString(R.string.rpis_extracted, count, minDateStr, maxDateStr));

            BarDataSet dataSet1 = new BarDataSet(dataPoints1, "RPIs"); // add entries to dataSet1
            dataSet1.setAxisDependency(YAxis.AxisDependency.LEFT);

            BarData barData1 = new BarData(dataSet1);
            dataSet1.setHighlightEnabled(false);
            chart1.setData(barData1);
            //chart1.setFitBars(true); // make the x-axis fit exactly all bars

            // the labels that should be drawn on the XAxis
            ValueFormatter xAxisFormatter1 = new ValueFormatter() {
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    return dateFormat.format(getDateFromDaysSinceEpoch((int) value));
                }
            };
            // the labels that should be drawn on the YAxis
            ValueFormatter yAxisFormatter1 = new ValueFormatter() {
                @SuppressLint("DefaultLocale")
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    return String.format("%5d", (int) value);
                }
            };
            // the bar labels
            ValueFormatter BarFormatter1 = new ValueFormatter() {
                @Override
                public String getBarLabel(BarEntry barEntry) {
                    return String.valueOf((int) barEntry.getY());
                }
            };
            barData1.setValueFormatter(BarFormatter1);
            XAxis xAxis = chart1.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setValueFormatter(xAxisFormatter1);
            xAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
            xAxis.setGranularityEnabled(true);
            xAxis.setDrawGridLines(false);

            YAxis yAxis = chart1.getAxisLeft();
            yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
            yAxis.setGranularityEnabled(true);
            yAxis.setAxisMinimum(0.0f);
            yAxis.setGridColor(gridColor);
            yAxis.setValueFormatter(yAxisFormatter1);

            chart1.getAxisRight().setAxisMinimum(0.0f);
            chart1.getAxisRight().setDrawLabels(false);
            chart1.getLegend().setEnabled(false);
            chart1.getDescription().setEnabled(false);
            chart1.setScaleYEnabled(false);
            chart1.getViewPortHandler().setMaximumScaleX(5.0f);
            chart1.invalidate(); // refresh
        }

        // 2nd Section: Diagnosis Keys

        if (!DEMO_MODE) {
            diagnosisKeysList = app.getDiagnosisKeysList();
            if (diagnosisKeysList == null) {
                diagnosisKeysDownload = new DKDownload();
                diagnosisKeysDownload.availableDatesRequest(new availableDatesResponseCallbackCommand());
                // (the rest is done asynchronously in callback functions)
            } else {
                processDownloadedDiagnosisKeys();
            }
        } else {
            try {
                InputStream inputStream = getAssets().open("demo_dks.zip");
                byte[] buffer = new byte[150000];
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
            // get Daily Diagnosis Keys URLs for the previous days
            @SuppressWarnings("unchecked") LinkedList<String> availableHours = (LinkedList<String>) data;
            for (String hour : availableHours) {
                diagnosisKeysUrls.add(diagnosisKeysDownload.getHourlyDKsURLForDateAndHour(currentDate, hour));
            }

            // Now we have all Diagnosis Keys URLs, let's process them
            processUrlList();
        }
    }

    private void processUrlList() {
        for (URL url : diagnosisKeysUrls) {
            Log.d(TAG, "Going to download: " + url);
            diagnosisKeysDownload.dkFileRequest(url, new processUrlListCallbackCommand());
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

            DiagnosisKeysImport diagnosisKeysImport = new DiagnosisKeysImport(exportDotBinBytes);

            if (diagnosisKeysList == null) diagnosisKeysList = new ArrayList<>();
            diagnosisKeysList.addAll(diagnosisKeysImport.getDiagnosisKeys());

            diagnosisKeysUrls.remove(fileResponse.url);
            Log.d(TAG, "Downloads left: " + diagnosisKeysUrls.size());
            if (diagnosisKeysUrls.size() == 0) {  // all files have been downloaded
                processDownloadedDiagnosisKeys();
            }
        }
    }

    private void processDownloadedDiagnosisKeys() {

        app.setDiagnosisKeysList(diagnosisKeysList);
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

        textView2.setText(getString(R.string.diagnosis_keys_downloaded, count));

        List<BarEntry> dataPoints2 = new ArrayList<>();

        for (Integer ENIN : diagnosisKeyCountMap.keySet()) {
            //noinspection ConstantConditions
            int numEntries = diagnosisKeyCountMap.get(ENIN);
            //Log.d(TAG, "Datapoint: " + ENIN + ": " + numEntries);
            dataPoints2.add(new BarEntry(getDaysSinceEpochFromENIN(ENIN), numEntries));
        }

        // set date label formatter
        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation

        BarDataSet dataSet2 = new BarDataSet(dataPoints2, "DKs"); // add entries to dataSet2
        dataSet2.setHighlightEnabled(false);
        dataSet2.setAxisDependency(YAxis.AxisDependency.LEFT);

        BarData barData2 = new BarData(dataSet2);
        chart2.setData(barData2);
        findViewById(R.id.progressBar2).setVisibility(View.GONE);
        //chart2.setFitBars(true); // make the x-axis fit exactly all bars

        // the labels that should be drawn on the XAxis
        ValueFormatter xAxisFormatter2 = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return dateFormat.format(getDateFromDaysSinceEpoch((int) value));
            }
        };
        // the labels that should be drawn on the YAxis
        ValueFormatter yAxisFormatter2 = new ValueFormatter() {
            @SuppressLint("DefaultLocale")
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.format("%5d", (int) value);
            }
        };
        // the bar labels
        ValueFormatter BarFormatter2 = new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return String.valueOf((int) barEntry.getY());
            }
        };
        barData2.setValueFormatter(BarFormatter2);
        XAxis xAxis = chart2.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(xAxisFormatter2);
        xAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);

        YAxis yAxis = chart2.getAxisLeft();
        yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        yAxis.setGranularityEnabled(true);
        yAxis.setAxisMinimum(0.0f);
        yAxis.setGridColor(gridColor);
        yAxis.setValueFormatter(yAxisFormatter2);

        chart2.getAxisRight().setAxisMinimum(0.0f);
        chart2.getAxisRight().setDrawLabels(false);
        chart2.getLegend().setEnabled(false);
        chart2.getDescription().setEnabled(false);
        chart2.setScaleYEnabled(false);
        chart2.getViewPortHandler().setMaximumScaleX(5.0f);
        chart2.invalidate(); // refresh

        textView3.setText(getString(R.string.matching_not_done_yet));
        startMatching();
    }

    public Handler uiThreadHandler;
    public HandlerThread backgroundMatcher;

    private void startMatching() {
        uiThreadHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@SuppressWarnings("NullableProblems") Message inputMessage) {
                Log.d(TAG, "Message received.");
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
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);

            if ((rpiList != null) && (diagnosisKeysList != null)) {
                MatchEntryContent matchEntryContent = new MatchEntryContent();
                Matcher matcher = new Matcher(rpiList, diagnosisKeysList, matchEntryContent);
                matcher.findMatches(
                        progress -> runOnUiThread(
                                () -> textView3.setText(getResources().getString(R.string.
                                        matching_not_done_yet_with_progress, progress.first, progress.second))));
                Log.d(TAG, "Finished matching, sending the message...");
                app.setMatchEntryContent(matchEntryContent);
            }
            Message completeMessage = mainActivity.uiThreadHandler.obtainMessage();
            completeMessage.sendToTarget();
        }
    }

    private void presentMatchResults() {
        if ((rpiList != null) && (diagnosisKeysList != null)) {
            MatchEntryContent matchEntryContent = app.getMatchEntryContent();
            int numberOfMatches = matchEntryContent.matchEntries.getTotalMatchingDkCount();
            Resources res = getResources();
            if (numberOfMatches > 0) {
                textView3.setText(res.getQuantityString(R.plurals.number_of_matches_found, numberOfMatches, numberOfMatches));
                textView3.setTextColor(matchBarColor);
            } else {
                textView3.setText(R.string.no_matches_found);
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

            // set date label formatter
            String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
            DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            // UTC because we don't want DateFormat to do additional time zone compensation

            BarDataSet dataSet3 = new BarDataSet(dataPoints3, "Matches"); // add entries to dataSet3
            dataSet3.setAxisDependency(YAxis.AxisDependency.LEFT);
            dataSet3.setColor(matchBarColor);

            BarData barData3 = new BarData(dataSet3);
            chart3.setData(barData3);
            findViewById(R.id.progressBar3).setVisibility(View.GONE);
            //chart3.setFitBars(true); // make the x-axis fit exactly all bars

            // the labels that should be drawn on the XAxis
            ValueFormatter xAxisFormatter3 = new ValueFormatter() {
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    return dateFormat.format(getDateFromDaysSinceEpoch((int) value));
                }
            };
            // the labels that should be drawn on the YAxis
            ValueFormatter yAxisFormatter3 = new ValueFormatter() {
                @SuppressLint("DefaultLocale")
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    return String.format("%5d", (int) value);
                }
            };
            // the bar labels
            ValueFormatter BarFormatter3 = new ValueFormatter() {
                @Override
                public String getBarLabel(BarEntry barEntry) {
                    return String.valueOf((int) barEntry.getY());
                }
            };
            barData3.setValueFormatter(BarFormatter3);
            XAxis xAxis = chart3.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setValueFormatter(xAxisFormatter3);
            xAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
            xAxis.setGranularityEnabled(true);
            xAxis.setDrawGridLines(false);

            YAxis yAxis = chart3.getAxisLeft();
            yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
            yAxis.setGranularityEnabled(true);
            yAxis.setAxisMinimum(0.0f);
            yAxis.setGridColor(gridColor);
            yAxis.setValueFormatter(yAxisFormatter3);

            chart3.getAxisRight().setAxisMinimum(0.0f);
            chart3.getAxisRight().setDrawLabels(false);
            chart3.getLegend().setEnabled(false);
            chart3.getDescription().setEnabled(false);
            chart3.setScaleYEnabled(false);
            chart3.getViewPortHandler().setMaximumScaleX(5.0f);
            chart3.invalidate(); // refresh

            // End of this path.
            // From now on, the user can scroll the charts,
            // or tap on a match to reach the DisplayDetailsActivity.
        }
    }


    private void syncCharts (BarChart mainChart, BarChart[]otherCharts){
        Matrix mainMatrix;
        float[] mainVals = new float[9];
        Matrix otherMatrix;
        float[] otherValues = new float[9];
        mainMatrix = mainChart.getViewPortHandler().getMatrixTouch();
        mainMatrix.getValues(mainVals);

        for (BarChart tempChart : otherCharts) {
            otherMatrix = tempChart.getViewPortHandler().getMatrixTouch();
            otherMatrix.getValues(otherValues);
            otherValues[Matrix.MSCALE_X] = mainVals[Matrix.MSCALE_X];
            otherValues[Matrix.MTRANS_X] = mainVals[Matrix.MTRANS_X];
            otherValues[Matrix.MSKEW_X] = mainVals[Matrix.MSKEW_X];
            otherMatrix.setValues(otherValues);
            tempChart.getViewPortHandler().refresh(otherMatrix, tempChart, true);
        }
    }

    class Chart1GestureListener implements OnChartGestureListener {
        public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
        public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
        public void onChartLongPressed(MotionEvent me) { }
        public void onChartDoubleTapped(MotionEvent me) {
            BarChart[] otherCharts = {chart2, chart3};
            syncCharts(chart1, otherCharts);
        }
        public void onChartSingleTapped(MotionEvent me) { }
        public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
            BarChart[] otherCharts = {chart2, chart3};
            syncCharts(chart1, otherCharts);
        }
        public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
            BarChart[] otherCharts = {chart2, chart3};
            syncCharts(chart1, otherCharts);
        }
        public void onChartTranslate(MotionEvent me, float dX, float dY) {
            BarChart[] otherCharts = {chart2, chart3};
            syncCharts(chart1, otherCharts);
        }
    }

    class Chart2GestureListener implements OnChartGestureListener {
        public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
        public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
        public void onChartLongPressed(MotionEvent me) { }
        public void onChartDoubleTapped(MotionEvent me) {
            BarChart[] otherCharts = {chart1, chart3};
            syncCharts(chart2, otherCharts);
        }
        public void onChartSingleTapped(MotionEvent me) { }
        public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
            BarChart[] otherCharts = {chart1, chart3};
            syncCharts(chart2, otherCharts);
        }
        public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
            BarChart[] otherCharts = {chart1, chart3};
            syncCharts(chart2, otherCharts);
        }
        public void onChartTranslate(MotionEvent me, float dX, float dY) {
            BarChart[] otherCharts = {chart1, chart3};
            syncCharts(chart2, otherCharts);
        }
    }

    class Chart3GestureListener implements OnChartGestureListener {
        public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
        public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
        public void onChartLongPressed(MotionEvent me) { }
        public void onChartDoubleTapped(MotionEvent me) {
            BarChart[] otherCharts = {chart1, chart2};
            syncCharts(chart3, otherCharts);
        }
        public void onChartSingleTapped(MotionEvent me) { }
        public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
            BarChart[] otherCharts = {chart1, chart2};
            syncCharts(chart3, otherCharts);
        }
        public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
            BarChart[] otherCharts = {chart1, chart2};
            syncCharts(chart3, otherCharts);
        }
        public void onChartTranslate(MotionEvent me, float dX, float dY) {
            BarChart[] otherCharts = {chart1, chart2};
            syncCharts(chart3, otherCharts);
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
                chart3.highlightValues(null);
            }
        }

        @Override
        public void onNothingSelected() {
            onValueSelected(entry, highlight);
        }
    }
}