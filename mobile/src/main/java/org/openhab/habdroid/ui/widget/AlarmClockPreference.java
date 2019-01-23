package org.openhab.habdroid.ui.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;

import org.openhab.habdroid.R;

public class AlarmClockPreference extends EditTextPreference implements TextWatcher {
    private EditText mEditor;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public AlarmClockPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWidgetLayoutResource(R.layout.help_icon_pref);
    }

    public AlarmClockPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWidgetLayoutResource(R.layout.help_icon_pref);
    }

    public AlarmClockPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.help_icon_pref);
    }

    public AlarmClockPreference(Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.help_icon_pref);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        ImageView helpIcon = view.findViewById(R.id.help_icon);
        HelpIconShowingPreferenceUtil.setupHelpIcon(getContext(), helpIcon, isEnabled(),
                R.string.settings_alarm_clock_howto_url,
                R.string.settings_alarm_clock_howto_summary);

        return view;
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);
        mEditor = editText;
        editText.addTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // no-op
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // no-op
    }

    @Override
    public void afterTextChanged(Editable s) {
        String value = s.toString();
        boolean valid = true;
        if (TextUtils.isEmpty(value.trim()) || value.contains(" ") || value.contains("\n")) {
            valid = false;
        }
        mEditor.setError(valid ? null
                : getContext().getString(R.string.error_sending_alarm_clock_item_empty));
        updateOkButtonState(valid);
    }

    private void updateOkButtonState(boolean valid) {
        if (getDialog() instanceof AlertDialog) {
            AlertDialog dialog = (AlertDialog) getDialog();
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (okButton != null) {
                okButton.setEnabled(valid);
            }
        }
    }
}
