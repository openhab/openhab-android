package org.openhab.habdroid.util;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.UnknownHostException;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PreferenceManager.class)
@PowerMockIgnore("javax.net.ssl.*")
public class MySyncHttpClientTest {

    @Mock
    SharedPreferences mSharedPreferences;

    @Before
    public void setupContext() {
        PowerMockito.mockStatic(PreferenceManager.class);

        PowerMockito.when(PreferenceManager.getDefaultSharedPreferences(any(Context.class))).thenReturn(mSharedPreferences);
    }

    /**
     * Unit test against Issue #315 "Crash when connection could not be established"
     */
    @Test
    public void testMethodErrorResponse() {
        MySyncHttpClient httpClient = new MySyncHttpClient(null,false, true);
        httpClient.setBaseUrl("https://demo.test");

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
        assertTrue(resp.message().startsWith(UnknownHostException.class.getName()));
    }
}
