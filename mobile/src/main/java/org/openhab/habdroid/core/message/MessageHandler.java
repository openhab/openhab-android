package org.openhab.habdroid.core.message;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
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

    private static Snackbar snackbar;
    private static AlertDialog alertDialog;
    private static Toast toast;

    public static void closeAllMessages() {
        if (snackbar != null) {
            snackbar.dismiss();
        }

        if (alertDialog != null) {
            alertDialog.dismiss();
        }

        if (toast != null) {
            toast.cancel();
        }
    }

    public static void showMessageToUser(Activity ctx, String message, int messageType, int logLevel) {
        showMessageToUser(ctx, message, messageType, logLevel, 0, null);
    }

    /**
     * Shows a message to the user.
     * You might want to send two messages: One detailed one with
     * logLevel MessageHandler.LOGLEVEL_DEBUG and one simple message with
     * MessageHandler.LOGLEVEL_NO_DEBUG
     *
     * @param message message to show
     * @param messageType can be one of MessageHandler.TYPE_*
     * @param logLevel can be on of MessageHandler.LOGLEVEL.*
     * @param actionMessage A StringRes to use as a message for an action, if supported by the
     *                      messageType (can be 0)
     * @param actionListener A listener that should be executed when the action message is clicked.
     */
    public static void showMessageToUser(Activity ctx, String message, int messageType,
                                         int logLevel, int actionMessage,
                                         @Nullable View.OnClickListener actionListener) {
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
                alertDialog = builder.create();
                alertDialog.show();
                break;
            case TYPE_SNACKBAR:
                snackbar = Snackbar.make(ctx.findViewById(android.R.id.content),
                        message, Snackbar.LENGTH_LONG);
                if (actionListener != null && actionMessage != 0) {
                    snackbar.setAction(actionMessage, actionListener);
                    snackbar.setDuration(Snackbar.LENGTH_INDEFINITE);
                }
                snackbar.show();
                break;
            case TYPE_TOAST:
                toast = Toast.makeText(ctx, message, Toast.LENGTH_LONG);
                toast.show();
                break;
            default:
                throw new IllegalArgumentException("Wrong message type");
        }
    }
}
