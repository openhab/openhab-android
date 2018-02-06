package org.openhab.habdroid.core.message;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;

import org.openhab.habdroid.util.Constants;

public class MessageHandler {
    private static final String TAG = MessageHandler.class.getSimpleName();
    public static final int TYPE_DIALOG = 1;
    public static final int TYPE_SNACKBAR = 2;

    public static final int LOGLEVEL_DEBUG = 0;
    public static final int LOGLEVEL_NO_DEBUG = 4;
    public static final int LOGLEVEL_ALWAYS = 5;

    private Activity mActivity;
    private Snackbar snackbar;
    private AlertDialog alertDialog;

    public MessageHandler(Activity activity) {
        mActivity = activity;
    }

    public void closeAllMessages() {
        if (snackbar != null) {
            snackbar.dismiss();
        }

        if (alertDialog != null) {
            alertDialog.dismiss();
        }
    }

    public void showMessageToUser(String message, int messageType, int logLevel) {
        showMessageToUser(message, messageType, logLevel, 0, null);
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
    public void showMessageToUser(String message, int messageType, int logLevel,
            int actionMessage, @Nullable View.OnClickListener actionListener) {
        if (mActivity.isFinishing() || message == null) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean debugEnabled = settings.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false);

        // if debug mode is enabled, show all messages, except those with logLevel 4
        if((debugEnabled && logLevel == LOGLEVEL_NO_DEBUG) ||
                (!debugEnabled && logLevel == LOGLEVEL_DEBUG)) {
            return;
        }

        switch (messageType) {
            case TYPE_DIALOG:
                alertDialog = new AlertDialog.Builder(mActivity)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;
            case TYPE_SNACKBAR:
                snackbar = Snackbar.make(mActivity.findViewById(android.R.id.content),
                        message, Snackbar.LENGTH_LONG);
                if (actionListener != null && actionMessage != 0) {
                    snackbar.setAction(actionMessage, actionListener);
                    snackbar.setDuration(Snackbar.LENGTH_INDEFINITE);
                }
                snackbar.show();
                break;
            default:
                throw new IllegalArgumentException("Wrong message type");
        }
    }
}
