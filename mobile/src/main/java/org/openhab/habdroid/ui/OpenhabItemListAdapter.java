package org.openhab.habdroid.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;

import java.util.ArrayList;

/**
 * Created by redeye on 30.10.16.
 */
public class OpenhabItemListAdapter extends BaseAdapter{


        Context context;
        ArrayList<OpenHABItem> data;
        private static LayoutInflater inflater = null;

        public OpenhabItemListAdapter(Context context, ArrayList<OpenHABItem> data) {
            // TODO Auto-generated constructor stub
            this.context = context;
            this.data = data;
            inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            View vi = convertView;
            if (vi == null)
                vi = inflater.inflate(R.layout.openhabitemlist_item, null);
            TextView text = (TextView) vi.findViewById(R.id.text1);
            text.setText(data.get(position).getName());

            return vi;
        }

}
