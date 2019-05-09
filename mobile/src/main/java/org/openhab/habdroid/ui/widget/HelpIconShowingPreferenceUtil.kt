package org.openhab.habdroid.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat

object HelpIconShowingPreferenceUtil {
    fun setupHelpIcon(context: Context, helpIcon: ImageView,
                      url: String, contentDescription: String) {
        val howToUri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, howToUri)

        if (intent.resolveActivity(context.packageManager) != null) {
            helpIcon.setOnClickListener { v -> context.startActivity(intent) }
            helpIcon.contentDescription = contentDescription
            TooltipCompat.setTooltipText(helpIcon, contentDescription)
        } else {
            helpIcon.visibility = View.GONE
        }
    }

    fun updateHelpIconAlpha(helpIcon: ImageView?, isEnabled: Boolean) {
        if (helpIcon != null) {
            helpIcon.alpha = if (isEnabled) 1.0f else 0.5f
        }
    }
}
