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

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;

public class ByteArrayRequest extends Request<byte[]> {

    /** Lock to guard mListener as it is cleared on cancel() and read on delivery. */
    private final Object mLock = new Object();

    @Nullable
    @GuardedBy("mLock")
    private Listener<byte[]> mListener;

    /**
     * Creates a new request with the given method.
     *  @param method the request {@link Method} to use
     * @param url URL to fetch the byte array at
     * @param listener Listener to receive the byte array response
     * @param errorListener Error listener, or null to ignore errors
     */
    public ByteArrayRequest(
            int method,
            String url,
            @Nullable Listener<byte[]> listener,
            @Nullable ErrorListener errorListener) {
        super(method, url, errorListener);
        mListener = listener;
    }

    /**
     * Creates a new GET request.
     *
     * @param url URL to fetch the byte array at
     * @param listener Listener to receive the byte array response
     * @param errorListener Error listener, or null to ignore errors
     */
    public ByteArrayRequest(
            String url, Listener<byte[]> listener, @Nullable ErrorListener errorListener) {
        this(Method.GET, url, listener, errorListener);
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (mLock) {
            mListener = null;
        }
    }

    @Override
    protected void deliverResponse(byte[] response) {
        Response.Listener<byte[]> listener;
        synchronized (mLock) {
            listener = mListener;
        }
        if (listener != null) {
            listener.onResponse(response);
        }
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        byte[] parsed;
        parsed = response.data;
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }
}
