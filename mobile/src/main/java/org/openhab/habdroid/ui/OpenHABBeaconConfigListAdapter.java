package org.openhab.habdroid.ui;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABBleService;
import org.openhab.habdroid.model.OpenHABBeacon;

import java.util.List;

public class OpenHABBeaconConfigListAdapter extends RecyclerView.Adapter<OpenHABBeaconConfigListAdapter.ViewHolder>
        implements OpenHABBleService.ConfigUiUpdateListener, View.OnClickListener {

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView mName;
        TextView mMac;
        TextView mRssi;
        TextView mTxPower;
        TextView mDistance;
        TextView mType;
        TextView mExtra;
        View mSpacer;
        View mDivider;

        public ViewHolder(View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.id_name);
            mMac = itemView.findViewById(R.id.id_mac);
            mRssi = itemView.findViewById(R.id.id_rssi);
            mTxPower = itemView.findViewById(R.id.id_tx_power);
            mDistance = itemView.findViewById(R.id.id_distance);
            mExtra = itemView.findViewById(R.id.id_extra);
            mType = itemView.findViewById(R.id.id_type);
            mSpacer = itemView.findViewById(R.id.spacer);
            mDivider = itemView.findViewById(R.id.divider);
        }

        public void setShownAsFirst(boolean shownAsFirst) {
            mDivider.setVisibility(shownAsFirst ? View.GONE : View.VISIBLE);
            mSpacer.setVisibility(shownAsFirst ? View.VISIBLE : View.GONE);
        }
    }

    public interface ItemClickListener {
        void onClick(int position);
    }

    private List<OpenHABBeacon> mBeaconList;
    private ItemClickListener mItemClickListener;

    public OpenHABBeaconConfigListAdapter(ItemClickListener listener) {
        mItemClickListener = listener;
    }

    @NonNull
    @Override
    public OpenHABBeaconConfigListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.openhabblelist_beaconitem, parent, false);
        ViewHolder holder = new ViewHolder(v);
        holder.itemView.setTag(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull OpenHABBeaconConfigListAdapter.ViewHolder holder, int position) {
        holder.setShownAsFirst(position == 0);
        OpenHABBeacon item = mBeaconList.get(position);
        Resources resources = holder.itemView.getResources();
        holder.itemView.setOnClickListener(this);
        holder.mName.setText(Html.fromHtml(resources.getString(R.string.beacon_name, item.name())));
        holder.mMac.setText(Html.fromHtml(resources.getString(R.string.beacon_address, item.address())));
        holder.mType.setText(Html.fromHtml(resources.getString(R.string.beacon_type, item.type())));
        holder.mDistance.setText(Html.fromHtml(resources.getString(R.string.beacon_distance, item.distance())));
        holder.mTxPower.setText(Html.fromHtml(resources.getString(R.string.beacon_tx_power, item.txPower())));
        holder.mRssi.setText(Html.fromHtml(resources.getString(R.string.beacon_rssi, item.rssi())));
        switch (item.type()){
            case iBeacon:
                holder.mExtra.setText(Html.fromHtml(resources.getString(R.string.ibeacon_properties
                        , item.uuid(), item.major(), item.minor())));
                break;
            case EddystoneUrl:
                holder.mExtra.setText(Html.fromHtml(resources.getString(R.string.eddystone_url, item.url())));
                break;
            case EddystoneUid:
                holder.mExtra.setText(Html.fromHtml(resources.getString(R.string.eddystone_uid
                        , item.nameSpace(), item.instance())));
                break;
            default:
                holder.mExtra.setText(Html.fromHtml(resources.getString(R.string.beacon_type_not_correct, item.type())));
        }
    }

    @Override
    public int getItemCount() {
        return mBeaconList == null ? 0 : mBeaconList.size();
    }

    @Override
    public void itemChange(int position) {
        notifyItemChanged(position);
    }

    @Override
    public void itemInsert(int position) {
        notifyItemInserted(position);
    }

    @Override
    public void bindItemList(List<OpenHABBeacon> beaconList) {
        mBeaconList = beaconList;
    }

    @Override
    public void onClick(View v) {
        ViewHolder holder = (ViewHolder)v.getTag();
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            mItemClickListener.onClick(position);
        }
    }
}
