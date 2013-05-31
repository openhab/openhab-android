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

import android.text.TextUtils;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.w3c.dom.Document;

import java.io.UnsupportedEncodingException;

public class CommandRequest extends BaseRequest<String>  {

    private String mCommand;
    private final Response.Listener<String> mListener;

    public CommandRequest(int method, String url, Response.Listener<String> listener,
                          Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        mListener = listener;
    }

    public CommandRequest(int method, String url, String command, Response.Listener<String> listener,
                          Response.ErrorListener errorListener) {
        this(method, url, listener, errorListener);
        setCommand(command);
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        if (!TextUtils.isEmpty(mCommand)) {
            try {
                return mCommand.getBytes(getParamsEncoding());
            } catch (UnsupportedEncodingException uee) {
                throw new RuntimeException("Encoding not supported: " + getParamsEncoding(), uee);
            }
        }
        return null;
    }

    @Override
    public String getBodyContentType() {
        return "text/plain; charset=" + getParamsEncoding();
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse networkResponse) {
        return Response.success(networkResponse.data.toString(), HttpHeaderParser.parseCacheHeaders(networkResponse));
    }

    @Override
    protected void deliverResponse(String s) {
        mListener.onResponse(s);
    }

    public void setCommand(String command) {
        mCommand = command;
    }

    public String getCommand() {
        return mCommand;
    }

}
