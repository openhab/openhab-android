package org.openhab.habdroid.ui

import android.app.AlertDialog
import android.location.Location
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.Util
import java.util.*

object MapViewHelper {
    fun createViewHolder(inflater: LayoutInflater,
                         parent: ViewGroup, connection: Connection,
                         colorMapper: WidgetAdapter.ColorMapper): WidgetAdapter.ViewHolder {
        MapsInitializer.initialize(inflater.context)
        return GoogleMapsViewHolder(inflater, parent, connection, colorMapper)
    }

    private class GoogleMapsViewHolder(inflater: LayoutInflater, parent: ViewGroup,
                                       private val connection: Connection,
                                       colorMapper: WidgetAdapter.ColorMapper) :
            WidgetAdapter.LabeledItemBaseViewHolder(inflater, parent, R.layout.openhabwidgetlist_mapitem, connection, colorMapper),
            GoogleMap.OnMarkerDragListener {
        private val mapView: MapView = itemView.findViewById(R.id.mapview)
        private var map: GoogleMap? = null
        private var boundItem: Item? = null
        private var started: Boolean = false

        init {
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                this.map = map
                with (map.uiSettings) {
                    setAllGesturesEnabled(false)
                    isMapToolbarEnabled = false
                }
                map.setOnMarkerClickListener {
                    openPopup()
                    true
                }
                map.setOnMapClickListener { openPopup() }
                map.applyPositionAndLabel(boundItem, labelView.text, 15.0f, false)
            }

        }

        override fun bind(widget: Widget) {
            super.bind(widget)

            mapView.adjustForWidgetHeight(widget, 5)

            boundItem = widget.item
            map?.clear()
            map?.applyPositionAndLabel(boundItem, labelView.text, 15.0f, false)
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

            dialog.setOnDismissListener {
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
                map.applyPositionAndLabel(boundItem, labelView.text, 16.0f, true)
            }
        }
    }
}

fun GoogleMap.applyPositionAndLabel(item: Item?, itemLabel: CharSequence, zoomLevel: Float, allowDrag: Boolean) {
    if (item == null) {
        return
    }
    val canDragMarker = allowDrag && !item.readOnly
    if (item.members.isNotEmpty()) {
        val positions = ArrayList<LatLng>()
        for (member in item.members) {
            val position = member.state?.asLocation?.toLatLng()
            if (position != null) {
                setMarker(position, member, member.label, canDragMarker)
                positions.add(position)
            }
        }
        if (positions.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            for (position in positions) {
                boundsBuilder.include(position)
            }
            moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 0))
            if (cameraPosition.zoom > zoomLevel) {
                moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
            }
        }
    } else {
        val position = item.state?.asLocation?.toLatLng()
        if (position != null) {
            setMarker(position, item, itemLabel, canDragMarker)
            moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoomLevel))
        }
    }
}

fun GoogleMap.setMarker(position: LatLng, item: Item, label: CharSequence?, canDrag: Boolean) {
    val marker = MarkerOptions()
            .draggable(canDrag)
            .position(position)
            .title(label?.toString())
    addMarker(marker).tag = item
}

fun Location.toLatLng(): LatLng {
    return LatLng(latitude, longitude)
}
