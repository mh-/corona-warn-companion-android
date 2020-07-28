package org.tosl.coronawarncompanion.tools;

import java.util.Date;

public class Utils {
    public static final int standardRollingPeriod = 144;

    public static long getMillisFromDaysSinceEpoch(int daysSinceEpoch) {
        return (long) daysSinceEpoch * 24*60*60*1000L;
    }

    public static Date getDateFromDaysSinceEpoch(int daysSinceEpoch) {
        return new Date(getMillisFromDaysSinceEpoch(daysSinceEpoch));
    }

    public static int getENINFromDate(Date date) {
        return (int)(date.getTime()/(10*60*1000));
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
