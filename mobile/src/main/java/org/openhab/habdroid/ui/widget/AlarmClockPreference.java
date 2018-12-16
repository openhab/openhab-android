package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.widget.TooltipCompat;

import org.openhab.habdroid.R;

public class AlarmClockPreference extends EditTextPreference {
    private ImageView mHelpIcon;

    public AlarmClockPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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

        final Context context = getContext();
        final Uri howToUri = Uri.parse(
                context.getString(R.string.settings_alarm_clock_howto_url));
        final Intent intent = new Intent(Intent.ACTION_VIEW, howToUri);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            mHelpIcon.setOnClickListener(v -> context.startActivity(intent));
            mHelpIcon.setContentDescription(
                    context.getString(R.string.settings_alarm_clock_howto_summary));
            TooltipCompat.setTooltipText(mHelpIcon,
                    context.getString(R.string.settings_alarm_clock_howto_summary));
            updateHelpIconAlpha();
        } else {
            mHelpIcon.setVisibility(View.GONE);
        }

        return view;
    }

    private void updateHelpIconAlpha() {
        if (mHelpIcon != null) {
            mHelpIcon.setAlpha(isEnabled() ? 1.0f : 0.5f);
        }
    }
}
