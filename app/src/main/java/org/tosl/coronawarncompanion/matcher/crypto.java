package org.tosl.coronawarncompanion.matcher;

import org.tosl.coronawarncompanion.crypto.AesEcbEncryptor;
import org.tosl.coronawarncompanion.crypto.CryptoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import static org.tosl.coronawarncompanion.crypto.AesCtrEncryptor.aesCtr;
import static org.tosl.coronawarncompanion.crypto.KeyDerivation.hkdfSha256;

public class crypto {

    private static final int intervalLengthMinutes = 10;
    private static final int tekRollingPeriod = 144;

    int en_interval_number(int timestamp_seconds) {
        return timestamp_seconds / (60 * intervalLengthMinutes);
    }

    byte[] encodedEnIntervalNumber(int enin) {
        final ByteBuffer bb = ByteBuffer.allocate(4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(enin);
        return bb.array();
    }

    byte[] deriveRpiKey(byte[] tek) {
        byte[] derivedKey = null;
        try {
            derivedKey = hkdfSha256(tek, null, "EN-RPIK".getBytes(StandardCharsets.UTF_8), 16);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return derivedKey;
    }

    byte[] deriveAemKey(byte[] tek) {
        byte[] derivedKey = null;
        try {
            derivedKey = hkdfSha256(tek, null, "EN-AEMK".getBytes(StandardCharsets.UTF_8), 16);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return derivedKey;
    }

    byte[] encryptRpi(byte[] rpi_key, int interval_number) {
        byte[] enin = encodedEnIntervalNumber(interval_number);
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
            AesEcbEncryptor encryptor = AesEcbEncryptor.create();
            encryptor.init(rpi_key);
            ciphertext = encryptor.encrypt(padded_data.toByteArray());
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return ciphertext;
    }

    LinkedList<byte[]> createListOfRpisForIntervalRange(byte[] rpi_key, int interval_number, int interval_count) {
        LinkedList<byte[]> rpis = new LinkedList<>();

        ByteArrayOutputStream padded_data_template = new ByteArrayOutputStream();
        try {
            padded_data_template.write("EN-RPI".getBytes(StandardCharsets.UTF_8));
            padded_data_template.write(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
            AesEcbEncryptor encryptor = AesEcbEncryptor.create();
            encryptor.init(rpi_key);
            for (int interval=interval_number; interval <= interval_number + interval_count; interval++) {
                ByteArrayOutputStream padded_data = new ByteArrayOutputStream();
                padded_data.write(padded_data_template.toByteArray());
                padded_data.write(encodedEnIntervalNumber(interval));
                rpis.add(encryptor.encrypt(padded_data.toByteArray()));
            }
        } catch (CryptoException | IOException e) {
            e.printStackTrace();
        }
        return rpis;
    }

    byte[] decryptAem(byte[] aem_key, byte[] aem, byte[] rpi) {
        byte[] result = null;
        try {
            result = aesCtr(aem_key, rpi, aem);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return result;
    }

    byte[] encryptAem(byte[] aem_key, byte[] metadata, byte[] rpi) {
        byte[] result = null;
        try {
            result = aesCtr(aem_key, rpi, metadata);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return result;
    }
}
