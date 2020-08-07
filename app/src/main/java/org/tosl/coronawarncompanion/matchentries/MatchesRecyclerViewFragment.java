/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tosl.coronawarncompanion.matchentries;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.tosl.coronawarncompanion.R;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matchentries.MatchesRecyclerViewAdapter;

/**
 * A fragment representing a list of Items.
 */
public class MatchesRecyclerViewFragment extends Fragment {

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
    public MatchesRecyclerViewFragment(int daysSinceEpochLocalTZ, MatchEntryContent matchEntryContent) {
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
        View view = inflater.inflate(R.layout.recycler_view_fragment, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            MatchesRecyclerViewAdapter adapter = new MatchesRecyclerViewAdapter(mMatchEntryContent.matchEntries.
                    getDailyMatchEntries(mDaysSinceEpochLocalTZ));
            recyclerView.setAdapter(adapter);
            //adapter.setHour(mInitialHour);
        }
        return view;
    }
}