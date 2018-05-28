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
import org.openhab.habdroid.model.OpenHABBeacon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class OpenHABBleAdapter extends RecyclerView.Adapter<OpenHABBleAdapter.ViewHolder>{

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView mName;
        TextView mMac;
        TextView mRssi;
        TextView mTxPower;
        TextView mDistance;
        TextView mType;
        TextView mExtra;

        public ViewHolder(View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.id_name);
            mMac = itemView.findViewById(R.id.id_mac);
            mRssi = itemView.findViewById(R.id.id_rssi);
            mTxPower = itemView.findViewById(R.id.id_tx_power);
            mDistance = itemView.findViewById(R.id.id_distance);
            mExtra = itemView.findViewById(R.id.id_extra);
            mType = itemView.findViewById(R.id.id_type);
        }
    }

    List<OpenHABBeacon> mOpenHABBeacons;


    public OpenHABBleAdapter() {
        mOpenHABBeacons = Collections.synchronizedList(new ArrayList<>());
    }

    @NonNull
    @Override
    public OpenHABBleAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.openhabblelist_beaconitem, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OpenHABBleAdapter.ViewHolder holder, int position) {
        OpenHABBeacon item = mOpenHABBeacons.get(position);
        Resources  resources = holder.itemView.getResources();
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
        return mOpenHABBeacons.size();
    }

    public void addBeacon(OpenHABBeacon beacon){
        int index = indexOf(beacon.address());
        if (index >= 0){
            mOpenHABBeacons.remove(index);
            notifyItemRemoved(index);
        }
        mOpenHABBeacons.add(0, beacon);
        notifyItemInserted(0);
    }

    private int indexOf(String address){
        for (int i = 0; i < mOpenHABBeacons.size(); i++){
            if (address.equals(mOpenHABBeacons.get(i).address())){
                return i;
            }
        }
        return -1;
    }
}
