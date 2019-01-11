package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.openhab.habdroid.R;

public class AlarmClockPreference extends EditTextPreference {
    private ImageView mHelpIcon;

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

        mHelpIcon = view.findViewById(R.id.help_icon);
        HelpIconShowingPrefernceUtil.setupHelpIcon(getContext(), mHelpIcon, isEnabled(),
                R.string.settings_alarm_clock_howto_url,
                R.string.settings_alarm_clock_howto_summary);

        return view;
    }
}
