package org.openhab.habdroid.core.message;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.util.Constants;

public class MessageHandler {
    private static final String TAG = MessageHandler.class.getSimpleName();

    /**
     * Shows a message to the user.
     * You might want to send two messages: One detailed one with
     * logLevel Constants.MESSAGES.LOGLEVEL.DEBUG and one simple message with
     * Constants.MESSAGES.LOGLEVEL.NO_DEBUG
     *
     * @param message message to show
     * @param messageType can be one of Constants.MESSAGES.*
     * @param logLevel can be on of Constants.MESSAGES.LOGLEVEL.*
     */
    public static void showMessageToUser(Activity ctx, String message, int messageType,
                                         int logLevel) {
        if (ctx.isFinishing() || message == null) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean debugEnabled = settings.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false);
        String remoteUrl = settings.getString(Constants.PREFERENCE_ALTURL, "");
        String localUrl = settings.getString(Constants.PREFERENCE_URL, "");

        // if debug mode is enabled, show all messages, except those with logLevel 4
        if(debugEnabled) {
            if (logLevel == Constants.MESSAGES.LOGLEVEL.NO_DEBUG) {
                return;
            }
        } else {
            switch (logLevel) {
                case Constants.MESSAGES.LOGLEVEL.REMOTE:
                    if (remoteUrl.length() > 1) {
                        Log.d(TAG, "Remote URL set, show message: " + message);
                    } else {
                        Log.d(TAG, "No remote URL set, don't show message: " + message);
                        return;
                    }
                    break;
                case Constants.MESSAGES.LOGLEVEL.LOCAL:
                    if (localUrl.length() > 1) {
                        Log.d(TAG, "Local URL set, show message: " + message);
                    } else {
                        Log.d(TAG, "No local URL set, don't show message: " + message);
                        return;
                    }
                    break;
            }
        }

        switch (messageType) {
            case Constants.MESSAGES.DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setMessage(message)
                        .setPositiveButton(ctx.getText(android.R.string.ok), new DialogInterface
                                .OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
                break;
            case Constants.MESSAGES.SNACKBAR:
                Snackbar snackbar = Snackbar.make(ctx.findViewById(android.R.id.content),
                        message, Snackbar.LENGTH_LONG);
                snackbar.show();
                break;
            case Constants.MESSAGES.TOAST:
                Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_LONG).show();
                break;
            default:
                throw new IllegalArgumentException("Message type not implemented");
        }
    }
}
