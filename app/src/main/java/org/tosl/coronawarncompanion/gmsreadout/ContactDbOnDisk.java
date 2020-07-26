package org.tosl.coronawarncompanion.gmsreadout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static org.tosl.coronawarncompanion.gmsreadout.Sudo.sudo;

public class ContactDbOnDisk {
    private static final String TAG = "ContactDbOnDisk";
    private DB levelDBStore = null;
    private final Context context;

    public ContactDbOnDisk(Context appContext) {
        context = appContext;
    }

    @SuppressLint("SdCardPath")
    private static final String gmsPathStr = "/data/data/com.google.android.gms";
    private static final String dbName = "app_contact-tracing-contact-record-db";
    private static final String dbNameModifier = "_";
    private static final String dbNameModified = dbName+dbNameModifier;

    public void copyFromGMS() {
        // Copy the GMS LevelDB to local app cache
        Log.d(TAG, "Trying to copy LevelDB");
        String cachePathStr = Objects.requireNonNull(context.getExternalCacheDir()).getPath();

        // First rename the LevelDB directory, then copy it, then rename to the original name
        String result = sudo(
                "rm -rf "+cachePathStr+"/"+dbNameModified,
                "mv "+gmsPathStr+"/"+dbName+" "+gmsPathStr+"/"+dbNameModified,
                "cp -R "+gmsPathStr+"/"+dbNameModified+" "+cachePathStr+"/",
                "mv "+gmsPathStr+"/"+dbNameModified+" "+gmsPathStr+"/"+dbName,
                "ls -la "+cachePathStr+"/"+dbNameModified
        );
        Log.d(TAG, "Copied LevelDB: "+result);
        if (result.length() < 10) {
            Log.e(TAG, "ERROR: Super User rights not granted!");
            //TODO
        }
    }

    public void open() throws IOException {
        // Now open our locally cached copy
        Options options = new Options();
        options.createIfMissing(false);
        options.compressionType(CompressionType.NONE);
        DBFactory factory = new Iq80DBFactory();
        String cachePathStr = Objects.requireNonNull(context.getExternalCacheDir()).getPath();
        levelDBStore = factory.open(new File(cachePathStr+"/"+dbNameModified), options);
        Log.d(TAG, "Opened LevelDB.");
    }

    public void close() throws IOException {
        levelDBStore.close();
        Log.d(TAG, "Closed LevelDB.");
        levelDBStore = null;
    }


    public RpiList readToRpiList() {
        RpiList rpiList = new RpiList();

        ReadOptions readOptions = new ReadOptions();
        readOptions.verifyChecksums(true);
        readOptions.fillCache(true);

        try (DBIterator iterator = levelDBStore.iterator(readOptions)) {
            for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                byte[] key = iterator.peekNext().getKey();
                byte[] value = iterator.peekNext().getValue();

                RpiList.RpiEntry rpiEntry = new RpiList.RpiEntry();
                ByteBuffer keyBuf = ByteBuffer.wrap(key);
                int daysSinceEpoch = keyBuf.getShort();
                // Log.d(TAG, "Days since Epoch: "+ daysSinceEpoch + ", Date: " + date);

                keyBuf.get(rpiEntry.rpi);
                rpiEntry.scanData = value;

                rpiList.addEntry(daysSinceEpoch, rpiEntry);
            }
        }
        return rpiList;
    }

    public RpiList getRpisFromContactDB() {
        RpiList rpiList = null;
        try {
            ContactDbOnDisk contactDBonDisk = new ContactDbOnDisk(context);
            contactDBonDisk.copyFromGMS();

            contactDBonDisk.open();
            try {
                // Use the db in here....
                rpiList = contactDBonDisk.readToRpiList();
            } catch (Exception e) {
                Log.e(TAG, "Exception", e);
                e.printStackTrace();
            } finally {
                // Make sure you close the db to shutdown the
                // database and avoid resource leaks.
                contactDBonDisk.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            e.printStackTrace();
        }
        return rpiList;
    }
}
