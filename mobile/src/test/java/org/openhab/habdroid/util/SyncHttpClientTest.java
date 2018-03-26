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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PreferenceManager.class)
@PowerMockIgnore("javax.net.ssl.*")
public class SyncHttpClientTest {

    @Mock
    Context mContext;
    @Mock
    SharedPreferences mSharedPreferences;

    @Before
    public void setupContext() {
        PowerMockito.mockStatic(PreferenceManager.class);

        PowerMockito.when(mContext.getApplicationContext()).thenReturn(mContext);
        PowerMockito.when(mSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        PowerMockito.when(PreferenceManager.getDefaultSharedPreferences(any(Context.class))).thenReturn(mSharedPreferences);
    }

    /**
     * Unit test against Issue #315 "Crash when connection could not be established"
     */
    @Test
    public void testMethodErrorResponse() {
        SyncHttpClient httpClient = new SyncHttpClient(mContext, mSharedPreferences, "https://demo.test");

        String host = "just.a.local.url.local";
        SyncHttpClient.HttpResult resp = httpClient.get("https://" + host);

        assertEquals(500, resp.statusCode);
        assertTrue(resp.error instanceof UnknownHostException);
    }
}
