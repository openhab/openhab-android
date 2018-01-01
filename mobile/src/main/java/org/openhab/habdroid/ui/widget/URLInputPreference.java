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

import org.openhab.habdroid.R;

import java.net.MalformedURLException;
import java.net.URL;

public class URLInputPreference extends EditTextPreference implements TextWatcher {
    private EditText mEditor;
    private boolean mUrlIsValid;

    public URLInputPreference(Context context) {
        super(context);
    }

    public URLInputPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public URLInputPreference(Context context, AttributeSet attrs, int defStyleAttr) {
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
        if (TextUtils.isEmpty(editable)) {
            mUrlIsValid = true;
        } else {
            String url = editable.toString();
            if (url.contains("\n") || url.contains(" ")) {
                mUrlIsValid = false;
            } else {
                try {
                    new URL(url);
                    mUrlIsValid = true;
                } catch (MalformedURLException e) {
                    mUrlIsValid = false;
                }
            }
        }
        mEditor.setError(mUrlIsValid ? null : mEditor.getResources().getString(R.string.error_invalid_url));
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
