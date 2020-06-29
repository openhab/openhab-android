/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui

import android.location.Location
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
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
import java.util.ArrayList
import java.util.Locale

object MapViewHelper {
    fun createViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        connection: Connection,
        colorMapper: WidgetAdapter.ColorMapper
    ): WidgetAdapter.ViewHolder {
        MapsInitializer.initialize(inflater.context)
        return GoogleMapsViewHolder(inflater, parent, connection, colorMapper)
    }

    private class GoogleMapsViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        connection: Connection,
        colorMapper: WidgetAdapter.ColorMapper
    ) : WidgetAdapter.AbstractMapViewHolder(inflater, parent, connection, colorMapper),
        GoogleMap.OnMarkerDragListener {
        private val mapView = baseMapView as MapView
        private var map: GoogleMap? = null
        override val dialogManager = WidgetAdapter.DialogManager()

        init {
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                this.map = map
                with(map.uiSettings) {
                    setAllGesturesEnabled(false)
                    isMapToolbarEnabled = false
                }
                map.setOnMarkerClickListener {
                    openPopup()
                    true
                }
                map.setOnMapClickListener { openPopup() }
            }
        }

        override fun bindAfterDataSaverCheck(widget: Widget) {
            map?.clear()
            map?.applyPositionAndLabel(boundItem, labelView.text, 15.0f, false)
        }

        override fun onStart() {
            super.onStart()
            mapView.onStart()
            mapView.onResume()
        }

        override fun onStop() {
            super.onStop()
            mapView.onPause()
            mapView.onStop()
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
            connection.httpClient.sendItemCommand(marker.tag as Item?, newState)
        }

        override fun openPopup() {
            val mapView = MapView(itemView.context)
            mapView.onCreate(null)

            val dialog = AlertDialog.Builder(itemView.context)
                    .setView(mapView)
                    .setCancelable(true)
                    .setNegativeButton(R.string.close, null)
                    .create()

            dialogManager.manage(dialog)
            with(dialog) {
                setOnDismissListener {
                    mapView.onPause()
                    mapView.onStop()
                    mapView.onDestroy()
                }
                dialog.setCanceledOnTouchOutside(true)
                dialog.show()
                window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
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

fun Location.toMapsUrl(): String? {
    return "https://www.google.de/maps/@$latitude,$longitude,16z"
}
