package org.tosl.coronawarncompanion.gmsreadout;

import android.util.Log;

import java.io.Closeable;
import java.net.DatagramSocket;
import java.net.Socket;

public class CloseablesCloser {
    public static void close(Object... xs) {
        // Note: On Android API levels prior to 19, Socket does not implement Closeable
        for (Object x : xs) {
            if (x != null) {
                try {
                    Log.d("CloseablesCloser", "closing: "+x);
                    if (x instanceof Closeable) {
                        ((Closeable)x).close();
                    } else if (x instanceof Socket) {
                        ((Socket)x).close();
                    } else if (x instanceof DatagramSocket) {
                        ((DatagramSocket)x).close();
                    } else {
                        Log.d("CloseablesCloser", "cannot close: "+x);
                        throw new RuntimeException("cannot close "+x);
                    }
                } catch (Throwable e) {
                    Log.e("CloseablesCloser", "throws: ", e);
                }
            }
        }
    }
}