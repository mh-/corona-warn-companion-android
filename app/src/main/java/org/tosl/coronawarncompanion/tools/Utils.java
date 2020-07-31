package org.tosl.coronawarncompanion.tools;

import java.util.Date;

public class Utils {
    public static final int standardRollingPeriod = 144;

    public static long getMillisFromDays(int days) {
        return (long) days * 24*3600*1000L;
    }

    public static int getSecondsFromDays(int days) {
        return days * 24*3600;
    }

    public static Date getDateFromDaysSinceEpoch(int daysSinceEpoch) {
        return new Date(getMillisFromDays(daysSinceEpoch));
    }

    public static int getDaysFromSeconds(int seconds) {
        return (int) seconds / (24*3600);
    }

    public static int getDaysFromMillis(long millis) {
        return (int) millis / (24*3600*1000);
    }

    public static int getENINFromDate(Date date) {
        return (int)(date.getTime()/(10*60*1000));
    }

    public static int getENINFromSeconds(int seconds) {
        return seconds / (10*60);
    }

    public static long getMillisFromENIN(int ENIN) {
        return (long) ENIN * 10*60*1000L;
    }

    public static Date getDateFromENIN(int ENIN) {
        return new Date((long) ENIN * 10*60*1000L);
    }

    public static int getDaysSinceEpochFromENIN(int ENIN) {
        return ENIN/standardRollingPeriod;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
