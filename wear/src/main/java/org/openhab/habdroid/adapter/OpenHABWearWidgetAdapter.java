package org.openhab.habdroid.adapter;

import android.content.Context;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABWidget;

import java.util.List;

/**
 * Created by tamon on 17.03.15.
 */
public class OpenHABWearWidgetAdapter extends WearableListView.Adapter {

    private static final String TAG = OpenHABWearWidgetAdapter.class.getSimpleName();

    private final Context mContext;
    private final LayoutInflater mInflater;
    private List<OpenHABWidget> mWidgets;

    // Provide a suitable constructor (depends on the kind of dataset)
    public OpenHABWearWidgetAdapter(Context context, List<OpenHABWidget> widgets) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mWidgets = widgets;
    }

    // Provide a reference to the type of views you're using
    public static class ItemViewHolder extends WearableListView.ViewHolder {
        private TextView textView;
        private TextView widgetNameText;
        private TextView linkArrow;
        private ImageView itemIcon;

        public ItemViewHolder(View itemView) {
            super(itemView);
            // find the text view within the custom item's layout
            textView = (TextView) itemView.findViewById(R.id.name);
            widgetNameText = (TextView) itemView.findViewById(R.id.widgetName);
            linkArrow = (TextView) itemView.findViewById(R.id.linkArrow);
            itemIcon = (ImageView) itemView.findViewById(R.id.circle);
        }
    }

    // Create new views for list items
    // (invoked by the WearableListView's layout manager)
    @Override
    public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                          int viewType) {
        // Inflate our custom layout for list items
        return new ItemViewHolder(mInflater.inflate(R.layout.list_item, null));
    }

    // Replace the contents of a list item
    // Instead of creating new views, the list tries to recycle existing ones
    // (invoked by the WearableListView's layout manager)
    @Override
    public void onBindViewHolder(WearableListView.ViewHolder holder,
                                 int position) {
        // retrieve the text view
        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        TextView view = itemHolder.textView;
        TextView widgetName = itemHolder.widgetNameText;
        TextView link = itemHolder.linkArrow;
        ImageView itemIcon = itemHolder.itemIcon;

        OpenHABWidget widget = mWidgets.get(position);
        view.setText(widget.getLabel());
        String widgetType = "" + widget.getType();
        Log.d(TAG, "Setting widgetType " + widgetType);
        if (widget.getItem() != null) {
            String itemType = widget.getItem().getType();
            Log.d(TAG, "Or we can use the item type " + itemType);
            if ("RollershutterItem".equals(itemType)) {
                widgetType = "Shutter";
            }
        }
        if ("Slider".equals(widgetType)) {
            itemIcon.setImageResource(R.drawable.slider_icon);
        } else if ("Switch".equals(widgetType)) {
            itemIcon.setImageResource(R.drawable.switch_icon);
        } else if ("Shutter".equals(widgetType)) {
            itemIcon.setImageResource(R.drawable.shutter_icon);
        } else {
            itemIcon.setImageResource(R.drawable.group_icon);
        }
        widgetName.setText(widgetType);
        if (widget.getType().equals("Text")) {
            link.setVisibility(View.GONE);
            if (widget.getItem() != null && widget.getItem().getState() != null) {
                if (!widget.getLabel().toLowerCase().contains(widget.getItem().getState().toLowerCase())) {
                    view.setText(widget.getLabel() + " " + widget.getItem().getState());
                }
            }
        } else {
            link.setVisibility(View.VISIBLE);
        }

        // replace list item's metadata
        holder.itemView.setTag(position);
    }

    // Return the size of your dataset
    // (invoked by the WearableListView's layout manager)
    @Override
    public int getItemCount() {
        return mWidgets.size();
    }

}
