package org.openhab.habdroid;

import android.app.Application;
import android.content.Context;

import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

public class TestUtils {
    public static Context makeMockedAppContext(TemporaryFolder folder) throws IOException {
        Context mockContext = Mockito.mock(Application.class);
        File cacheFolder = folder.newFolder("cache");
        File appDir = folder.newFolder();
        Mockito.when(mockContext.getApplicationContext()).thenReturn(mockContext);
        Mockito.when(mockContext.getCacheDir()).thenReturn(cacheFolder);
        Mockito.when(mockContext.getDir(anyString(), anyInt()))
                .then(invocation -> new File(appDir, invocation.getArgument(0).toString()));
        return mockContext;
    }
}
