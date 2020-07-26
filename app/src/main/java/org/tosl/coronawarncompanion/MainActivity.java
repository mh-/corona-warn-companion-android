package org.tosl.coronawarncompanion;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysImport;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.dkdownload.DKDownload;
import org.tosl.coronawarncompanion.gmsreadout.ContactDbOnDisk;
import org.tosl.coronawarncompanion.gmsreadout.RpiList;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeMap;

import static org.tosl.coronawarncompanion.dkdownload.Unzip.getUnzippedBytesFromZipFileBytes;
import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysSinceEpochFromENIN;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromDate;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromDaysSinceEpoch;
import static org.tosl.coronawarncompanion.tools.Utils.standardRollingPeriod;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private RpiList rpiList;
    private Date minDate;
    private Date maxDate;
    private Date currentDate;  // usually the same as maxDate

    private DKDownload diagnosisKeysDownload;
    private final LinkedList<URL> diagnosisKeysUrls = new LinkedList<>();

    private LinkedList<DiagnosisKeysProtos.TemporaryExposureKey> diagnosisKeysList = new LinkedList<>();

    private LinkedList<Matcher.MatchEntry> matches = new LinkedList<>();

    private final int gridColor = Color.parseColor("#E0E0E0");

    private BarChart chart1;
    private BarChart chart2;
    private BarChart chart3;
    private TextView textView1;
    private TextView textView2;
    private TextView textView3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1st Section: Get RPIs from database (requires root)

        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);
        textView3 = findViewById(R.id.textView3);
        chart1 = findViewById(R.id.chart1);
        chart2 = findViewById(R.id.chart2);
        chart3 = findViewById(R.id.chart3);
        chart1.setOnChartGestureListener(new Chart1GestureListener());
        chart2.setOnChartGestureListener(new Chart2GestureListener());
        chart3.setOnChartGestureListener(new Chart3GestureListener());

        ContactDbOnDisk contactDbOnDisk = new ContactDbOnDisk(this);
        rpiList = contactDbOnDisk.getRpisFromContactDB();

        SortedSet<Integer> rpiListDaysSinceEpoch = rpiList.getAvailableDaysSinceEpoch();
        List<BarEntry> dataPoints1 = new ArrayList<>();

        int count = 0;
        for (Integer daysSinceEpoch : rpiListDaysSinceEpoch) {
            int numEntries = rpiList.getRpiEntriesForDaysSinceEpoch(daysSinceEpoch).size();
            //Log.d(TAG, "Datapoint: " + daysSinceEpoch + ": " + numEntries);
            dataPoints1.add(new BarEntry(daysSinceEpoch, numEntries));
            count += numEntries;
        }

        // set date label formatter
        DateFormat dateFormat = new SimpleDateFormat("d.M.");

        minDate = new Date(getMillisFromDaysSinceEpoch(rpiListDaysSinceEpoch.first()));
        String minDateStr = dateFormat.format(minDate);
        maxDate = new Date(getMillisFromDaysSinceEpoch(rpiListDaysSinceEpoch.last()));
        String maxDateStr = dateFormat.format(maxDate);

        textView1.setText("RPIs: " + count + " entries (" + minDateStr + "-" + maxDateStr + ")");

        BarDataSet dataSet1 = new BarDataSet(dataPoints1, "RPIs"); // add entries to dataSet1
        dataSet1.setAxisDependency(YAxis.AxisDependency.LEFT);

        BarData barData1 = new BarData(dataSet1);
        chart1.setData(barData1);
        //chart1.setFitBars(true); // make the x-axis fit exactly all bars

        // the labels that should be drawn on the XAxis
        ValueFormatter xAxisFormatter1 = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return dateFormat.format(getDateFromDaysSinceEpoch((int) value));
            }
        };
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

        chart1.getAxisRight().setAxisMinimum(0.0f);
        chart1.getAxisRight().setDrawLabels(false);
        chart1.getLegend().setEnabled(false);
        chart1.getDescription().setEnabled(false);
        chart1.setScaleYEnabled(false);
        chart1.invalidate(); // refresh


        // 2nd Section: Diagnosis Keys

        diagnosisKeysDownload = new DKDownload(this);
        diagnosisKeysDownload.availableDatesRequest(new availableDatesResponseCallbackCommand());
        // (the rest is done asynchronously in callback functions)
    }

    public class availableDatesResponseCallbackCommand implements DKDownload.CallbackCommand {
        public void execute(Object data) {
            // get Daily Diagnosis Keys URLs for the previous days
            LinkedList<Date> availableDates = (LinkedList<Date>) data;
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
            LinkedList<String> availableHours = (LinkedList<String>) data;
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
            diagnosisKeysList.addAll(diagnosisKeysImport.getDiagnosisKeys());

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

        textView2.setText("DKs: " + count + " entries");

        List<BarEntry> dataPoints2 = new ArrayList<>();

        for (Integer ENIN : diagnosisKeyCountMap.keySet()) {
            //noinspection ConstantConditions
            int numEntries = diagnosisKeyCountMap.get(ENIN);
            //Log.d(TAG, "Datapoint: " + ENIN + ": " + numEntries);
            dataPoints2.add(new BarEntry(getDaysSinceEpochFromENIN(ENIN), numEntries));
        }

        // set date label formatter
        DateFormat dateFormat = new SimpleDateFormat("d.M.");

        BarDataSet dataSet2 = new BarDataSet(dataPoints2, "DKs"); // add entries to dataSet2
        dataSet2.setAxisDependency(YAxis.AxisDependency.LEFT);

        BarData barData2 = new BarData(dataSet2);
        chart2.setData(barData2);
        //chart2.setFitBars(true); // make the x-axis fit exactly all bars

        // the labels that should be drawn on the XAxis
        ValueFormatter xAxisFormatter2 = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return dateFormat.format(getDateFromDaysSinceEpoch((int) value));
            }
        };
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

        chart2.getAxisRight().setAxisMinimum(0.0f);
        chart2.getAxisRight().setDrawLabels(false);
        chart2.getLegend().setEnabled(false);
        chart2.getDescription().setEnabled(false);
        chart2.setScaleYEnabled(false);
        chart2.invalidate(); // refresh

        startMatching();
    }

    public Handler uiThreadHandler;

    private void startMatching() {
        uiThreadHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@SuppressWarnings("NullableProblems") Message inputMessage) {
                Log.d(TAG, "Message received.");
                presentMatchResults();
            }
        };

        HandlerThread ht = new HandlerThread("BackgroundMatcher");
        ht.start();
        Handler backgroundThreadHandler = new Handler(ht.getLooper());
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

            Matcher matcher = new Matcher(rpiList, diagnosisKeysList);
            mainActivity.matches = matcher.findMatches();
            Log.d(TAG, "Finished matching, sending the message...");
            Message completeMessage = mainActivity.uiThreadHandler.obtainMessage();
            completeMessage.sendToTarget();
        }
    }

    private void presentMatchResults() {
        int numberOfMatches = matches.size();
        if (numberOfMatches == 0) {
            textView3.setText("No matches found.");
        } else {
            textView3.setText("Matches found: " + numberOfMatches);
        }
        Log.d(TAG, "Number of matches: "+numberOfMatches);

        List<BarEntry> dataPoints3 = new ArrayList<>();
        SortedSet<Integer> rpiListDaysSinceEpoch = rpiList.getAvailableDaysSinceEpoch();
        int total = 0;
        for (Integer daysSinceEpoch : rpiListDaysSinceEpoch) {
            int count = 0;
            for (Matcher.MatchEntry matchEntry : matches) {
                if (getDaysSinceEpochFromENIN(matchEntry.diagnosisKey.getRollingStartIntervalNumber()) == daysSinceEpoch) {
                    count++;
                }
            }
            //Log.d(TAG, "Datapoint: " + daysSinceEpoch + ": " + count);
            dataPoints3.add(new BarEntry(daysSinceEpoch, count));
            total += count;
        }
        Log.d(TAG, "Number of matches displayed: "+total);

        // set date label formatter
        DateFormat dateFormat = new SimpleDateFormat("d.M.");

        BarDataSet dataSet3 = new BarDataSet(dataPoints3, "Matches"); // add entries to dataSet3
        dataSet3.setAxisDependency(YAxis.AxisDependency.LEFT);

        BarData barData3 = new BarData(dataSet3);
        chart3.setData(barData3);
        //chart3.setFitBars(true); // make the x-axis fit exactly all bars

        // the labels that should be drawn on the XAxis
        ValueFormatter xAxisFormatter3 = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return dateFormat.format(getDateFromDaysSinceEpoch((int) value));
            }
        };
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

        chart3.getAxisRight().setAxisMinimum(0.0f);
        chart3.getAxisRight().setDrawLabels(false);
        chart3.getLegend().setEnabled(false);
        chart3.getDescription().setEnabled(false);
        chart3.setScaleYEnabled(false);
        chart3.invalidate(); // refresh



        //TODO
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
        public void onChartDoubleTapped(MotionEvent me) { }
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
        public void onChartDoubleTapped(MotionEvent me) { }
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
        public void onChartDoubleTapped(MotionEvent me) { }
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
}