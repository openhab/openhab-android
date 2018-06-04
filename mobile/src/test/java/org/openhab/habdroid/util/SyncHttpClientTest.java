package org.openhab.habdroid.util;


import android.content.Context;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.openhab.habdroid.TestUtils;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class SyncHttpClientTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    Context mContext;

    @Before
    public void setupContext() throws IOException {
        mContext = TestUtils.makeMockedAppContext(tempFolder);
    }

    /**
     * Unit test against Issue #315 "Crash when connection could not be established"
     */
    @Test
    public void testMethodErrorResponse() {
        SyncHttpClient httpClient = new SyncHttpClient(mContext, "https://demo.test", null);

        String host = "just.a.local.url.local";
        SyncHttpClient.HttpStatusResult resp = httpClient.get("https://" + host).asStatus();

        assertEquals(500, resp.statusCode);
        assertTrue(resp.error instanceof UnknownHostException);
    }
}
