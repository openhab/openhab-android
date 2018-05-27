package org.openhab.habdroid.ui;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OpenHABBleAdapter extends RecyclerView.Adapter<OpenHABBleAdapter.ViewHolder>{

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView mac;
        TextView rssi;
        TextView txPower;
        TextView distance;
        TextView type;
        TextView url;


        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.id_name);
            mac = itemView.findViewById(R.id.id_mac);
            rssi = itemView.findViewById(R.id.id_rssi);
            txPower = itemView.findViewById(R.id.id_tx_power);
            distance = itemView.findViewById(R.id.id_distance);
            url = itemView.findViewById(R.id.id_url);
            type = itemView.findViewById(R.id.id_type);
        }
    }

    List<OpenHABBeacon> mOpenHABBeacons;


    public OpenHABBleAdapter() {
        mOpenHABBeacons = new ArrayList<>();
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
        holder.name.setText("Name: " + item.name());
        holder.mac.setText("MAC: " + item.address());
        holder.type.setText("Type: " + item.type());
        holder.distance.setText("Distance: " + String.format(Locale.ENGLISH, "%.2f", item.distance()) + " m");
        holder.txPower.setText(" TX power: " + String.format(Locale.ENGLISH, "%d", item.txPower()) + " dBm");
        holder.rssi.setText(" RSSI: " + String.format(Locale.ENGLISH, "%d", item.rssi()) + " dBm");
        if (item.url() != null) {
            holder.url.setText("URL: " + item.url());
        } else {
            holder.url.setText("URL not available");
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

    public void removeBeacon(OpenHABBeacon beacon){
        mOpenHABBeacons.remove(beacon);
        //notifyItemRemoved(0);
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
