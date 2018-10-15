package org.openhab.habdroid.util;

import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class SyncHttpClientTest {
    OkHttpClient mClient;

    @Before
    public void setupClient() {
        mClient = new OkHttpClient.Builder().build();
    }

    /**
     * Unit test against Issue #315 "Crash when connection could not be established".
     */
    @Test
    public void testMethodErrorResponse() {
        SyncHttpClient httpClient = new SyncHttpClient(mClient, "https://demo.test",
                null, null);

        String host = "just.a.local.url.local";
        SyncHttpClient.HttpStatusResult resp = httpClient.get("https://" + host).asStatus();

        assertEquals(500, resp.statusCode);
        assertTrue(resp.error instanceof UnknownHostException);
    }
}
