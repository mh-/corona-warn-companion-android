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

package org.tosl.coronawarncompanion.dkdownload;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.tosl.coronawarncompanion.MainActivity;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;

public class DKDownload {
    private static final String TAG = "DKDownload";

    private static final String CWA_URL = "https://svc90.main.px.t-online.de/version/v1/diagnosis-keys/country/DE/date";

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private final RequestQueue queue;
    private final Response.ErrorListener errorResponseListener;
    private CallbackCommand errorResponseCallbackCommand;

    public DKDownload(Context context) {
        // Instantiate the Volley RequestQueue.
        queue = Volley.newRequestQueue(context);

        errorResponseListener = error -> {
            Log.e(TAG, "VolleyError "+error);
            if (errorResponseCallbackCommand != null) {
                doCallback(errorResponseCallbackCommand, null);
            }
        };
    }

    public static class FileResponse {
        public URL url;
        public byte[] fileBytes;
    }

    void startHttpRequestForStringResponse(String urlStr, Listener<String> responseListener,
                                           Response.ErrorListener errorResponseListener) {
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET,
                urlStr, responseListener, errorResponseListener);
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    public interface CallbackCommand {
        void execute(Object data);
    }

    public static void doCallback(CallbackCommand callbackCommand, Object data) {
        callbackCommand.execute(data);
    }

    String[] parseCwaListResponse(String str) {
        String reducedStr = str.replace("\"","");
        reducedStr = reducedStr.replace("[","");
        reducedStr = reducedStr.replace("]","");
        return reducedStr.split(",");
    }

    public void availableDatesRequest(CallbackCommand callbackCommand,
                                      MainActivity.errorResponseCallbackCommand errorResponseCallbackCommand) {

        Listener<String> responseListener = availableDatesStr -> {
            //Log.d(TAG, "Available Dates: "+availableDatesStr);
            String[] dateStringArray = parseCwaListResponse(availableDatesStr);
            LinkedList<Date> result = new LinkedList<>();
            for (String str : dateStringArray) {
                //Log.d(TAG, "Date: "+str);
                Date date = null;
                try {
                    date = dateFormatter.parse(str);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                if (date != null) {
                    //Log.d(TAG, "Date: "+date);
                    result.add(date);
                }
            }
            doCallback(callbackCommand, result);
        };
        this.errorResponseCallbackCommand = errorResponseCallbackCommand;
        startHttpRequestForStringResponse(CWA_URL+"", responseListener, errorResponseListener);
    }

    public void availableHoursForDateRequest(Date date, CallbackCommand callbackCommand) {

        Listener<String> responseListener = availableHoursStr -> {
            //Log.d(TAG, "Available Hours: "+availableHoursStr);
            String[] hourStringArray = parseCwaListResponse(availableHoursStr);
            LinkedList<String> result = new LinkedList<>();
            Collections.addAll(result, hourStringArray);
            doCallback(callbackCommand, result);
        };
        Response.ErrorListener localErrorResponseListener = error -> {
            Log.i(TAG, "VolleyError "+error);
            Log.i(TAG, "No hourly downloads available yet.");
            LinkedList<String> result = new LinkedList<>();
            doCallback(callbackCommand, result);
        };

        startHttpRequestForStringResponse(CWA_URL+"/"+getStringFromDate(date)+"/"+"hour", responseListener, localErrorResponseListener);
    }

    void startHttpRequestForByteArrayResponse(String urlStr, Listener<byte[]> responseListener,
                                              Response.ErrorListener errorResponseListener) {
        // Request a byte[] response from the provided URL.
        ByteArrayRequest byteArrayRequest = new ByteArrayRequest(Request.Method.GET,
                urlStr, responseListener, errorResponseListener);
        // Add the request to the RequestQueue.
        queue.add(byteArrayRequest);
    }

    public void dkFileRequest(URL url, MainActivity.processUrlListCallbackCommand callbackCommand,
                              CallbackCommand errorResponseCallbackCommand) {
        FileResponse fileResponse = new FileResponse();
        fileResponse.url = url;

        Listener<byte[]> responseListener = fileBytes -> {
            //Log.d(TAG, "File received, Length: "+fileBytes.length);
            fileResponse.fileBytes = fileBytes;
            doCallback(callbackCommand, fileResponse);
        };
        this.errorResponseCallbackCommand = errorResponseCallbackCommand;
        startHttpRequestForByteArrayResponse(url.toString(), responseListener, errorResponseListener);
    }

    private String getStringFromDate(Date date) {
        StringBuffer stringBuffer = new StringBuffer();
        return dateFormatter.format(date, stringBuffer, new FieldPosition(0)).toString();
    }

    public URL getDailyDKsURLForDate(Date date) {
        URL result = null;
        try {
            result = new URL(CWA_URL+"/"+getStringFromDate(date));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        //Log.d(TAG, "getDailyDKsURLForDate: URL: "+result);
        return result;
    }

    public URL getHourlyDKsURLForDateAndHour(Date date, String hour) {
        URL result = null;
        try {
            result = new URL(CWA_URL+"/"+getStringFromDate(date)+"/hour/"+hour);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        //Log.d(TAG, "getHourlyDKsURLForDateAndHour: URL: "+result);
        return result;
    }
}
