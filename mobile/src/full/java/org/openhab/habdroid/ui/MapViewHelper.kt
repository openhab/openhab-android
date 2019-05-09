package org.openhab.habdroid.ui

import android.app.AlertDialog
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.ViewGroup

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.Util

import java.util.ArrayList
import java.util.Locale

object MapViewHelper {
    fun createViewHolder(inflater: LayoutInflater,
                         parent: ViewGroup, connection: Connection,
                         colorMapper: WidgetAdapter.ColorMapper): WidgetAdapter.ViewHolder {
        MapsInitializer.initialize(inflater.context)
        return GoogleMapsViewHolder(inflater, parent, connection, colorMapper)
    }

    private class GoogleMapsViewHolder(inflater: LayoutInflater, parent: ViewGroup,
                                       connection: Connection, colorMapper: WidgetAdapter.ColorMapper) :
            WidgetAdapter.LabeledItemBaseViewHolder(inflater, parent, R.layout.openhabwidgetlist_mapitem, connection, colorMapper),
            GoogleMap.OnMarkerDragListener {
        private val mapView: MapView
        private val rowHeightPixels: Int
        private var map: GoogleMap? = null
        private var boundItem: Item? = null
        private var started: Boolean = false

        init {
            mapView = itemView.findViewById(R.id.mapview)

            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                this.map = map
                val settings = map.uiSettings
                settings.setAllGesturesEnabled(false)
                settings.isMapToolbarEnabled = false
                map.setOnMarkerClickListener { marker ->
                    openPopup()
                    true
                }
                map.setOnMapClickListener { position -> openPopup() }
                applyPositionAndLabel(map, 15.0f, false)
            }

            rowHeightPixels = itemView.resources.getDimensionPixelSize(R.dimen.row_height)
        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            val lp = mapView.layoutParams
            val rows = if (widget.height > 0) widget.height else 5
            val desiredHeightPixels = rows * rowHeightPixels
            if (lp.height != desiredHeightPixels) {
                lp.height = desiredHeightPixels
                mapView.layoutParams = lp
            }

            boundItem = widget.item
            val map = map;
            if (map != null) {
                map.clear()
                applyPositionAndLabel(map, 15.0f, false)
            }
        }

        override fun start() {
            super.start()
            if (!started) {
                mapView.onStart()
                mapView.onResume()
                started = true
            }
        }

        override fun stop() {
            super.stop()
            if (started) {
                mapView.onPause()
                mapView.onStop()
                started = false
            }
        }

        override fun onMarkerDragStart(marker: Marker) {
            // no-op, we're interested in drag end only
        }

        override fun onMarkerDrag(marker: Marker) {
            // no-op, we're interested in drag end only
        }

        override fun onMarkerDragEnd(marker: Marker) {
            val newState = String.format(Locale.US, "%f,%f",
                    marker.position.latitude, marker.position.longitude)
            Util.sendItemCommand(connection.asyncHttpClient, marker.tag as Item?, newState)
        }

        private fun openPopup() {
            val mapView = MapView(itemView.context)
            mapView.onCreate(null)

            val dialog = AlertDialog.Builder(itemView.context)
                    .setView(mapView)
                    .setCancelable(true)
                    .setNegativeButton(R.string.close, null)
                    .create()

            dialog.setOnDismissListener { dialogInterface ->
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()

            mapView.onStart()
            mapView.onResume()
            mapView.getMapAsync { map ->
                map.setOnMarkerDragListener(this@GoogleMapsViewHolder)
                applyPositionAndLabel(map, 16.0f, true)
            }
        }

        private fun applyPositionAndLabel(map: GoogleMap, zoomLevel: Float, allowDrag: Boolean) {
            val item = boundItem
            if (item == null) {
                return
            }
            val canDragMarker = allowDrag && !item.readOnly
            if (!item.members.isEmpty()) {
                val positions = ArrayList<LatLng>()
                for (member in item.members) {
                    val position = toLatLng(member.state)
                    if (position != null) {
                        setMarker(map, position, member, member.label, canDragMarker)
                        positions.add(position)
                    }
                }
                if (!positions.isEmpty()) {
                    val boundsBuilder = LatLngBounds.Builder()
                    for (position in positions) {
                        boundsBuilder.include(position)
                    }
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 0))
                    val zoom = map.cameraPosition.zoom
                    if (zoom > zoomLevel) {
                        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
                    }
                }
            } else {
                val position = toLatLng(item.state)
                if (position != null) {
                    setMarker(map, position, item, labelView.text, canDragMarker)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoomLevel))
                }
            }
        }

        private fun setMarker(map: GoogleMap, position: LatLng, item: Item,
                              label: CharSequence?, canDrag: Boolean) {
            val marker = MarkerOptions()
                    .draggable(canDrag)
                    .position(position)
                    .title(label?.toString())
            map.addMarker(marker).tag = item
        }

        private fun toLatLng(state: ParsedState?): LatLng? {
            val location = state?.asLocation
            if (location == null) {
                return null
            }
            return LatLng(location.latitude, location.longitude)
        }
    }
}