package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.ParsedState;
import org.openhab.habdroid.model.Widget;
import org.openhab.habdroid.util.Util;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.Locale;

public class MapViewHelper {
    private static final String TAG = MapViewHelper.class.getSimpleName();

    public static WidgetAdapter.ViewHolder createViewHolder(LayoutInflater inflater,
            ViewGroup parent, Connection connection, WidgetAdapter.ColorMapper colorMapper) {
        Context context = inflater.getContext();
        Configuration.getInstance().load(context,
                PreferenceManager.getDefaultSharedPreferences(context));
        return new OsmViewHolder(inflater, parent, connection, colorMapper);
    }

    private static class OsmViewHolder extends WidgetAdapter.LabeledItemBaseViewHolder
            implements Marker.OnMarkerDragListener {
        private final MapView mMapView;
        private final Handler mHandler;
        private final int mRowHeightPixels;
        private Item mBoundItem;
        private boolean mStarted;

        public OsmViewHolder(LayoutInflater inflater, ViewGroup parent,
                Connection connection, WidgetAdapter.ColorMapper colorMapper) {
            super(inflater, parent, R.layout.openhabwidgetlist_mapitem, connection, colorMapper);
            mHandler = new Handler();
            mMapView = itemView.findViewById(R.id.mapview);
            mMapView.setTileSource(TileSourceFactory.MAPNIK);

            mMapView.setVerticalMapRepetitionEnabled(false);
            mMapView.getOverlays().add(new CopyrightOverlay(itemView.getContext()));
            mMapView.setBuiltInZoomControls(false);
            mMapView.setMultiTouchControls(false);
            mMapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    openPopup();
                    return true;
                }

                @Override
                public boolean longPressHelper(GeoPoint p) {
                    return false;
                }
            }));

            applyPositionAndLabelWhenReady(mMapView, 15.0f, false, false);

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
            applyPositionAndLabelWhenReady(mMapView, 15.0f, false, false);
        }

        @Override
        public void start() {
            super.start();
            if (!mStarted) {
                mMapView.onResume();
                mStarted = true;
            }
        }

        @Override
        public void stop() {
            super.stop();
            if (mStarted) {
                mMapView.onPause();
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
                    marker.getPosition().getLatitude(), marker.getPosition().getLongitude());
            String item = marker.getId();
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), item, newState);
        }

        private void openPopup() {
            final MapView mapView = new MapView(itemView.getContext());

            AlertDialog dialog = new AlertDialog.Builder(itemView.getContext())
                    .setView(mapView)
                    .setCancelable(true)
                    .setNegativeButton(R.string.close, null)
                    .create();

            dialog.setOnDismissListener(dialogInterface -> mapView.onPause());
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);
            mapView.setVerticalMapRepetitionEnabled(false);
            mapView.getOverlays().add(new CopyrightOverlay(itemView.getContext()));
            mapView.onResume();
            applyPositionAndLabelWhenReady(mapView, 16.0f, true, true);
        }

        private void applyPositionAndLabelWhenReady(MapView mapView, float zoomLevel,
                boolean allowDrag, boolean allowScroll) {
            mHandler.post(() -> applyPositionAndLabel(mapView, zoomLevel, allowDrag, allowScroll));
        }

        private void applyPositionAndLabel(MapView mapView, float zoomLevel, boolean allowDrag,
                boolean allowScroll) {
            if (mBoundItem == null) {
                return;
            }
            boolean canDragMarker = allowDrag && !mBoundItem.readOnly();
            if (!mBoundItem.members().isEmpty()) {
                ArrayList<GeoPoint> positions = new ArrayList<>();
                for (Item item : mBoundItem.members()) {
                    GeoPoint position = toGeoPoint(item.state());
                    if (position != null) {
                        setMarker(mapView, position, item, item.label(), canDragMarker,
                                this);
                        positions.add(position);
                    }
                }

                if (!positions.isEmpty()) {
                    double north = -90;
                    double south = 90;
                    double west = 180;
                    double east = -180;
                    for (GeoPoint position : positions) {
                        north = Math.max(position.getLatitude(), north);
                        south = Math.min(position.getLatitude(), south);

                        west = Math.min(position.getLongitude(), west);
                        east = Math.max(position.getLongitude(), east);
                    }

                    Log.d(TAG, String.format("North %f, south %f, west %f, east %f",
                            north, south, west, east));
                    BoundingBox boundingBox = new BoundingBox(north, east, south, west);
                    int extraPixel = (int) convertDpToPixel(24f, mapView.getContext());
                    try {
                        mapView.zoomToBoundingBox(boundingBox, false, extraPixel);
                    } catch (Exception e) {
                        Log.d(TAG, "Error applying markers", e);
                    }
                    if (!allowScroll) {
                        mapView.setScrollableAreaLimitLongitude(west, east, extraPixel);
                        mapView.setScrollableAreaLimitLatitude(north, south, extraPixel);
                    }
                }
            } else {
                GeoPoint position = toGeoPoint(mBoundItem.state());
                if (position != null) {
                    setMarker(mapView, position, mBoundItem, mLabelView.getText(), canDragMarker,
                            this);
                    moveCamera(mapView, zoomLevel, position);
                    if (!allowScroll) {
                        mapView.setScrollableAreaLimitLatitude(position.getLatitude(),
                                position.getLatitude(), 0);
                        mapView.setScrollableAreaLimitLongitude(position.getLongitude(),
                                position.getLongitude(), 0);
                    }
                }
            }
        }

        /**
         * This method converts dp unit to equivalent pixels, depending on device density.
         *
         * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
         * @param context Context to get resources and device specific display metrics
         * @return A float value to represent px equivalent to dp depending on device density
         */
        private static float convertDpToPixel(float dp, Context context){
            return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        }

        private static void moveCamera(MapView mapView, float zoom, GeoPoint geoPoint) {
            IMapController mapController = mapView.getController();
            mapController.setZoom(zoom);
            mapController.setCenter(geoPoint);
        }

        private static void setMarker(MapView mapView, GeoPoint position, Item item,
                CharSequence label, boolean canDrag,
                Marker.OnMarkerDragListener onMarkerDragListener) {
            Marker marker = new Marker(mapView);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setDraggable(canDrag);
            marker.setPosition(position);
            marker.setTitle(label != null ? label.toString() : null);
            marker.setId(item.link());
            marker.setOnMarkerDragListener(onMarkerDragListener);
            marker.setIcon(ContextCompat.getDrawable(mapView.getContext(),
                    R.drawable.ic_location_on_red_24dp));
            mapView.getOverlays().add(marker);
        }

        private static GeoPoint toGeoPoint(ParsedState state) {
            Location location = state != null ? state.asLocation() : null;
            return location != null ? new GeoPoint(location) : null;
        }
    }
}