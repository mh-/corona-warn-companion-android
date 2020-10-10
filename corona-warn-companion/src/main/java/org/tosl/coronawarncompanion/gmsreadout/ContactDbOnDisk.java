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

package org.tosl.coronawarncompanion.gmsreadout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.rpis.RpiList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static org.tosl.coronawarncompanion.gmsreadout.Sudo.sudo;

public class ContactDbOnDisk {
    private static final String TAG = "ContactDbOnDisk";
    private DB levelDBStore = null;

    @SuppressLint("SdCardPath")
    private static final String gmsPathStr = "/data/data/com.google.android.gms";
    private static final String dbName = "app_contact-tracing-contact-record-db";
    private static final String dbNameModifier = "_";
    private static final String dbNameModified = dbName+dbNameModifier;
    private static String cachePathStr = "";

    private final Context context;

    public ContactDbOnDisk(Context context) {
        this.context = context;
    }

    public void copyFromGMS() {
        // Copy the GMS LevelDB to local app cache
        Log.d(TAG, "Trying to copy LevelDB");
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();
        }
        assert cacheDir != null;
        cachePathStr = cacheDir.getPath();

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
        }
    }

    static int checkAck(InputStream in) throws IOException{
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
	           c = in.read();
	           sb.append((char)c);
            } while(c != '\n');
            if (b == 1) { // error
                Log.e(TAG, sb.toString());
            }
            if (b == 2) { // fatal error
                Log.e(TAG, sb.toString());
            }
        }
        return b;
    }

    public void copyFromRaspberry() {
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();
        }
        assert cacheDir != null;
        cachePathStr = cacheDir.getPath();
        FileOutputStream fos = null;
        String rfile = "/tmp/test_db.zip";
        String lfile = cachePathStr+"/test_db.zip";
        try {
            JSch jSch = new JSch();
            Session session = jSch.getSession("pi", "192.168.43.16", 22);
            String prefix = null;
            if (new File(lfile).isDirectory()){
                prefix = lfile + File.separator;
            }
            UserInfo userInfo = new UserInfo() {
                @Override
                public String getPassphrase() {
                    return null;
                }

                @Override
                public String getPassword() {
                    return "raspberry";
                }

                @Override
                public boolean promptPassword(String message) {
                    return true;
                }

                @Override
                public boolean promptPassphrase(String message) {
                    return true;
                }

                @Override
                public boolean promptYesNo(String message) {
                    return true;
                }

                @Override
                public void showMessage(String message) {

                }
            };
            session.setUserInfo(userInfo);
            session.connect();

            String command = "scp -f "+rfile;
            Channel channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);

            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            byte[] buf = new byte[1024];
            buf[0] = 0; out.write(buf, 0, 1); out.flush();

            while (true) {
                int c = checkAck(in);
                if(c != 'C'){
                    break;
                }

                // read '0644 '
                in.read(buf, 0,5);

                long filesize = 0L;
                while (true) {
                    if (in.read(buf, 0, 1) < 0) {
                        break;
                    }
                    if (buf[0] == ' ') break;
                    filesize = filesize*10L+(long)(buf[0]-'0');
                }

                String file;
                for(int i=0;;i++){
                    in.read(buf, i, 1);
                    if(buf[i]==(byte)0x0a){
                        file=new String(buf, 0, i);
                        break;
                    }
                }


                // send '\0'
                buf[0]=0; out.write(buf, 0, 1); out.flush();

                // read a content of lfile
                fos = new FileOutputStream(prefix == null ? lfile : prefix + file);
                int foo;
                while (true) {
                    if (buf.length < filesize) foo = buf.length;
                    else foo = (int)filesize;
                    foo = in.read(buf, 0, foo);
                    if(foo < 0){
                        // error
                        break;
                    }
                    fos.write(buf, 0, foo);
                    filesize -= foo;
                    if (filesize == 0L) break;
                }
                fos.close();
                fos = null;

                if (checkAck(in) != 0){
                    return;
                }

                // send '\0'
                buf[0] = 0; out.write(buf, 0, 1); out.flush();
            }

            session.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
            try {
                if (fos != null) fos.close();
            } catch (Exception ee) {
                ee.printStackTrace();
            }
            return;
        }

        try {
            File destDir = new File(cachePathStr+"/"+dbNameModified);
            if (!destDir.mkdirs()) {
                throw new IOException("cannot create directories");
            }
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(new FileInputStream(lfile));
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                FileOutputStream fosZip = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fosZip.write(buffer, 0, len);
                }
                fosZip.close();
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
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
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();
        }
        assert cacheDir != null;
        cachePathStr = cacheDir.getPath();

        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("demo_rpi_db");
        } catch (IOException e) {
            Log.e(TAG, "Failed to get asset file list.", e);
        }
        String outDir = cachePathStr+'/'+dbNameModified;
        File outDirFile = new File(outDir);
        boolean mkdirResult = outDirFile.mkdir();
        if (files != null) {
            for (String filename : files) {
                InputStream in;
                OutputStream out;
                try {
                    in = assetManager.open("demo_rpi_db/" +filename);
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
        try {
            levelDBStore = factory.open(new File(cachePathStr + "/" + dbNameModified), options);
        } catch (IllegalArgumentException | IOException e) {
            String message = e.getMessage();
            if (message != null) {
                Log.d(TAG, message);
            }
        }
        if (levelDBStore != null) {
            Log.d(TAG, "Opened LevelDB.");
        } else {
            Log.d(TAG, "LevelDB not found.");
        }
    }

    public void close() {
        try {
            levelDBStore.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            return;
        }
        Log.d(TAG, "Closed LevelDB.");
        levelDBStore = null;
    }


    public RpiList readToRpiList() {
        RpiList rpiList = new RpiList();

        ReadOptions readOptions = new ReadOptions();
        readOptions.verifyChecksums(true);
        readOptions.fillCache(true);

        DBIterator iterator = levelDBStore.iterator(readOptions);
        for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            byte[] key = iterator.peekNext().getKey();
            byte[] value = iterator.peekNext().getValue();

            byte[] rpiBytes = new byte[16];
            ByteBuffer keyBuf = ByteBuffer.wrap(key);
            int daysSinceEpochUTC = keyBuf.getShort();  // get first 2 bytes: date
            keyBuf.get(rpiBytes); // get the next 16 bytes: RPI

            ContactRecordsProtos.ContactRecords contactRecords = null;
            try {
                contactRecords = ContactRecordsProtos.ContactRecords.parseFrom(value);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            if (contactRecords != null) {
                rpiList.addEntry(daysSinceEpochUTC, rpiBytes, contactRecords);
            }
        }
        return rpiList;
    }

    public Single<RpiList> getRpisFromContactDB() {
        Completable copyDB;
        // delete cache:
        try {
            File dir = context.getExternalCacheDir();
            deleteDir(dir);
        } catch (Exception e) { e.printStackTrace();}

        if (CWCApplication.appMode == CWCApplication.AppModeOptions.NORMAL_MODE) {
            copyDB = Completable.fromRunnable(this::copyFromGMS);
        } else if (CWCApplication.appMode == CWCApplication.AppModeOptions.RASPBERRY_MODE) {
            copyDB = Completable.fromRunnable(this::copyFromRaspberry)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread());
        } else if (CWCApplication.appMode == CWCApplication.AppModeOptions.DEMO_MODE) {
            copyDB = Completable.fromRunnable(this::copyFromAssets);
        } else {
            throw new IllegalStateException();
        }
        Single<RpiList> rpiListSingle = Single.fromCallable(() -> {
            RpiList rpiList = new RpiList();
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
            return rpiList;
        });
        return copyDB.andThen(rpiListSingle);
    }
}
