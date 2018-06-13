package org.openhab.habdroid.ui;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABBleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenHABBeaconAdapter extends RecyclerView.Adapter<OpenHABBeaconAdapter.ViewHolder> {

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView mName;
        TextView mMac;
        TextView mFrame;

        public ViewHolder(View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.beacon_name);
            mMac = itemView.findViewById(R.id.beacon_mac);
            mFrame = itemView.findViewById(R.id.frame_label);
        }
    }

    private List<Map.Entry<String, ?>> mAddressFramePairList;
    private OpenHABBleService mBleService;

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

        //Display name. Or mac if BLE service not available.
        if (mBleService != null) {
            holder.mName.setText(mBleService.getNameByMac(mac));
        } else {
            holder.mMac.setText(mac);
        }
        if (pair.getValue() != null) {
            holder.mFrame.setText((String) pair.getValue());
        } else {
            holder.mFrame.setText(R.string.frame_label_not_available);
        }
    }

    @Override
    public int getItemCount() {
        return mAddressFramePairList.size();
    }

    public void updateList(SharedPreferences sharedPreferences, OpenHABBleService bleService) {
        Map<String, ?> map = sharedPreferences.getAll();
        mAddressFramePairList = new ArrayList<>(map.entrySet());
        mBleService = bleService;
        notifyItemRangeChanged(0, mAddressFramePairList.size());
    }
}
