package org.tosl.coronawarncompanion.tracking;

import android.provider.BaseColumns;

public class TrackingData {
    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TrackingEntry.TABLE_NAME + " (" +
                    TrackingEntry._ID + " INTEGER PRIMARY KEY," +
                    TrackingEntry.COLUMN_NAME_TIME + " TEXT," +
                    TrackingEntry.COLUMN_NAME_LONGITUDE + " TEXT," +
                    TrackingEntry.COLUMN_NAME_LATITUDE + " TEXT)";

    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TrackingEntry.TABLE_NAME;

    public static class TrackingEntry implements BaseColumns {
            public static final String TABLE_NAME = "tracking_entry";
            public static final String COLUMN_NAME_LONGITUDE = "longitude";
            public static final String COLUMN_NAME_LATITUDE = "latitude";
            public static final String COLUMN_NAME_TIME = "time";

    }
}
