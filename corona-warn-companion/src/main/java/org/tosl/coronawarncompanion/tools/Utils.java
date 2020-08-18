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

package org.tosl.coronawarncompanion.tools;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import java.util.Date;

public class Utils {
    public static final int standardRollingPeriod = 144;

    public static long getMillisFromDays(int days) {
        return (long) days * 24*3600*1000L;
    }

    public static long getMillisFromSeconds(int seconds) {
        return (long) seconds * 1000L;
    }

    public static int getSecondsFromDays(int days) {
        return days * 24*3600;
    }

    public static Date getDateFromDaysSinceEpoch(int daysSinceEpoch) {
        return new Date(getMillisFromDays(daysSinceEpoch));
    }

    public static int getDaysFromSeconds(int seconds) {
        return seconds / (24*3600);
    }

    public static int getDaysFromMillis(long millis) {
        return (int) (millis / (24*3600*1000L));
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

    public static byte[] xorTwoByteArrays(byte[] array1, byte[] array2) {
        byte[] result = new byte[array1.length];
        for (int i=0; i<array1.length; i++) {
            result[i] = (byte)(array1[i] ^ array2[i]);
        }
        return result;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static int resolveColorAttr(int colorAttr, Context context) {
        TypedValue resolvedAttr = resolveThemeAttr(colorAttr, context);
        // resourceId is used if it's a ColorStateList, and data if it's a color reference or a hex color
        int colorRes;
        if (resolvedAttr.resourceId != 0) {
            colorRes = resolvedAttr.resourceId;
        } else {
            colorRes = resolvedAttr.data;
        }
        return ContextCompat.getColor(context, colorRes);
    }

    private static TypedValue resolveThemeAttr(int attrRes, Context context) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(attrRes, typedValue, true);
        return typedValue;
    }
}
