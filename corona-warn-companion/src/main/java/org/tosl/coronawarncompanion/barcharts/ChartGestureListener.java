package org.tosl.coronawarncompanion.barcharts;

import android.graphics.Matrix;
import android.view.MotionEvent;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import java.util.ArrayList;

public class ChartGestureListener implements OnChartGestureListener {

    private final BarChartSync barChartSync;
    private BarChart barChart;

    public ChartGestureListener(BarChartSync barChartSync, BarChart barChart) {
        this.barChartSync = barChartSync;
        this.barChart = barChart;
    }

    private void syncCharts (BarChart mainChart, ArrayList<BarChart> otherCharts){
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

    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) { }
    public void onChartLongPressed(MotionEvent me) { }
    public void onChartDoubleTapped(MotionEvent me) {
        syncCharts(barChart, barChartSync.barChartList);
    }
    public void onChartSingleTapped(MotionEvent me) { }
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
        syncCharts(barChart, barChartSync.barChartList);
    }
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
        syncCharts(barChart, barChartSync.barChartList);
    }
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        syncCharts(barChart, barChartSync.barChartList);
    }
}
