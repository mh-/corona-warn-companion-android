package org.tosl.coronawarncompanion;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;

/**
 * A fragment representing a list of Items.
 */
public class ContactRecordRecyclerViewFragment extends Fragment {

    // TODO: Customize parameter argument names
    private static final String ARG_COLUMN_COUNT = "column-count";
    // TODO: Customize parameters
    private int mColumnCount = 1;

    private final MatchEntryContent mMatchEntryContent;
    private final int mDaysSinceEpochLocalTZ;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     * @param daysSinceEpochLocalTZ The day for which the fragment has been set up.
     * @param matchEntryContent The MarchEntryContent class that has all the MatchEntries.
     */
    public ContactRecordRecyclerViewFragment(int daysSinceEpochLocalTZ, MatchEntryContent matchEntryContent) {
        this.mDaysSinceEpochLocalTZ = daysSinceEpochLocalTZ;
        this.mMatchEntryContent = matchEntryContent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.recycler_view_frag, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            ContactRecordRecyclerViewAdapter adapter = new ContactRecordRecyclerViewAdapter(mMatchEntryContent.matchEntries.
                    getDailyMatchEntries(mDaysSinceEpochLocalTZ));
            recyclerView.setAdapter(adapter);
            //adapter.setHour(mInitialHour);
        }
        return view;
    }
}