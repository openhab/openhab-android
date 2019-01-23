package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.TooltipCompat;

public class HelpIconShowingPreferenceUtil {
    public static void setupHelpIcon(Context context, ImageView helpIcon, boolean isEnabled,
            @StringRes int url, @StringRes int contentDescription) {
        final Uri howToUri = Uri.parse(context.getString(url));
        final Intent intent = new Intent(Intent.ACTION_VIEW, howToUri);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            helpIcon.setOnClickListener(v -> context.startActivity(intent));
            helpIcon.setContentDescription(context.getString(contentDescription));
            TooltipCompat.setTooltipText(helpIcon, context.getString(contentDescription));
            updateHelpIconAlpha(helpIcon, isEnabled);
        } else {
            helpIcon.setVisibility(View.GONE);
        }
    }

    public static void updateHelpIconAlpha(ImageView helpIcon, boolean isEnabled) {
        if (helpIcon != null) {
            helpIcon.setAlpha(isEnabled ? 1.0f : 0.5f);
        }
    }
}
