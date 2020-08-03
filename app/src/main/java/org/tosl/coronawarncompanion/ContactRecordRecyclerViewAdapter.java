package org.tosl.coronawarncompanion;

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

import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
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
public class ContactRecordRecyclerViewAdapter extends RecyclerView.Adapter<ContactRecordRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = "CRRecyclerViewAdapter";
    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int lineColor = Color.parseColor("#FF0000");

    private final ArrayList<Pair<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries>> mValues;
    private CWCApplication mApp;

    public ContactRecordRecyclerViewAdapter(DailyMatchEntries dailyMatchEntries) {
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
                .inflate(R.layout.match_fragment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mMatchEntriesPair = mValues.get(position);
        DiagnosisKeysProtos.TemporaryExposureKey dk = holder.mMatchEntriesPair.first;
        MatchEntryContent.GroupedByDkMatchEntries groupedByDkMatchEntries = holder.mMatchEntriesPair.second;
        int timeZoneOffset = mApp.getTimeZoneOffsetSeconds();

        // Text View:

        ArrayList<Matcher.MatchEntry> list = groupedByDkMatchEntries.getList();
        int minTimestampLocalTZ = Integer.MAX_VALUE;
        int maxTimestampLocalTZ = Integer.MIN_VALUE;

        for (Matcher.MatchEntry matchEntry : list) {
            if (minTimestampLocalTZ > matchEntry.startTimestampLocalTZ) {
                minTimestampLocalTZ = matchEntry.startTimestampLocalTZ;
            }
            if (maxTimestampLocalTZ < matchEntry.endTimestampLocalTZ) {
                maxTimestampLocalTZ = matchEntry.endTimestampLocalTZ;
            }
        }

        // set date label formatter
        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation
        Date startDate = new Date(getMillisFromSeconds(minTimestampLocalTZ));
        Date endDate = new Date(getMillisFromSeconds(maxTimestampLocalTZ));
        String startDateStr = dateFormat.format(startDate);
        String endDateStr = dateFormat.format(endDate);

        @SuppressWarnings("deprecation") int transmissionRiskLevel = dk.getTransmissionRiskLevel();

        List<Entry> dataPoints = new ArrayList<>();
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
                dataPoints.add(new Entry(timestampLocalTZ, attenuation));

                if (minAttenuation > attenuation) {
                    minAttenuation = attenuation;
                }
            }
        }

        String text = CWCApplication.getAppContext().getResources().getString(R.string.time);
        text += ": ";
        if (startDateStr.equals(endDateStr)) {
            text += startDateStr;
        } else {
            text += startDateStr+"-"+endDateStr;
        }
        text += ", ";
        text += CWCApplication.getAppContext().getResources().getString(R.string.transmission_risk_level)+": "+transmissionRiskLevel+"\n";
        text += CWCApplication.getAppContext().getResources().getString(R.string.min_attenuation)+": "+minAttenuation+"dB\n";
        text += "("+byteArrayToHex(dk.getKeyData().toByteArray())+")";

        holder.mTextView.setText(text);

        // Graph:

        LineDataSet dataSet = new LineDataSet(dataPoints, "Attenuation"); // add entries to dataSet
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setColor(lineColor);
        dataSet.setCircleColor(lineColor);

        LineData lineData = new LineData(dataSet);
        dataSet.setHighlightEnabled(false);
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
        xAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisMinimum(minTimestampLocalTZ-50);
        xAxis.setAxisMaximum(maxTimestampLocalTZ+100);

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
        holder.mChartView.invalidate(); // refresh
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mTextView;
        public final LineChart mChartView;
        public Pair<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries> mMatchEntriesPair;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTextView = view.findViewById(R.id.textview);
            mChartView = view.findViewById(R.id.chart);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mTextView.getText() + "'";
        }
    }
}