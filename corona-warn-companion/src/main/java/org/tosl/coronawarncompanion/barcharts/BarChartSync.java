package org.tosl.coronawarncompanion.barcharts;

import com.github.mikephil.charting.charts.BarChart;

import java.util.ArrayList;

public class BarChartSync {
    ArrayList<BarChart> barChartList;

    public BarChartSync() {
        this.barChartList = new ArrayList<>();
    }

    public void add(BarChart barChart) {
        this.barChartList.add(barChart);
    }
}
