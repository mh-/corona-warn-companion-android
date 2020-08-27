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

package org.tosl.coronawarncompanion;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matchentries.MatchesRecyclerViewAdapter;
import org.tosl.coronawarncompanion.matchentries.MatchesRecyclerViewFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;

public class DisplayDetailsActivity extends AppCompatActivity {

    private static final String TAG = "DisplayDetailsActivity";
    private MatchesRecyclerViewFragment matchesRecyclerViewFragment;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.details_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.showhideallscans) {
            RecyclerView recyclerView = (RecyclerView) this.matchesRecyclerViewFragment.getView();
            MatchesRecyclerViewAdapter matchesRecyclerViewAdapter;
            if (recyclerView != null) {
                matchesRecyclerViewAdapter = (MatchesRecyclerViewAdapter) recyclerView.getAdapter();
                if (matchesRecyclerViewAdapter != null) {
                    matchesRecyclerViewAdapter.toggleShowAllScans();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_details);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        String dayMessage = intent.getStringExtra(MainActivity.EXTRA_MESSAGE_DAY);
        int selectedDaysSinceEpochLocalTZ = 0;
        if (dayMessage != null) {
            selectedDaysSinceEpochLocalTZ = Integer.parseInt(dayMessage);
        }
        String countMessage = intent.getStringExtra(MainActivity.EXTRA_MESSAGE_COUNT);
        int count = 0;
        if (countMessage != null) {
            count = Integer.parseInt(countMessage);
        }

        MatchEntryContent matchEntryContent = CWCApplication.getMatchEntryContent();

        if (savedInstanceState == null) {

            // Action Bar:
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                if (CWCApplication.appMode == CWCApplication.AppModeOptions.NORMAL_MODE) {
                    actionBar.setTitle(R.string.title_activity_details);
                } else if (CWCApplication.appMode == CWCApplication.AppModeOptions.DEMO_MODE) {
                    actionBar.setTitle(R.string.title_activity_details_demo);
                } else if (CWCApplication.appMode == CWCApplication.AppModeOptions.RAMBLE_MODE) {
                    actionBar.setTitle(R.string.title_activity_details_ramble);
                } else {
                    throw new IllegalStateException();
                }
            }

            // TextView:
            // set date label formatter
            String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
            DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            // UTC because we don't want DateFormat to do additional time zone compensation
            String dateStr = dateFormat.format(getDateFromDaysSinceEpoch(selectedDaysSinceEpochLocalTZ));

            TextView textView = findViewById(R.id.textViewHeadline);
            textView.setText(getResources().getQuantityString(R.plurals.details_title_matches_on_day, count, dateStr));

            // RecyclerView List:
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            this.matchesRecyclerViewFragment =
                    new MatchesRecyclerViewFragment(selectedDaysSinceEpochLocalTZ, matchEntryContent);
            transaction.replace(R.id.contentFragment, this.matchesRecyclerViewFragment);
            transaction.commit();

            // End of this path.
            // From now on, the user can tap on a match entry.
        }
    }
}