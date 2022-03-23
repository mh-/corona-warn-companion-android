/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020-2022  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import static org.tosl.coronawarncompanion.CWCApplication.mainActivityShouldBeRecreatedAnyway;
import static org.tosl.coronawarncompanion.CWCApplication.numDownloadDays;
import static org.tosl.coronawarncompanion.CWCApplication.sharedPreferences;
import static org.tosl.coronawarncompanion.CWCApplication.maxNumDownloadDays;
import static org.tosl.coronawarncompanion.CWCApplication.minNumDownloadDays;
import static org.tosl.coronawarncompanion.CWCApplication.backgroundThreadsShouldStop;
import static org.tosl.coronawarncompanion.CWCApplication.userHasChosenNumDownloadDays;
import static org.tosl.coronawarncompanion.MainActivity.mainActivityShouldBeRecreated;


public class SetNumberOfDownloadDaysActivity extends AppCompatActivity {

    private int previousNumDownloadDays;
    private EditText numberOfDownloadDaysEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_num_download_days);

        userHasChosenNumDownloadDays = true;
        if (mainActivityShouldBeRecreatedAnyway) {
            mainActivityShouldBeRecreated = true;
            backgroundThreadsShouldStop = true;
        }

        // Action Bar:
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_activity_set_num_download_days);
        }
        TextView explanationTextView1 = findViewById(R.id.explanationTextView1);
        explanationTextView1.setText(getResources().getString(R.string.explanation_for_num_download_days_1, minNumDownloadDays, maxNumDownloadDays));
        TextView explanationTextView2 = findViewById(R.id.explanationTextView2);
        explanationTextView2.setText(getResources().getString(R.string.explanation_for_num_download_days_2, minNumDownloadDays, maxNumDownloadDays));

        numberOfDownloadDaysEditText = findViewById(R.id.editTextNumberOfDownloadDays);
        // get Number Of Download Days from SharedPreferences
        numDownloadDays = sharedPreferences.getInt(getString(R.string.saved_num_download_days), maxNumDownloadDays);
        if (numDownloadDays > maxNumDownloadDays) numDownloadDays = maxNumDownloadDays;
        if (numDownloadDays < minNumDownloadDays) numDownloadDays = minNumDownloadDays;
        previousNumDownloadDays = numDownloadDays;
        numberOfDownloadDaysEditText.setText(String.valueOf(numDownloadDays));
        numberOfDownloadDaysEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String text = v.getText().toString();
                    numDownloadDays = Integer.parseInt(text);
                    if (numDownloadDays > maxNumDownloadDays) numDownloadDays = maxNumDownloadDays;
                    if (numDownloadDays < minNumDownloadDays) numDownloadDays = minNumDownloadDays;
                    numberOfDownloadDaysEditText.setText(String.valueOf(numDownloadDays));

                    if (numDownloadDays != previousNumDownloadDays) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putInt(getString(R.string.saved_num_download_days), numDownloadDays);
                        editor.apply();
                        mainActivityShouldBeRecreated = true;
                        backgroundThreadsShouldStop = true;
                    }
                    finish();
                    return true;
                }
                return false;
            }
        });
    }
}