package org.openhab.habdroid.ui;

import android.content.Context;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.ui.widget.WidgetImageView;
import org.openhab.habdroid.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ItemPickerAdapter extends RecyclerView.Adapter<ItemPickerAdapter.ItemViewHolder>
        implements View.OnClickListener {
    public interface ItemClickListener {
        void onItemClicked(Item item);
    }

    private ArrayList<Item> mFilteredItems = new ArrayList<>();
    private ArrayList<Item> mAllItems = new ArrayList<>();
    private ItemClickListener mItemClickListener;
    private final LayoutInflater mInflater;
    private int mHighlightedPosition = -1;
    private static String mIconFormat;

    public ItemPickerAdapter(Context context, ItemClickListener itemClickListener) {
        super();
        mIconFormat = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Constants.PREFERENCE_ICON_FORMAT, "");
        mInflater = LayoutInflater.from(context);
        mItemClickListener = itemClickListener;
    }

    public void setItems(List<Item> items) {
        mFilteredItems.clear();
        mFilteredItems.addAll(items);
        Collections.sort(mFilteredItems, new ItemNameComparator());
        mAllItems.clear();
        mAllItems.addAll(mFilteredItems);
        notifyDataSetChanged();
    }

    public void filter(String filter) {
        mFilteredItems.clear();
        String searchTerm = filter.toLowerCase();
        for (Item item : mAllItems) {
            if (item.getName().toLowerCase().contains(searchTerm)
                    || item.getLabel().toLowerCase().contains(searchTerm)
                    || item.getType().toString().toLowerCase().contains(searchTerm)) {
                mFilteredItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    private class ItemNameComparator implements Comparator<Item> {
        public int compare(Item left, Item right) {
            return left.getName().compareToIgnoreCase(right.getName());
        }
    }

    public void clear() {
        mFilteredItems.clear();
        notifyDataSetChanged();
    }

    public int findPositionForName(String name) {
        for (int i = 0; i < mFilteredItems.size(); i++) {
            if (TextUtils.equals(mFilteredItems.get(i).getName(), name)) {
                return i;
            }
        }
        return -1;
    }

    public void highlightItem(int position) {
        mHighlightedPosition = position;
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ItemViewHolder(mInflater, parent);
    }

    @Override
    public void onBindViewHolder(ItemViewHolder holder, int position) {
        holder.bind(mFilteredItems.get(position));
        holder.itemView.setOnClickListener(mItemClickListener != null ? this : null);

        if (position == mHighlightedPosition) {
            final View v = holder.itemView;
            v.post(() -> {
                if (v.getBackground() != null) {
                    final int centerX = v.getWidth() / 2;
                    final int centerY = v.getHeight() / 2;
                    DrawableCompat.setHotspot(v.getBackground(), centerX, centerY);
                }
                v.setPressed(true);
                v.setPressed(false);
                mHighlightedPosition = -1;
            });
        }
    }

    @Override
    public int getItemCount() {
        return mFilteredItems.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        final TextView mItemNameView;
        final TextView mItemLabelView;
        final WidgetImageView mIconView;
        final TextView mItemTypeView;

        public ItemViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.itempickerlist_item, parent, false));
            mItemNameView = itemView.findViewById(R.id.itemName);
            mItemLabelView = itemView.findViewById(R.id.itemLabel);
            mItemTypeView = itemView.findViewById(R.id.itemType);
            mIconView = itemView.findViewById(R.id.itemIcon);
            itemView.setTag(this);
        }

        public void bind(Item item) {
            mItemNameView.setText(item.getName());
            mItemLabelView.setText(item.getLabel());
            mItemTypeView.setText(item.getType().toString());

            Connection connection;
            try {
                connection = ConnectionFactory.Companion.getUsableConnection();
            } catch (ConnectionException e) {
                connection = null;
            }
            if (item.getCategory() != null && connection != null) {
                String iconUrl = String.format(Locale.US, "images/%s.%s",
                        Uri.encode(item.getCategory()),
                        TextUtils.isEmpty(mIconFormat) ? "png" : mIconFormat);
                mIconView.setImageUrl(connection, iconUrl, mIconView.getResources()
                        .getDimensionPixelSize(R.dimen.notificationlist_icon_size), 2000);
            } else {
                mIconView.setImageResource(R.drawable.ic_openhab_appicon_24dp);
            }
        }
    }

    @Override
    public void onClick(View view) {
        ItemPickerAdapter.ItemViewHolder holder = (ItemPickerAdapter.ItemViewHolder) view.getTag();
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            mItemClickListener.onItemClicked(mFilteredItems.get(position));
        }
    }
}