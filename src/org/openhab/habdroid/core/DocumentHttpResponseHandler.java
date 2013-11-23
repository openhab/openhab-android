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

import android.os.Message;
import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.apache.http.Header;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class DocumentHttpResponseHandler extends AsyncHttpResponseHandler {

    public void onSuccess(Document response) {}

    public void onSuccess(int statusCode, Header[] headers, Document response) {
        onSuccess(statusCode, response);
    }

    public void onSuccess(int statusCode, Document response) {
        onSuccess(response);
    }

    //
    // Pre-processing of messages (in original calling thread, typically the UI thread)
    //

    protected void handleSuccessMessage(int statusCode, Header[] headers, Document response) {
        onSuccess(statusCode, headers, response);
    }

    // Methods which emulate android's Handler and Message methods
    protected void handleMessage(Message msg) {
        Object[] response;

        switch(msg.what) {
            case SUCCESS_MESSAGE:
                response = (Object[])msg.obj;
                Document responseDocument = null;
                Log.d("DocumentHttpResponseHandler", String.format("response[%d]", response.length));
                try {
                    Log.d("DocumentHttpResponseHandler", "Got response = " + (String) response[2]);
                    responseDocument = parseResponse((String) response[2]);
                    handleSuccessMessage(((Integer) response[0]).intValue(), (Header[]) response[1], responseDocument);
                } catch (ParserConfigurationException e) {
                    handleFailureMessage(e, (String) response[2]);
                } catch (IOException e) {
                    handleFailureMessage(e, (String) response[2]);
                } catch (SAXException e) {
                    handleFailureMessage(e, (String) response[2]);
                }
                break;
            case FAILURE_MESSAGE:
                response = (Object[])msg.obj;
                handleFailureMessage((Throwable)response[0], (String)response[1]);
                break;
            case START_MESSAGE:
                onStart();
                break;
            case FINISH_MESSAGE:
                onFinish();
                break;
        }
    }

    protected Document parseResponse(String responseBody) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document document;
        DocumentBuilder builder = factory.newDocumentBuilder();
        if (responseBody != null) {
            document = builder.parse(new ByteArrayInputStream(responseBody.getBytes()));
            return document;
        } else
            return null;
    }

}
