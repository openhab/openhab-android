package org.openhab.habdroid.ui.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SwitchCompat;

import org.openhab.habdroid.R;

public class ItemUpdatingPreference extends DialogPreference implements
        TextWatcher, SwitchCompat.OnCheckedChangeListener {
    private String mHowtoHint;
    private String mHowtoUrl;
    private String mSummaryOn;
    private String mSummaryOff;
    private Pair<Boolean, String> mValue;

    private ImageView mHelpIcon;
    private SwitchCompat mSwitch;
    private TextInputLayout mEditorWrapper;
    private EditText mEditor;
    private Button mOkButton;

    public ItemUpdatingPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.ItemUpdatingPreference);
            initAttributes(a);
            a.recycle();
        }
    }

    public ItemUpdatingPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.ItemUpdatingPreference);
            initAttributes(a);
            a.recycle();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ItemUpdatingPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.ItemUpdatingPreference, defStyleAttr, defStyleRes);
            initAttributes(a);
            a.recycle();
        }
    }

    private void initAttributes(TypedArray attrs) {
        mHowtoHint = attrs.getString(R.styleable.ItemUpdatingPreference_helpHint);
        mHowtoUrl = attrs.getString(R.styleable.ItemUpdatingPreference_helpUrl);
        mSummaryOn = attrs.getString(R.styleable.ItemUpdatingPreference_summaryEnabled);
        mSummaryOff = attrs.getString(R.styleable.ItemUpdatingPreference_summaryDisabled);

        setDialogTitle(null);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            mValue = parseValue(getPersistedString(null));
        } else {
            mValue = (Pair<Boolean, String>) defaultValue;
            // XXX: persist if not yet present
        }
        updateSummary();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return parseValue(a.getString(index));
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v = inflater.inflate(R.layout.item_updating_pref_dialog, null);

        mSwitch = v.findViewById(R.id.enabled);
        mSwitch.setOnCheckedChangeListener(this);
        mEditor = v.findViewById(R.id.itemName);
        mEditor.addTextChangedListener(this);
        mEditorWrapper = v.findViewById(R.id.itemNameWrapper);

        TextView label = v.findViewById(R.id.enabledLabel);
        label.setText(getTitle());

        if (mValue != null) {
            mSwitch.setChecked(mValue.first);
            mEditor.setText(mValue.second);
        }

        mHelpIcon = v.findViewById(R.id.help_icon);
        HelpIconShowingPreferenceUtil.setupHelpIcon(getContext(),
                mHelpIcon, mHowtoUrl, mHowtoHint);

        onCheckedChanged(mSwitch, mSwitch.isChecked());

        return v;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        if (getDialog() instanceof AlertDialog) {
            AlertDialog dialog = (AlertDialog) getDialog();
            mOkButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        }
        updateOkButtonState();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            Pair<Boolean, String> newValue = Pair.create(
                    mSwitch.isChecked(), mEditor.getText().toString());
            if (callChangeListener(newValue)) {
                if (shouldPersist()) {
                    String persistedValue = Boolean.valueOf(newValue.first)
                            + "|" + newValue.second;
                    persistString(persistedValue);
                }
                mValue = newValue;
                updateSummary();
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        mEditorWrapper.setEnabled(checked);
        mEditorWrapper.setEnabled(checked);
        HelpIconShowingPreferenceUtil.updateHelpIconAlpha(mHelpIcon, checked);
        updateOkButtonState();
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
            mEditor.setError(
                    getContext().getString(R.string.error_sending_alarm_clock_item_empty));
        } else {
            mEditor.setError(null);
        }
        updateOkButtonState();
    }

    private void updateOkButtonState() {
        if (mEditor != null && mOkButton != null) {
            mOkButton.setEnabled(!mEditor.isEnabled() || mEditor.getError() == null);
        }
    }

    private void updateSummary() {
        if (mValue == null) {
            return;
        }
        String summary = mValue.first ? mSummaryOn : mSummaryOff;
        if (summary != null) {
            setSummary(String.format(summary, mValue.second));
        }
    }

    public static Pair<Boolean, String> parseValue(String value) {
        if (value == null) {
            return null;
        }
        int pos = value.indexOf('|');
        if (pos < 0) {
            return null;
        }
        return Pair.create(Boolean.parseBoolean(value.substring(0, pos)),
                value.substring(pos + 1));
    }
}
