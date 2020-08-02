package org.tosl.coronawarncompanion;

import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.util.Log;
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

import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.byteArrayToHex;
import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.xorTwoByteArrays;

/**
 * {@link RecyclerView.Adapter} that can display a {@link org.tosl.coronawarncompanion.matcher.Matcher.MatchEntry}.
 */
public class ContactRecordRecyclerViewAdapter extends RecyclerView.Adapter<ContactRecordRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = "CRRecyclerViewAdapter";
    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int lineColor = Color.parseColor("#FF0000");

    private final MatchEntryContent.DailyMatchEntries mDailyMatchEntries;
    private List<Matcher.MatchEntry> mValues;

    public ContactRecordRecyclerViewAdapter(MatchEntryContent.DailyMatchEntries dailyMatchEntries) {
        mDailyMatchEntries = dailyMatchEntries;
        mValues = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.match_fragment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mMatchEntry = mValues.get(position);

        // set date label formatter
        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation

        Date startDate = new Date(getMillisFromSeconds(mValues.get(position).startTimestampLocalTZ));
        Date endDate = new Date(getMillisFromSeconds(mValues.get(position).endTimestampLocalTZ));
        String startDateStr = dateFormat.format(startDate);
        String endDateStr = dateFormat.format(endDate);
        String timeInfoStr;
        if (startDateStr.equals(endDateStr)) {
            timeInfoStr = startDateStr;
        } else {
            timeInfoStr = startDateStr+"-"+endDateStr;
        }

        List<Entry> dataPoints = new ArrayList<>();
        int minAttenuation = Integer.MAX_VALUE;
        for (ContactRecordsProtos.ScanRecord scanRecord : mValues.get(position).contactRecords.getRecordList()) {
            byte[] aem = xorTwoByteArrays(scanRecord.getAem().toByteArray(), mValues.get(position).aemXorBytes);
            if ((aem[0] != 0x40) || (aem[2] != 0x00) || (aem[3] != 0x00)) {
                Log.w(TAG, "WARNING: Invalid AEM: "+byteArrayToHex(aem));
            }
            byte txPower = aem[1];
            //Log.d(TAG, "TXPower: "+txPower+" dBm");
            int rssi = (int) scanRecord.getRssi();
            //Log.d(TAG, "RSSI: "+rssi+" dBm");
            int attenuation = txPower-rssi;
            //Log.d(TAG, "Attenuation: "+attenuation+" dB");

            int timestamp = scanRecord.getTimestamp();
            dataPoints.add(new Entry(timestamp, attenuation));

            if (minAttenuation > attenuation) {
                minAttenuation = attenuation;
            }
        }

        String text = timeInfoStr + ", min. "+minAttenuation+" dB:";
        holder.mTextView.setText(text);


        String deviceDateFormat2 = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hms");
        DateFormat dateFormat2 = new SimpleDateFormat(deviceDateFormat2, Locale.getDefault());
        // Don't set time zone UTC, so that DateFormat does the time zone compensation

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
                return dateFormat2.format(new Date(getMillisFromSeconds((int) value)));
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

        YAxis yAxis = holder.mChartView.getAxisLeft();
        yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        yAxis.setGranularityEnabled(true);
        yAxis.setAxisMinimum(0.0f);
        yAxis.setGridColor(gridColor);
        yAxis.setValueFormatter(yAxisFormatter);

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

    public void setHour(int hour) {
        if (hour >=0 && hour <=23) {
            mValues = mDailyMatchEntries.getHourlyMatchEntries(hour).getList();
        } else {
            mValues = new ArrayList<>();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mTextView;
        public final LineChart mChartView;
        public Matcher.MatchEntry mMatchEntry;

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