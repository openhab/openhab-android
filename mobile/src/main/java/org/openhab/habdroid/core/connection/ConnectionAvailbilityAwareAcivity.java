package org.openhab.habdroid.core.connection;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.ui.NoNetworkActivity;

import static org.openhab.habdroid.ui.NoNetworkActivity.NO_NETWORK_MESSAGE;
import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_EXTRA;
import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_MESSAGE;

public abstract class ConnectionAvailbilityAwareAcivity extends AppCompatActivity {
    private Thread.UncaughtExceptionHandler originalHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setConnectionExceptionHandler();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (originalHandler == null) {
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler);
    }

    private void setConnectionExceptionHandler() {
        final Activity activity = this;

        originalHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                if (throwable instanceof NetworkNotAvailableException) {
                    Intent networkNotSupportedIntent = new Intent(activity, NoNetworkActivity.class);
                    networkNotSupportedIntent.putExtra(NO_NETWORK_MESSAGE, throwable.getMessage());
                    startActivity(networkNotSupportedIntent);
                } else if (throwable instanceof NetworkNotSupportedException) {
                    Intent noNetworkIntent = new Intent(activity, NoNetworkActivity.class);
                    noNetworkIntent.putExtra(NO_NETWORK_MESSAGE, throwable.getMessage());
                    startActivity(noNetworkIntent);
                } else if (throwable instanceof NoUrlInformationException) {
                    Intent mainIntent = getBaseContext().getPackageManager()
                            .getLaunchIntentForPackage(getBaseContext().getPackageName());
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    finish();
                    mainIntent.putExtra(NO_URL_INFO_EXCEPTION_EXTRA, true);
                    mainIntent.putExtra(NO_URL_INFO_EXCEPTION_MESSAGE, throwable.getMessage());
                    startActivity(mainIntent);
                } else {
                    originalHandler.uncaughtException(thread, throwable);
                    return;
                }

                System.exit(0);
            }
        });
    }
}
