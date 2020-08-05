package org.tosl.coronawarncompanion;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;

import org.tosl.coronawarncompanion.matchentries.MatchEntryContent;
import org.tosl.coronawarncompanion.matchentries.MatchesRecyclerViewFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;

public class DisplayDetailsActivity extends AppCompatActivity {

    private static final String TAG = "DisplayDetailsActivity";
    private static boolean DEMO_MODE;
    private CWCApplication app = null;
    private MatchEntryContent matchEntryContent;

    private BarChart chart1;
    private final int gridColor = Color.parseColor("#E0E0E0");
    private final int matchBarColor = Color.parseColor("#FF0000");

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

        DEMO_MODE = CWCApplication.DEMO_MODE;
        app = (CWCApplication) getApplicationContext();
        matchEntryContent = app.getMatchEntryContent();

        if (savedInstanceState == null) {

            // Action Bar:
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                if (!DEMO_MODE) {
                    actionBar.setTitle("Corona Warn Companion");
                } else {
                    actionBar.setTitle("DEMO Corona Warn Companion");
                }
            }

            // TextView:
            // set date label formatter
            String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM");
            DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            // UTC because we don't want DateFormat to do additional time zone compensation
            String dateStr = dateFormat.format(getDateFromDaysSinceEpoch(selectedDaysSinceEpochLocalTZ));

            TextView textView = findViewById(R.id.textView);
            textView.setText(getResources().getQuantityString(R.plurals.matches_on_day, count, dateStr));

            // RecyclerView List:
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            MatchesRecyclerViewFragment matchesRecyclerViewFragment =
                    new MatchesRecyclerViewFragment(selectedDaysSinceEpochLocalTZ, matchEntryContent);
            transaction.replace(R.id.contentFragment, matchesRecyclerViewFragment);
            transaction.commit();

            // End of this path.
            // From now on, the user can tap on a match entry.
        }
    }
}