package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.Widget;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Locale;

public class MapViewHelper {
    public static WidgetAdapter.ViewHolder createViewHolder(LayoutInflater inflater,
            ViewGroup parent, Connection connection, WidgetAdapter.ColorMapper colorMapper) {
        MapsInitializer.initialize(inflater.getContext());
        return new GoogleMapsViewHolder(inflater, parent, connection, colorMapper);
    }

    private static class GoogleMapsViewHolder extends WidgetAdapter.LabeledItemBaseViewHolder
            implements GoogleMap.OnMarkerDragListener {
        private final MapView mMapView;
        private final int mRowHeightPixels;
        private GoogleMap mMap;
        private Item mBoundItem;
        private boolean mStarted;

        public GoogleMapsViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection connection, WidgetAdapter.ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_mapitem, connection, colorMapper);

            mMapView = itemView.findViewById(R.id.mapview);

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

            final Resources res = itemView.getContext().getResources();
            mRowHeightPixels = res.getDimensionPixelSize(R.dimen.row_height);
        }

        @Override
        public void bind(Widget widget) {
            super.bind(widget);

            ViewGroup.LayoutParams lp = mMapView.getLayoutParams();
            int rows = widget.height() > 0 ? widget.height() : 5;
            int desiredHeightPixels = rows * mRowHeightPixels;
            if (lp.height != desiredHeightPixels) {
                lp.height = desiredHeightPixels;
                mMapView.setLayoutParams(lp);
            }

            mBoundItem = widget.item();
            if (mMap != null) {
                mMap.clear();
                applyPositionAndLabel(mMap, 15.0f, false);
            }
        }

        @Override
        public void start() {
            super.start();
            if (!mStarted) {
                mMapView.onStart();
                mMapView.onResume();
                mStarted = true;
            }
        }

        @Override
        public void stop() {
            super.stop();
            if (mStarted) {
                mMapView.onPause();
                mMapView.onStop();
                mStarted = false;
            }
        }

        @Override
        public void onMarkerDragStart(Marker marker) {
            // no-op, we're interested in drag end only
        }

        @Override
        public void onMarkerDrag(Marker marker) {
            // no-op, we're interested in drag end only
        }

        @Override
        public void onMarkerDragEnd(Marker marker) {
            String newState = String.format(Locale.US, "%f,%f",
                    marker.getPosition().latitude, marker.getPosition().longitude);
            Item item = (Item) marker.getTag();
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), item, newState);
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
                map.setOnMarkerDragListener(GoogleMapsViewHolder.this);
                applyPositionAndLabel(map, 16.0f, true);
            });
        }

        private void applyPositionAndLabel(GoogleMap map, float zoomLevel, boolean allowDrag) {
            boolean canDragMarker = allowDrag && !mBoundItem.readOnly();
            if (!mBoundItem.members().isEmpty()) {
                ArrayList<LatLng> positions = new ArrayList<>();
                for (Item item : mBoundItem.members()) {
                    LatLng position = parseLocation(item.state());
                    if (position != null) {
                        setMarker(map, position, item, item.label(), canDragMarker);
                        positions.add(position);
                    }
                }
                if (!positions.isEmpty()) {
                    LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                    for (LatLng position : positions) {
                        boundsBuilder.include(position);
                    }
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 0));
                    float zoom = map.getCameraPosition().zoom;
                    if (zoom > zoomLevel) {
                        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel));
                    }
                }
            } else {
                LatLng position = parseLocation(mBoundItem.state());
                if (position != null) {
                    setMarker(map, position, mBoundItem, mLabelView.getText(), canDragMarker);
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoomLevel));
                }
            }
        }

        private static void setMarker(GoogleMap map, LatLng position, Item item,
                CharSequence label, boolean canDrag) {
            MarkerOptions marker = new MarkerOptions()
                    .draggable(canDrag)
                    .position(position)
                    .title(label != null ? label.toString() : null);
            map.addMarker(marker).setTag(item);
        }

        private static LatLng parseLocation(String state) {
            String[] splitState = state != null ? state.split(",") : null;
            if (splitState != null && splitState.length == 2) {
                try {
                    return new LatLng(Float.valueOf(splitState[0]), Float.valueOf(splitState[1]));
                } catch (NumberFormatException e) {
                    // ignored
                }
            }
            return null;
        }
    }
}