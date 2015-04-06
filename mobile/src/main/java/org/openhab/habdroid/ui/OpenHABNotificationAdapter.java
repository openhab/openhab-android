package org.openhab.habdroid.ui;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABNotification;
import org.w3c.dom.Text;

import java.util.ArrayList;

/**
 * Created by belovictor on 03/04/15.
 */
public class OpenHABNotificationAdapter extends ArrayAdapter<OpenHABNotification> {
    int mResource;

    public OpenHABNotificationAdapter(Context context, int resource, ArrayList<OpenHABNotification> objects) {
        super(context, resource, objects);
        mResource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        OpenHABNotification notification = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mResource, parent, false);
        }
        TextView createdView = (TextView)convertView.findViewById(R.id.notificationCreated);
        TextView messageView = (TextView)convertView.findViewById(R.id.notificationMessage);
        createdView.setText(DateUtils.getRelativeDateTimeString(this.getContext(), notification.getCreated().getTime(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
        messageView.setText(notification.getMessage());
        return convertView;
    }
}
