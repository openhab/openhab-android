package org.openhab.habdroid.ui.homescreenwidget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import androidx.annotation.LayoutRes;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.MainActivity;

public class VoiceWidgetWithIcon extends VoiceWidget {
    @Override
    void setupOpenhabIcon(Context context, RemoteViews views) {
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(context, 8, mainIntent, 0);
        views.setOnClickPendingIntent(R.id.btn_open_main, mainPendingIntent);
    }

    @Override
    @LayoutRes int getLayoutRes() {
        return R.layout.widget_voice_with_icon;
    }
}
