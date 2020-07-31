package org.tosl.coronawarncompanion.gmsreadout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        Log.d(TAG, "Result from trying to copy LevelDB: "+result);
        if (result.length() < 10) {
            Log.e(TAG, "ERROR: Super User rights not granted!");
            //TODO
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if(dir!= null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    public void copyFromAssets() {
        // Copy the GMS LevelDB from our app's assets to local app cache

        Log.d(TAG, "Trying to copy LevelDB from Assets");
        String cachePathStr = Objects.requireNonNull(context.getExternalCacheDir()).getPath();

        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("demo_rpi_db");
        } catch (IOException e) {
            Log.e(TAG, "Failed to get asset file list.", e);
        }
        String outDir = cachePathStr+'/'+dbNameModified;
        File outDirFile = new File(outDir);
        @SuppressWarnings("unused") boolean mkdirResult = outDirFile.mkdir();
        if (files != null) {
            for (String filename : files) {
                InputStream in;
                OutputStream out;
                try {
                    in = assetManager.open("demo_rpi_db/"+filename);
                    File outFile = new File(outDir, filename);
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                    in.close();
                    //noinspection UnusedAssignment
                    in = null;
                    out.flush();
                    out.close();
                    //noinspection UnusedAssignment
                    out = null;
                } catch(IOException e) {
                    Log.e(TAG, "Failed to copy asset file: " + filename, e);
                }
            }
        }

        Log.d(TAG, "Copied LevelDB.");
    }

    public void open() {
        // Now open our locally cached copy
        Options options = new Options();
        options.createIfMissing(false);
        options.compressionType(CompressionType.NONE);
        DBFactory factory = new Iq80DBFactory();
        String cachePathStr = Objects.requireNonNull(context.getExternalCacheDir()).getPath();
        try {
            levelDBStore = factory.open(new File(cachePathStr + "/" + dbNameModified), options);
        } catch (IllegalArgumentException | IOException e) { Log.d(TAG, e.getMessage()); }
        if (levelDBStore != null) {
            Log.d(TAG, "Opened LevelDB.");
        } else {
            Log.d(TAG, "LevelDB not found.");
        }
    }

    public void close() throws IOException {
        levelDBStore.close();
        Log.d(TAG, "Closed LevelDB.");
        levelDBStore = null;
    }


    public RpiList readToRpiList() {
        RpiList rpiList = new RpiList(context);

        ReadOptions readOptions = new ReadOptions();
        readOptions.verifyChecksums(true);
        readOptions.fillCache(true);

        DBIterator iterator = levelDBStore.iterator(readOptions);
        for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            byte[] key = iterator.peekNext().getKey();
            byte[] value = iterator.peekNext().getValue();

            byte[] rpiBytes = new byte[16];
            ByteBuffer keyBuf = ByteBuffer.wrap(key);
            int daysSinceEpoch = keyBuf.getShort();  // get first 2 bytes: date
            keyBuf.get(rpiBytes); // get the next 16 bytes: RPI

            ContactRecordsProtos.ContactRecords contactRecords = null;
            try {
                contactRecords = ContactRecordsProtos.ContactRecords.parseFrom(value);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            rpiList.addEntry(daysSinceEpoch, rpiBytes, contactRecords);
        }
        return rpiList;
    }

    public RpiList getRpisFromContactDB(boolean demoMode) {
        RpiList rpiList = null;
        try {
            // delete cache:
            try {
                File dir = context.getExternalCacheDir();
                deleteDir(dir);
            } catch (Exception e) { e.printStackTrace();}

            if (!demoMode) {
                copyFromGMS();
            } else {
                copyFromAssets();
            }
            open();
            if (levelDBStore != null) {
                try {
                    // Use the db in here...
                    rpiList = readToRpiList();
                } catch (Exception e) {
                    Log.e(TAG, "Exception", e);
                    e.printStackTrace();
                } finally {
                    // Make sure you close the db to shutdown the
                    // database and avoid resource leaks.
                    close();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            e.printStackTrace();
        }
        return rpiList;
    }
}
