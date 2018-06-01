package org.openhab.habdroid.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.GeofencingService;
import org.openhab.habdroid.model.OpenHABGeofence;
import org.openhab.habdroid.model.OpenHABItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class OpenHABGeofenceAdapter extends RecyclerView.Adapter<OpenHABGeofenceAdapter.GeofenceViewHolder> {
    private static final String TAG = OpenHABGeofenceAdapter.class.getSimpleName();
    private final ArrayList<OpenHABGeofence> mItems;
    private final LayoutInflater mInflater;
    private Activity mActivity;

    private ArrayList<OpenHABGeofenceAdapter.GeofenceViewHolder> selectedItems;

    public OpenHABGeofenceAdapter(Activity activity, ArrayList<OpenHABGeofence> items) {
        super();
        selectedItems = new ArrayList<>();
        mItems = items;
        mActivity = activity;
        mInflater = LayoutInflater.from(activity);
    }


    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public OpenHABGeofenceAdapter.GeofenceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new OpenHABGeofenceAdapter.GeofenceViewHolder(mInflater, parent);
    }

    public void selectItem(OpenHABGeofenceAdapter.GeofenceViewHolder holder) {
        selectedItems.add(holder);
        TypedValue value = new TypedValue();
        mActivity.getTheme().resolveAttribute(R.attr.colorAccent, value, true);
        holder.itemView.setBackgroundColor(value.data);
        if(selectedItems.size() > 1) {
            mActionMode.getMenu().findItem(R.id.copy).setVisible(false);
        }

    }

    public void deselectItem(OpenHABGeofenceAdapter.GeofenceViewHolder holder) {
        selectedItems.remove(holder);
        TypedValue value = new TypedValue();
        mActivity.getTheme().resolveAttribute(R.attr.backgroundColor, value, true);
        holder.itemView.setBackgroundColor(value.data);

        if(!(selectedItems.size() > 1)) {
            mActionMode.getMenu().findItem(R.id.copy).setVisible(true);
        }
    }

    @Override
    public void onBindViewHolder(OpenHABGeofenceAdapter.GeofenceViewHolder holder, int position) {
        OpenHABGeofence geofence = mItems.get(position);
        holder.setGeofence(geofence);
        holder.itemView.setOnLongClickListener(v -> {
            if (mActionMode != null) {
                return false;
            }

            selectItem(holder);
            // Start the CAB using the ActionMode.Callback defined above
            mActionMode = mActivity.startActionMode(mActionModeCallback);
            holder.itemView.setSelected(true);
            holder.itemView.setActivated(true);
            return true;
        });
        holder.itemView.setOnClickListener(v -> {
            if (mActionMode != null) {
                if (selectedItems.contains(holder))
                    deselectItem(holder);
                else  selectItem(holder);
                if(selectedItems.size() == 0)
                    mActionMode.finish();
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull GeofenceViewHolder holder) {
        super.onViewRecycled(holder);
    }

    private ActionMode mActionMode;
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.geofence_context_menu, menu);
            //menu.getItem(0).setIcon(R.drawable.ic_notifications_black_24dp);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    //shareCurrentItem();
                    List<OpenHABGeofence> geosForRemoval = new ArrayList<>(selectedItems.size());
                    for(OpenHABGeofenceAdapter.GeofenceViewHolder holder:selectedItems) {
                        geosForRemoval.add(holder.mGeofence);
                    }
                    GeofencingService.removeGeofences(mActivity,geosForRemoval);
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            while (selectedItems.size() > 0) deselectItem(selectedItems.get(0));
            mActionMode = null;
        }
    };


    public static class GeofenceViewHolder extends RecyclerView.ViewHolder implements GoogleMap.OnMarkerDragListener {
        private OpenHABGeofence mGeofence;

        private final MapView  mMapView     = itemView.findViewById(R.id.geofenceMapView);
        private final TextView mLabelView   = itemView.findViewById(R.id.geofencelabel);
        private final TextView mNameView    = itemView.findViewById(R.id.geofencename);
        private final TextView mRadiusView  = itemView.findViewById(R.id.geofenceradius);
        private final TextView mCoordsView  = itemView.findViewById(R.id.geofencecoordinates);

        private GoogleMap mMap;
        private boolean mStarted;


        public void setGeofence(OpenHABGeofence geofence) {
            this.mGeofence = geofence;
            mLabelView.setText(geofence.getLabel());
            mNameView .setText(geofence.getName());
            mRadiusView.setText(geofence.getRadius()+"m");
            mCoordsView.setText(geofence.getCoordinatesString());
        }

        public GeofenceViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.openhabgeofenceslist_item, parent, false));
           // itemView.set
            mMapView.onCreate(null);
            mMapView.getMapAsync(map -> {
                mMap = map;
                UiSettings settings = map.getUiSettings();
                settings.setAllGesturesEnabled(false);
                settings.setMapToolbarEnabled(false);
                map.setOnMarkerClickListener(marker -> { openPopup(); return true; });
                map.setOnMapClickListener(position -> openPopup());
                applyPositionAndLabel(map, 15.0f, false);
            });
            start();
        }

        //@Override
        public void start() {
            if (!mStarted) {
                mMapView.onStart();
                mMapView.onResume();
                mStarted = true;
            }
        }

        //@Override
        public void stop() {
            if (mStarted) {
                mMapView.onPause();
                mMapView.onStop();
                mStarted = false;
            }
        }

        private void openPopup() {
            final MapView mapView = new MapView(itemView.getContext());
            mapView.onCreate(null);

            AlertDialog dialog = new AlertDialog.Builder(itemView.getContext())
                    .setView(mapView)
                    .setCancelable(true)
                    .setNegativeButton(R.string.close, null)
                    .create();

            dialog.setOnDismissListener(dialogInterface -> {
                mapView.onPause();
                mapView.onStop();
                mapView.onDestroy();
            });
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            mapView.onStart();
            mapView.onResume();
            mapView.getMapAsync(map -> {
                map.setOnMarkerDragListener(GeofenceViewHolder.this);
                applyPositionAndLabel(map, 16.0f, true);
            });
        }

        private void applyPositionAndLabel(GoogleMap map, float zoomLevel, boolean allowDrag) {

            LatLng position = null;
            if (mGeofence != null)
                position = new LatLng(mGeofence.getLatitude(),mGeofence.getLongitude());
            if (position != null) {
                setMarker(map, position, null, mLabelView.getText(), false);
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoomLevel));
            }
        }

        private static void setMarker(GoogleMap map, LatLng position, OpenHABItem item,CharSequence label, boolean canDrag) {
            MarkerOptions marker = new MarkerOptions()
                    .draggable(canDrag)
                    .position(position)
                    .alpha(0.9f)
                    .title(label != null ? label.toString() : null);
            map.addMarker(marker).setTag(item);
        }

        @Override
        public void onMarkerDragStart(Marker marker) {

        }

        @Override
        public void onMarkerDrag(Marker marker) {

        }

        @Override
        public void onMarkerDragEnd(Marker marker) {
            String newState = String.format(Locale.US, "%f,%f",
                    marker.getPosition().latitude, marker.getPosition().longitude);
            OpenHABItem item = (OpenHABItem) marker.getTag();
        }
    }
}
