package org.tosl.coronawarncompanion.barcharts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ProgressBar;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.tosl.coronawarncompanion.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;

public class CwcBarChart  {

    private final BarChart barChart;
    private final ProgressBar progressBar;
    private BarData barData;

    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int matchBarColor = Color.parseColor("#FF0000");

    public CwcBarChart(BarChart barChart, ProgressBar progressBar, BarChartSync barChartSync, Context context) {
        this.barChart = barChart;
        this.progressBar = progressBar;
        this.barChart.setNoDataText(context.getString(R.string.please_wait));
        this.barChart.setNoDataTextColor(Color.parseColor("black"));
        barChartSync.add(barChart);
        barChart.setOnChartGestureListener(new ChartGestureListener(barChartSync, barChart));
    }

    public BarChart getBarChart() {
        return barChart;
    }

    public void setData(List<BarEntry> dataPoints, Integer color, String label, boolean itemsSelectable) {
        BarDataSet dataSet = new BarDataSet(dataPoints, label); // add entries to dataSet
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setColor(color);
        BarData barData = new BarData(dataSet);
        dataSet.setHighlightEnabled(itemsSelectable);
        this.barData = barData;
        this.barChart.setData(barData);
    }

    public void setFormatAndRefresh() {
        switchProgressBarOff();

        // set date label formatter
        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation

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
        barData.setValueFormatter(BarFormatter1);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(xAxisFormatter1);
        xAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);

        YAxis yAxis = barChart.getAxisLeft();
        yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        yAxis.setGranularityEnabled(true);
        yAxis.setAxisMinimum(0.0f);
        yAxis.setGridColor(gridColor);
        yAxis.setValueFormatter(yAxisFormatter1);

        barChart.getAxisRight().setAxisMinimum(0.0f);
        barChart.getAxisRight().setDrawLabels(false);
        barChart.getLegend().setEnabled(false);
        barChart.getDescription().setEnabled(false);
        barChart.setScaleYEnabled(false);
        barChart.getViewPortHandler().setMaximumScaleX(5.0f);

        //barChart.setFitBars(true); // make the x-axis fit exactly all bars

        barChart.invalidate(); // refresh
    }

    public void switchProgressBarOff() {
        progressBar.setVisibility(View.GONE);
    }
}
