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
    public static final int TYPE_DIALOG = 1;
    public static final int TYPE_SNACKBAR = 2;
    public static final int TYPE_TOAST = 3;

    public static final int LOGLEVEL_DEBUG = 0;
    public static final int LOGLEVEL_REMOTE = 1;
    public static final int LOGLEVEL_LOCAL = 2;
    public static final int LOGLEVEL_NO_DEBUG = 4;
    public static final int LOGLEVEL_ALWAYS = 5;

    /**
     * Shows a message to the user.
     * You might want to send two messages: One detailed one with
     * logLevel MessageHandler.LOGLEVEL_DEBUG and one simple message with
     * MessageHandler.LOGLEVEL_NO_DEBUG
     *
     * @param message message to show
     * @param messageType can be one of MessageHandler.TYPE_*
     * @param logLevel can be on of MessageHandler.LOGLEVEL.*
     */
    public static void showMessageToUser(Activity ctx, String message, int messageType,
                                         int logLevel) {
        if (ctx.isFinishing() || message == null) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean debugEnabled = settings.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false);
        String remoteUrl = settings.getString(Constants.PREFERENCE_REMOTE_URL, "");
        String localUrl = settings.getString(Constants.PREFERENCE_LOCAL_URL, "");

        // if debug mode is enabled, show all messages, except those with logLevel 4
        if((debugEnabled && logLevel == LOGLEVEL_NO_DEBUG) ||
                (!debugEnabled && logLevel == LOGLEVEL_DEBUG)) {
            return;
        }

        switch (logLevel) {
            case LOGLEVEL_REMOTE:
                if (remoteUrl.length() > 1) {
                    Log.d(TAG, "Remote URL set, show message: " + message);
                } else {
                    Log.d(TAG, "No remote URL set, don't show message: " + message);
                    return;
                }
                break;
            case LOGLEVEL_LOCAL:
                if (localUrl.length() > 1) {
                    Log.d(TAG, "Local URL set, show message: " + message);
                } else {
                    Log.d(TAG, "No local URL set, don't show message: " + message);
                    return;
                }
                break;
        }

        switch (messageType) {
            case TYPE_DIALOG:
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
            case TYPE_SNACKBAR:
                Snackbar snackbar = Snackbar.make(ctx.findViewById(android.R.id.content),
                        message, Snackbar.LENGTH_LONG);
                snackbar.show();
                break;
            case TYPE_TOAST:
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
                break;
            default:
                throw new IllegalArgumentException("Wrong message type");
        }
    }
}
