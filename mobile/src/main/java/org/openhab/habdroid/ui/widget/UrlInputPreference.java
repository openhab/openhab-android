package org.openhab.habdroid.ui.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.StringRes;

import org.openhab.habdroid.R;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlInputPreference extends EditTextPreference implements TextWatcher {
    private EditText mEditor;
    private boolean mUrlIsValid;

    public UrlInputPreference(Context context) {
        super(context);
    }

    public UrlInputPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UrlInputPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);
        mEditor = editText;
        editText.addTextChangedListener(this);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        updateOkButtonState();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        boolean portSeemsInvalid = false;
        if (TextUtils.isEmpty(editable)) {
            mUrlIsValid = true;
        } else {
            String value = editable.toString();
            if (value.contains("\n") || value.contains(" ")) {
                mUrlIsValid = false;
            } else {
                try {
                    URL url = new URL(value);
                    mUrlIsValid = true;
                    if (url.getProtocol().equals("http")) {
                        portSeemsInvalid = url.getPort() == 443 || url.getPort() == 8443;
                    }
                    if (url.getProtocol().equals("https")) {
                        portSeemsInvalid = url.getPort() == 80 || url.getPort() == 8080;
                    }
                } catch (MalformedURLException e) {
                    mUrlIsValid = false;
                }
            }
        }
        @StringRes int error = 0;
        if (!mUrlIsValid) {
            error = R.string.error_invalid_url;
        } else if (portSeemsInvalid) {
            error = R.string.error_port_seems_invalid;
        }
        mEditor.setError(error == 0 ? null : mEditor.getResources().getString(error));
        updateOkButtonState();
    }

    private void updateOkButtonState() {
        if (getDialog() instanceof AlertDialog) {
            AlertDialog dialog = (AlertDialog) getDialog();
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (okButton != null) {
                okButton.setEnabled(mUrlIsValid);
            }
        }
    }
}
