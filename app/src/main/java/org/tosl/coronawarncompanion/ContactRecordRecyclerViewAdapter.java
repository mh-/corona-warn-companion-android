package org.tosl.coronawarncompanion;

import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link org.tosl.coronawarncompanion.matcher.Matcher.MatchEntry}.
 */
public class ContactRecordRecyclerViewAdapter extends RecyclerView.Adapter<ContactRecordRecyclerViewAdapter.ViewHolder> {

    private final MatchEntryContent.DailyMatchEntries mDailyMatchEntries;
    private List<Matcher.MatchEntry> mValues;

    public ContactRecordRecyclerViewAdapter(MatchEntryContent.DailyMatchEntries dailyMatchEntries) {
        mDailyMatchEntries = dailyMatchEntries;
        mValues = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fragment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mIdView.setText(Integer.toString(mValues.get(position).startTimestampLocalTZ));
        holder.mContentView.setText(Integer.toString(mValues.get(position).endTimestampLocalTZ));
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
        public final TextView mIdView;
        public final TextView mContentView;
        public Matcher.MatchEntry mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mIdView = (TextView) view.findViewById(R.id.item_number);
            mContentView = (TextView) view.findViewById(R.id.content);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}