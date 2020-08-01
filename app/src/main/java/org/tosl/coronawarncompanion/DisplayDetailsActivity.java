package org.tosl.coronawarncompanion;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;
import static org.tosl.coronawarncompanion.tools.Utils.getDaysFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;

public class DisplayDetailsActivity extends AppCompatActivity {

    private static final String TAG = "DisplayDetailsActivity";
    private static boolean DEMO_MODE;
    CWCApplication app = null;

    private BarChart chart1;
    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int matchBarColor = Color.parseColor("#FF0000");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_details);

        DEMO_MODE = CWCApplication.DEMO_MODE;
        app = (CWCApplication) getApplicationContext();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (!DEMO_MODE) {
                actionBar.setTitle("Corona Warn Companion");
            } else {
                actionBar.setTitle("DEMO Corona Warn Companion");
            }
        }

        // set date label formatter
        DateFormat dateFormat = new SimpleDateFormat("d.M.");

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.DAY_EXTRA_MESSAGE);
        assert message != null;
        int selectedDaysSinceEpoch = Integer.parseInt(message);
        String dateStr = dateFormat.format(getDateFromDaysSinceEpoch(selectedDaysSinceEpoch));

        TextView textView = findViewById(R.id.textView);
        textView.setText("Matches on "+dateStr);

        LinkedList<Matcher.MatchEntry> matches = app.getMatches();
        if (matches != null) {
            int[] numMatchesPerHour = new int[24];
            LinkedList<Matcher.MatchEntry>[] matchesPerHour = new LinkedList[24];
            for (int i=0; i<24; i++) {
                matchesPerHour[i] = new LinkedList<Matcher.MatchEntry>();
            }

            for (Matcher.MatchEntry match : matches) {
                if (getDaysFromSeconds(match.startTimestampLocalTZ) == selectedDaysSinceEpoch) {
                    /*
                    DiagnosisKeysProtos.TemporaryExposureKey diagnosisKey = match.diagnosisKey;
                    byte[] rpi = match.rpi;
                    ContactRecordsProtos.ContactRecords contactRecords = match.contactRecords;
                    */

                    Calendar startDateTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    // UTC because we don't want Calendar to do additional time zone compensation
                    startDateTime.setTimeInMillis(getMillisFromSeconds(match.startTimestampLocalTZ));
                    int startHour = startDateTime.get(Calendar.HOUR_OF_DAY);
                    //Log.d(TAG, "Hour: "+startHour);
                    numMatchesPerHour[startHour]++;
                    matchesPerHour[startHour].add(match);
                }
            }

            chart1 = findViewById(R.id.chart1);
            List<BarEntry> dataPoints1 = new ArrayList<>();
            for (int i=0; i<=23; i++) {
                if (numMatchesPerHour[i] > 0) {
                    dataPoints1.add(new BarEntry(i, numMatchesPerHour[i]));
                }
            }

            BarDataSet dataSet1 = new BarDataSet(dataPoints1, "Matches"); // add entries to dataSet1
            dataSet1.setAxisDependency(YAxis.AxisDependency.LEFT);
            dataSet1.setColor(matchBarColor);

            BarData barData1 = new BarData(dataSet1);
            dataSet1.setHighlightEnabled(true);
            chart1.setData(barData1);

            // the labels that should be drawn on the XAxis
            ValueFormatter xAxisFormatter1 = new ValueFormatter() {
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    return String.valueOf((int) value);
                }
            };
            // the labels that should be drawn on the YAxis
            ValueFormatter yAxisFormatter1 = new ValueFormatter() {
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
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(23.5f);

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
            chart1.setScaleXEnabled(true);
            chart1.invalidate(); // refresh


            // ListView listView = findViewById(R.id.listView);


        }
    }
}