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

package org.tosl.coronawarncompanion.matcher;

import org.tosl.coronawarncompanion.crypto.AesEcbEncryptor;
import org.tosl.coronawarncompanion.crypto.CryptoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.tosl.coronawarncompanion.crypto.AesCtrEncryptor.aesCtr;
import static org.tosl.coronawarncompanion.crypto.KeyDerivation.hkdfSha256;

public class Crypto {

    private static final int intervalLengthMinutes = 10;
    private static final int tekRollingPeriod = 144;
    private AesEcbEncryptor encryptor;

    private final ArrayList<RpiWithInterval> rpiBuffer;  // only one is required per Crypto object
    // (But this can't be static, otherwise there's a concurrency problem when MainActivity is recreated.)

    public Crypto() {
        try {
            this.encryptor = new AesEcbEncryptor();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        rpiBuffer = new ArrayList<>(144);
    }

    public static byte[] encodedEnIntervalNumber(int enin) {
        final ByteBuffer bb = ByteBuffer.allocate(4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(enin);
        return bb.array();
    }

    public static byte[] deriveRpiKey(byte[] tek) {
        byte[] derivedKey = null;
        try {
            derivedKey = hkdfSha256(tek, null, "EN-RPIK".getBytes(StandardCharsets.UTF_8), 16);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return derivedKey;
    }

    public static byte[] deriveAemKey(byte[] tek) {
        byte[] derivedKey = null;
        try {
            derivedKey = hkdfSha256(tek, null, "EN-AEMK".getBytes(StandardCharsets.UTF_8), 16);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return derivedKey;
    }

    public byte[] encryptRpi(byte[] rpiKey, int intervalNumber) {
        byte[] enin = encodedEnIntervalNumber(intervalNumber);
        ByteArrayOutputStream padded_data = new ByteArrayOutputStream();
        try {
            padded_data.write("EN-RPI".getBytes(StandardCharsets.UTF_8));
            padded_data.write(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
            padded_data.write(enin);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] ciphertext = null;
        try {
            encryptor.init(rpiKey);
            ciphertext = encryptor.encrypt(padded_data.toByteArray());
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return ciphertext;
    }

    public static class RpiWithInterval {
        public final byte[] rpiBytes;
        public final int intervalNumber;
        public RpiWithInterval(byte[] rpiBytes, int intervalNumber) {
            this.rpiBytes = rpiBytes;
            this.intervalNumber = intervalNumber;
        }
    }

    public ArrayList<RpiWithInterval> createListOfRpisForIntervalRange(byte[] rpiKey, int startIntervalNumber, int intervalCount) {
        rpiBuffer.clear();
        byte[] padded_data = {0x45, 0x4E, 0x2D, 0x52, 0x50, 0x49, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        try {
            encryptor.init(rpiKey);
            for (int interval=startIntervalNumber; interval < startIntervalNumber + intervalCount; interval++) {
                padded_data[12] = (byte) (interval&0x000000ff);
                padded_data[13] = (byte) ((interval&0x0000ff00)>>8);
                padded_data[14] = (byte) ((interval&0x00ff0000)>>16);
                padded_data[15] = (byte) ((interval&0xff000000)>>24);

                RpiWithInterval rpiWithInterval = new RpiWithInterval(encryptor.encrypt(padded_data), interval);
                rpiBuffer.add(rpiWithInterval);
            }
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return rpiBuffer;
    }

    public static byte[] decryptAem(byte[] aemKey, byte[] aem, byte[] rpi) {
        byte[] result = null;
        try {
            result = aesCtr(aemKey, rpi, aem);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static byte[] encryptAem(byte[] aemKey, byte[] metadata, byte[] rpi) {
        byte[] result = null;
        try {
            result = aesCtr(aemKey, rpi, metadata);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return result;
    }
}
