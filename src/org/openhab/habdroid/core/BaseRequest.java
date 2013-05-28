/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.core;

import android.util.Base64;
import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseRequest<T> extends Request<T>  {

    Map<String, String> mHeaders = new HashMap<String, String>();
    Map<String, String> mParams = new HashMap<String, String>();

    public BaseRequest(int method, String url, Response.ErrorListener listener) {
        super(method, url, listener);
    }

    @Override
    abstract protected Response<T> parseNetworkResponse(NetworkResponse networkResponse);

    @Override
    protected void deliverResponse(T t) {

    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return mHeaders;
    }

    public void setHeader(String headerName, String headerValue) {
        mHeaders.put(headerName, headerValue);
    }

    public String getHeader(String headerName) {
        return mHeaders.get(headerName);
    }

    public void setBasicAuth(String username, String password) {
        String userPassword = username + ":" + password;
        String encoding = Base64.encodeToString(userPassword.getBytes(), Base64.DEFAULT);
        setHeader("Authorization", "Basic " + encoding);
    }

    public void setLongPolling() {
        setHeader("X-Atmosphere-Transport", "long-polling");
    }

    @Override
    public Map<String, String> getParams() {
        return mParams;
    }

    public void setParam(String paramName, String paramValue) {
        mParams.put(paramName, paramValue);
    }
}