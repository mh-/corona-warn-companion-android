package org.tosl.coronawarncompanion.gmsreadout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

class Sudo {
    private static String readFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            s.write(buffer, 0, length);
        }
        return s.toString("UTF-8");
    }

    public static String sudo(String...strings) {
        String res = "";
        DataOutputStream outputStream = null;
        InputStream response = null;
        try{
            Process su = Runtime.getRuntime().exec("su");
            outputStream = new DataOutputStream(su.getOutputStream());
            response = su.getInputStream();
            for (String s : strings) {
                outputStream.writeBytes(s+"\n");
                outputStream.flush();
            }
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                su.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            res = readFromStream(response);
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            CloseablesCloser.close(outputStream, response);
        }
        return res;
    }
}
