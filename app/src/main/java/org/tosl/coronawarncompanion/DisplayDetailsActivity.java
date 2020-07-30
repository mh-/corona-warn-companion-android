package org.tosl.coronawarncompanion;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import static org.tosl.coronawarncompanion.tools.Utils.getDateFromDaysSinceEpoch;

public class DisplayDetailsActivity extends AppCompatActivity {

    private static boolean DEMO_MODE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_details);

        DEMO_MODE = CWCApplication.DEMO_MODE;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (!DEMO_MODE) {
                actionBar.setTitle("Corona Warn Companion");
            } else {
                actionBar.setTitle("DEMO Corona Warn Companion");
            }
        }

        // set date label formatter
        DateFormat dateFormat = new SimpleDateFormat("d.M.");

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.DAY_EXTRA_MESSAGE);
        assert message != null;
        int daysSinceEpoch = Integer.parseInt(message);
        String dateStr = dateFormat.format(getDateFromDaysSinceEpoch(daysSinceEpoch));

        TextView textView = findViewById(R.id.textView);
        textView.setText("Matches on "+dateStr);

        // ListView listView = findViewById(R.id.listView);

    }
}