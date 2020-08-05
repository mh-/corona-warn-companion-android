package org.tosl.coronawarncompanion.matchentries;

import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.R;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent.DailyMatchEntries;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.byteArrayToHex;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.xorTwoByteArrays;

/**
 * {@link RecyclerView.Adapter} that can display a {@link org.tosl.coronawarncompanion.matcher.Matcher.MatchEntry}.
 */
public class MatchesRecyclerViewAdapter extends RecyclerView.Adapter<MatchesRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = "CRRecyclerViewAdapter";
    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int redColor = Color.parseColor("#FF0000");
    private final int orangeColor = Color.parseColor("#FFA500");
    private final int yellowColor = Color.parseColor("#FFFF00");
    private final int greenColor = Color.parseColor("#00FF00");
    private final int blackColor = Color.parseColor("#000000");

    private final ArrayList<Pair<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries>> mValues;
    private CWCApplication mApp;

    public MatchesRecyclerViewAdapter(DailyMatchEntries dailyMatchEntries) {
        this.mApp = (CWCApplication) CWCApplication.getAppContext();
        this.mValues = new ArrayList<>();
        for (Map.Entry<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries> entry :
                dailyMatchEntries.getMap().entrySet()) {
            mValues.add(new Pair<>(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.match_card_fragment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mMatchEntriesPair = mValues.get(position);
        DiagnosisKeysProtos.TemporaryExposureKey dk = holder.mMatchEntriesPair.first;
        MatchEntryContent.GroupedByDkMatchEntries groupedByDkMatchEntries = holder.mMatchEntriesPair.second;
        int timeZoneOffset = mApp.getTimeZoneOffsetSeconds();

        ArrayList<Matcher.MatchEntry> list = groupedByDkMatchEntries.getList();

        // Text View:

        // set date label formatter
        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation

        boolean hasTransmissionRiskLevel = false;
        int transmissionRiskLevel = 0;
        //noinspection deprecation
        if (dk.hasTransmissionRiskLevel()) {
            //noinspection deprecation
            transmissionRiskLevel = dk.getTransmissionRiskLevel();
            hasTransmissionRiskLevel = true;
        }
        boolean hasReportType = false;
        DiagnosisKeysProtos.TemporaryExposureKey.ReportType reportType = DiagnosisKeysProtos.TemporaryExposureKey.ReportType.UNKNOWN;
        if (dk.hasReportType()) {
            reportType = dk.getReportType();
            hasReportType = true;
        }

        int minTimestampLocalTZDay0 = Integer.MAX_VALUE;
        int maxTimestampLocalTZDay0 = Integer.MIN_VALUE;
        List<Entry> dataPoints = new ArrayList<>();
        ArrayList<Integer> dotColors = new ArrayList<>();
        int minAttenuation = Integer.MAX_VALUE;
        for (Matcher.MatchEntry matchEntry : list) {
            for (ContactRecordsProtos.ScanRecord scanRecord : matchEntry.contactRecords.getRecordList()) {
                byte[] aem = xorTwoByteArrays(scanRecord.getAem().toByteArray(), matchEntry.aemXorBytes);
                if ((aem[0] != 0x40) || (aem[2] != 0x00) || (aem[3] != 0x00)) {
                    Log.w(TAG, "WARNING: Invalid AEM: " + byteArrayToHex(aem));
                }
                byte txPower = aem[1];
                //Log.d(TAG, "TXPower: "+txPower+" dBm");
                int rssi = (int) scanRecord.getRssi();
                //Log.d(TAG, "RSSI: "+rssi+" dBm");
                int attenuation = txPower - rssi;
                //Log.d(TAG, "Attenuation: "+attenuation+" dB");

                int timestampLocalTZ = scanRecord.getTimestamp() + timeZoneOffset;
                // reduce to "day0", to improve resolution within the float x value:
                int timestampLocalTZDay0 = timestampLocalTZ % (24*3600);
                dataPoints.add(new Entry(timestampLocalTZDay0, attenuation));

                if (attenuation < 55) {
                    dotColors.add(redColor);
                } else if (attenuation <= 63) {
                    dotColors.add(orangeColor);
                } else if (attenuation <= 73) {
                    dotColors.add(yellowColor);
                } else {
                    dotColors.add(greenColor);
                }

                if (minAttenuation > attenuation) {
                    minAttenuation = attenuation;
                }

                if (minTimestampLocalTZDay0 > timestampLocalTZDay0) {
                    minTimestampLocalTZDay0 = timestampLocalTZDay0;
                }
                if (maxTimestampLocalTZDay0 < timestampLocalTZDay0) {
                    maxTimestampLocalTZDay0 = timestampLocalTZDay0;
                }

            }
        }
        Date startDate = new Date(getMillisFromSeconds(minTimestampLocalTZDay0));
        Date endDate = new Date(getMillisFromSeconds(maxTimestampLocalTZDay0));
        String startDateStr = dateFormat.format(startDate);
        String endDateStr = dateFormat.format(endDate);

        String text = CWCApplication.getAppContext().getResources().getString(R.string.time);
        text += " ";
        if (startDateStr.equals(endDateStr)) {
            text += startDateStr;
        } else {
            text += startDateStr+"-"+endDateStr;
        }
        text += "\n";
        text += "\n";
        if (hasReportType) {
            text += CWCApplication.getAppContext().getResources().getString(R.string.report_type) + ": " + getReportTypeStr(reportType) + "\n";
        }
        // text += CWCApplication.getAppContext().getResources().getString(R.string.min_attenuation)+": "+minAttenuation+"dB\n";
        // text += "("+byteArrayToHex(dk.getKeyData().toByteArray())+")";
        text += CWCApplication.getAppContext().getResources().getString(R.string.distance_shown_as_attenuation)+":";
        holder.mTextView1.setText(text);

        text = "";
        if (hasTransmissionRiskLevel) {
            text = CWCApplication.getAppContext().getResources().getString(R.string.transmission_risk_level) + ": " + transmissionRiskLevel;
        }
        holder.mTextView2.setText(text);

        // Graph:

        LineDataSet dataSet = new LineDataSet(dataPoints, "Attenuation"); // add entries to dataSet
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setCircleColors(dotColors);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.enableDashedLine(0, 1, 0);

        LineData lineData = new LineData(dataSet);
        holder.mChartView.setData(lineData);

        // the labels that should be drawn on the XAxis
        ValueFormatter xAxisFormatter = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return dateFormat.format(new Date(getMillisFromSeconds((int) value)));
            }
        };
        // the labels that should be drawn on the YAxis
        ValueFormatter yAxisFormatter = new ValueFormatter() {
            @SuppressLint("DefaultLocale")
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.format("%2d", (int) value);
            }
        };

        XAxis xAxis = holder.mChartView.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(xAxisFormatter);
        xAxis.setGranularity(60.0f); // minimum axis-step (interval) is 60 seconds
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisMinimum(minTimestampLocalTZDay0-60);
        xAxis.setAxisMaximum(maxTimestampLocalTZDay0+60);

        YAxis yAxis = holder.mChartView.getAxisLeft();
        yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        yAxis.setGranularityEnabled(true);
        yAxis.setAxisMinimum(0.0f);
        yAxis.setGridColor(gridColor);
        yAxis.setValueFormatter(yAxisFormatter);
        yAxis.setInverted(true);

        holder.mChartView.getAxisRight().setAxisMinimum(0.0f);
        holder.mChartView.getAxisRight().setDrawLabels(false);
        holder.mChartView.getLegend().setEnabled(false);
        holder.mChartView.getDescription().setEnabled(false);
        holder.mChartView.setScaleYEnabled(false);
        int span = maxTimestampLocalTZDay0-minTimestampLocalTZDay0;
        float maximumScaleX = span / 700.0f;
        if (maximumScaleX < 1.0f) {
            maximumScaleX = 1.0f;
        }
        Log.d(TAG, "maximumScaleX: "+maximumScaleX);
        holder.mChartView.getViewPortHandler().setMaximumScaleX(maximumScaleX);
        holder.mChartView.invalidate(); // refresh
    }

    private String getReportTypeStr(DiagnosisKeysProtos.TemporaryExposureKey.ReportType reportType) {
        switch (reportType) {
            case REVOKED:
                return(mApp.getString(R.string.report_type_revoked));
            case UNKNOWN:
                return(mApp.getString(R.string.report_type_unknown));
            case RECURSIVE:
                return(mApp.getString(R.string.report_type_recursive));
            case SELF_REPORT:
                return(mApp.getString(R.string.report_type_self_report));
            case CONFIRMED_TEST:
                return(mApp.getString(R.string.report_type_confirmed_test));
            case CONFIRMED_CLINICAL_DIAGNOSIS:
                return(mApp.getString(R.string.report_type_clinical_diagnosis));
            default:
                return mApp.getString(R.string.invalid);
        }
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mTextView1;
        public final TextView mTextView2;
        public final LineChart mChartView;
        public Pair<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries> mMatchEntriesPair;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTextView1 = view.findViewById(R.id.textView1);
            mTextView2 = view.findViewById(R.id.textView2);
            mChartView = view.findViewById(R.id.chart);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mTextView1.getText() + "'";
        }
    }
}