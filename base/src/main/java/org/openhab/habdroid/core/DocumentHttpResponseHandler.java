/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
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
