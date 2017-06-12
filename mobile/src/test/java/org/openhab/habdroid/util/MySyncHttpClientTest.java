package org.openhab.habdroid.util;


import org.junit.Test;

import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;

public class MySyncHttpClientTest {
    /**
     * Unit test against Issue #315 "Crash when connection could not be established"
     */
    @Test
    public void testMethodErrorResponse() {
        MySyncHttpClient httpClient = new MySyncHttpClient(false);

        String host = "just.a.local.url.local";
        Response resp = httpClient.method(
                "https://" + host,
                "GET",
                new HashMap<String, String>(),
                null,
                "",
                new MyHttpClient.ResponseHandler() {
                    public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody,
                                          Throwable error) {}

                    public void onSuccess(Call call, int statusCode, Headers headers, byte[]
                            responseBody) {}
                });

        assertEquals(500, resp.code());
        assertEquals(host, resp.message());
    }
}
