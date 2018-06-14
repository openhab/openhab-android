package org.openhab.habdroid.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABBleService;
import org.openhab.habdroid.model.OpenHABBeacon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenHABBeaconAdapter extends RecyclerView.Adapter<OpenHABBeaconAdapter.ViewHolder>
        implements OpenHABBleService.ConfigUiUpdateListener{

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView mIcon;
        TextView mNameOrMac;
        TextView mFrame;
        TextView mDistance;

        public ViewHolder(View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.type_icon);
            mNameOrMac = itemView.findViewById(R.id.beacon_name_mac);
            mFrame = itemView.findViewById(R.id.frame_label);
            mDistance = itemView.findViewById(R.id.beacon_distance);
        }
    }

    private List<Map.Entry<String, ?>> mAddressFramePairList;
    private List<OpenHABBeacon> mBeaconList;

    public OpenHABBeaconAdapter(SharedPreferences sharedPreferences) {
        Map<String, ?> map = sharedPreferences.getAll();
        mAddressFramePairList = new ArrayList<>(map.entrySet());
    }

    @NonNull
    @Override
    public OpenHABBeaconAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.beacon_frame_pair_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OpenHABBeaconAdapter.ViewHolder holder, int position) {
        Map.Entry<String, ?> pair = mAddressFramePairList.get(position);
        String mac = pair.getKey();
        OpenHABBeacon beacon = getBeaconByAddress(mac);

        //TODO currently only set for beacon icon directly. Modify the logic to set icon for geo-fence later.
        holder.mIcon.setImageResource(R.drawable.ic_nfc_black_180dp);

        //Display name. Or mac if BLE service not available.
        String nameOrMac = beacon == null ? mac : beacon.name();
        holder.mNameOrMac.setText(nameOrMac);

        if (pair.getValue() != null) {
            holder.mFrame.setText((String) pair.getValue());
        } else {
            holder.mFrame.setText(R.string.frame_label_not_available);
        }

        String distance = beacon == null ? holder
                .mDistance.getContext().getString(R.string.beacon_no_name_found)
                : String.format("%.1f m", beacon.distance());
        holder.mDistance.setText(distance);
    }

    @Override
    public int getItemCount() {
        return mAddressFramePairList.size();
    }

    public void updateList(SharedPreferences sharedPreferences, OpenHABBleService bleService) {
        Map<String, ?> map = sharedPreferences.getAll();
        mAddressFramePairList = new ArrayList<>(map.entrySet());
        notifyItemRangeChanged(0, mAddressFramePairList.size());
    }

    @Override
    public void itemChange(int position) {
        int index;
        if ((index = findBeaconInPairList(position)) >= 0) {
            notifyItemChanged(index);
        }
    }

    @Override
    public void itemInsert(int position) {
        int index;
        if ((index = findBeaconInPairList(position)) >= 0) {
            notifyItemChanged(index);
        }
    }

    @Override
    public void bindItemList(List<OpenHABBeacon> beaconList) {
         mBeaconList = beaconList;
    }

    private OpenHABBeacon getBeaconByAddress(String address) {
        if (mBeaconList == null) {
            return null;
        }

        for (OpenHABBeacon beacon : mBeaconList) {
            if (beacon.address().equals(address)) {
                return beacon;
            }
        }
        return null;
    }

    private int findBeaconInPairList(int position) {
        String mac = mBeaconList.get(position).address();
        int size = mAddressFramePairList.size();
        for (int i = 0; i < size; i++) {
            String pairMac = mAddressFramePairList.get(i).getKey();
            if (mac.equals(pairMac)) {
                return i;
            }
        }
        return -1;
    }
}
