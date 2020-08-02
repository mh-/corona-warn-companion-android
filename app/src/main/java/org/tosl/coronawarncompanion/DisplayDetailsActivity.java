package org.tosl.coronawarncompanion;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;

public class DisplayDetailsActivity extends AppCompatActivity {

    private static final String TAG = "DisplayDetailsActivity";
    private static boolean DEMO_MODE;
    private CWCApplication app = null;
    private MatchEntryContent matchEntryContent;

    private BarChart chart1;
    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int matchBarColor = Color.parseColor("#FF0000");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_details);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.DAY_EXTRA_MESSAGE);
        assert message != null;
        int selectedDaysSinceEpochLocalTZ = Integer.parseInt(message);

        DEMO_MODE = CWCApplication.DEMO_MODE;
        app = (CWCApplication) getApplicationContext();
        matchEntryContent = app.getMatchEntryContent();

        if (savedInstanceState == null) {

            // Action Bar:
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                if (!DEMO_MODE) {
                    actionBar.setTitle("Corona Warn Companion");
                } else {
                    actionBar.setTitle("DEMO Corona Warn Companion");
                }
            }

            // Chart:

            // set date label formatter
            String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
            DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            // UTC because we don't want DateFormat to do additional time zone compensation
            String dateStr = dateFormat.format(getDateFromDaysSinceEpoch(selectedDaysSinceEpochLocalTZ));

            TextView textView = findViewById(R.id.textView);
            textView.setText(getString(R.string.matches_on_day, dateStr));

            chart1 = findViewById(R.id.chart1);
            chart1.setOnChartValueSelectedListener(new Chart1ValueSelectedListener());

            List<BarEntry> dataPoints1 = new ArrayList<>();
            int minHourWithData = -1;
            for (int i = 0; i <= 23; i++) {
                int numMatchesPerHour = matchEntryContent.matchEntries.
                        getDailyMatchEntries(selectedDaysSinceEpochLocalTZ).getHourlyMatchEntries(i).getHourlyCount();
                if (numMatchesPerHour > 0) {
                    dataPoints1.add(new BarEntry(i, numMatchesPerHour));
                    if (minHourWithData == -1) {
                        minHourWithData = i;
                    }
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
                    return String.valueOf((int) value).concat("h");
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
            if (minHourWithData >= 0) {
                chart1.highlightValue((float) minHourWithData, 0);
            }
            chart1.invalidate(); // refresh

            // RecyclerView List:
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            ContactRecordRecyclerViewFragment contactRecordRecyclerViewFragment =
                    new ContactRecordRecyclerViewFragment(selectedDaysSinceEpochLocalTZ, minHourWithData, matchEntryContent);
            transaction.replace(R.id.contentFragment, contactRecordRecyclerViewFragment);
            transaction.commit();

            // End of this path.
            // From now on, the user can scroll the chart,
            // or tap on a match.
        }
    }
    // global variables
    protected static Entry entry;
    protected static Highlight highlight;

    class Chart1ValueSelectedListener implements OnChartValueSelectedListener {
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

                ContactRecordRecyclerViewFragment fragment = (ContactRecordRecyclerViewFragment) getSupportFragmentManager().findFragmentById(R.id.contentFragment);
                if (fragment != null) {
                    RecyclerView recyclerView = (RecyclerView) fragment.getView();
                    if (recyclerView != null) {
                        ContactRecordRecyclerViewAdapter adapter = (ContactRecordRecyclerViewAdapter) recyclerView.getAdapter();
                        if (adapter != null) {
                            adapter.setHour(x);
                            adapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        }

        @Override
        public void onNothingSelected() {
            chart1.highlightValue(highlight);
            onValueSelected(entry, highlight);
        }
    }
}