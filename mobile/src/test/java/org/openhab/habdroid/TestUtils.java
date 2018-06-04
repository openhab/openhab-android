package org.openhab.habdroid;

import android.app.Application;
import android.content.Context;

import org.mockito.Mockito;

import java.io.File;

public class TestUtils {
    public static Context makeMockedAppContext() {
        Context mockContext = Mockito.mock(Application.class);
        Mockito.when(mockContext.getApplicationContext()).thenReturn(mockContext);
        Mockito.when(mockContext.getCacheDir()).thenReturn(new File("/foo"));
        return mockContext;
    }
}
