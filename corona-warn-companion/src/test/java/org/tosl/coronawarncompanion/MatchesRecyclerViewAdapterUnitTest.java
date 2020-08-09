package org.tosl.coronawarncompanion;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matchentries.MatchesRecyclerViewAdapter;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.tosl.coronawarncompanion.tools.Utils.getENINFromSeconds;

/**
 * Local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class MatchesRecyclerViewAdapterUnitTest {

    @Test
    public void getMatchEntryDetails_isCorrect() {

        // create content

        final int daysSinceEpoch = 18000;
        final int numGroups = 10;
        final int numEntriesPerGroup = 20;

        DiagnosisKeysProtos.TemporaryExposureKeyExport.Builder dkImportBuilder =
                DiagnosisKeysProtos.TemporaryExposureKeyExport.newBuilder();
        byte[] keyBytes = new byte[] {(byte) 0, (byte) 1, (byte) 2, (byte) 3,
                (byte) 4, (byte) 5, (byte) 6, (byte) 7, (byte) 8, (byte) 9,
                (byte) 10, (byte) 11, (byte) 12, (byte) 13, (byte) 14, (byte) 15};
        @SuppressWarnings("deprecation") DiagnosisKeysProtos.TemporaryExposureKey.Builder dkBuilder =
                DiagnosisKeysProtos.TemporaryExposureKey.newBuilder()
                .setKeyData(ByteString.copyFrom(keyBytes))
                .setTransmissionRiskLevel(8)
                .setRollingStartIntervalNumber(getENINFromSeconds(daysSinceEpoch*24*3600))
                .setRollingPeriod(144)
                .setReportType(DiagnosisKeysProtos.TemporaryExposureKey.ReportType.CONFIRMED_TEST);
        dkImportBuilder.addKeys(dkBuilder.build());
        DiagnosisKeysProtos.TemporaryExposureKeyExport dkImport = dkImportBuilder.build();

        byte[] aemBytes = new byte[] {(byte) 0, (byte) 1, (byte) 2, (byte) 3};
        byte[] aemXorBytes = new byte[] {(byte) 0, (byte) 1, (byte) 2, (byte) 3};

        int TimestampLocalTZ = daysSinceEpoch * 24*3600;

        ArrayList<Matcher.MatchEntry> list = new ArrayList<>();
        for (int groupPos = 0; groupPos < numGroups; groupPos++) {
            for (int entryPos = 0; entryPos < numEntriesPerGroup; entryPos++) {
                int startTimestampLocalTZ = TimestampLocalTZ;
                TimestampLocalTZ += 3;
                int endTimestampLocalTZ = TimestampLocalTZ;
                TimestampLocalTZ += 3;

                DiagnosisKeysProtos.TemporaryExposureKey dk = dkImport.getKeys(0);
                byte[] rpiBytes = {(byte) 0, (byte) 1, (byte) 2, (byte) 3,
                        (byte) 4, (byte) 5, (byte) 6, (byte) 7, (byte) 8, (byte) 9,
                        (byte) 10, (byte) 11, (byte) 12, (byte) 13, (byte) 14, (byte) 15};

                ContactRecordsProtos.ContactRecords.Builder contactRecordsBuilder1 =
                        ContactRecordsProtos.ContactRecords.newBuilder()
                                .addRecord(ContactRecordsProtos.ScanRecord.newBuilder()
                                        .setTimestamp(startTimestampLocalTZ)
                                        .setRssi(-50)
                                        .setAem(ByteString.copyFrom(aemBytes))
                                );
                ContactRecordsProtos.ContactRecords contactRecords1 = contactRecordsBuilder1.build();
                Matcher.MatchEntry entry1 = new Matcher.MatchEntry(dk, rpiBytes, contactRecords1,
                        startTimestampLocalTZ, endTimestampLocalTZ, aemXorBytes);
                list.add(entry1);

                ContactRecordsProtos.ContactRecords.Builder contactRecordsBuilder2 =
                        ContactRecordsProtos.ContactRecords.newBuilder()
                                .addRecord(ContactRecordsProtos.ScanRecord.newBuilder()
                                        .setTimestamp(startTimestampLocalTZ+1)
                                        .setRssi(-50)
                                        .setAem(ByteString.copyFrom(aemBytes))
                                );
                ContactRecordsProtos.ContactRecords contactRecords2 = contactRecordsBuilder2.build();
                Matcher.MatchEntry entry2 = new Matcher.MatchEntry(dk, rpiBytes, contactRecords2,
                        startTimestampLocalTZ, endTimestampLocalTZ, aemXorBytes);
                list.add(entry2);
            }
            TimestampLocalTZ += 4*60;
        }

        // assert that the list contains the correct number of items
        assertEquals(numGroups*numEntriesPerGroup*2, list.size());

        // run getMatchEntryDetails()
        MatchesRecyclerViewAdapter.MatchEntryDetails matchEntryDetails =
                MatchesRecyclerViewAdapter.getMatchEntryDetails(list,0);

        // assert that no entry was lost in the process
        assertEquals(list.size(),
                matchEntryDetails.dataPoints.size()+matchEntryDetails.dataPointsMinAttenuation.size());
        assertEquals(list.size(),
                matchEntryDetails.dotColors.size()+matchEntryDetails.dotColorsMinAttenuation.size());
        // assert that there's a color for each dataPoint
        assertEquals(matchEntryDetails.dataPoints.size(), matchEntryDetails.dotColors.size());
    }
}
