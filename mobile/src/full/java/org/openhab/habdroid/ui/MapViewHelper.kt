/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isGone
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget

object MapViewHelper {
    fun createViewHolder(initData: WidgetAdapter.ViewHolderInitData): WidgetAdapter.ViewHolder {
        MapsInitializer.initialize(initData.inflater.context)
        return GoogleMapsViewHolder(initData)
    }

    private class GoogleMapsViewHolder(initData: WidgetAdapter.ViewHolderInitData) :
        WidgetAdapter.AbstractMapViewHolder(initData) {
        private val mapView = baseMapView as MapView
        private var map: GoogleMap? = null

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
            map?.applyPositionAndLabel(widget, 15.0f, false)
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

        override fun openPopup() {
            val widget = boundWidget ?: return
            fragmentPresenter.showBottomSheet(MapBottomSheet(), widget)
        }
    }
}

fun GoogleMap.applyPositionAndLabel(widget: Widget, zoomLevel: Float, allowDrag: Boolean) {
    if (widget.item == null) {
        return
    }
    val canDragMarker = allowDrag && !widget.item.readOnly
    if (widget.item.members.isNotEmpty()) {
        val positions = ArrayList<LatLng>()
        for (member in widget.item.members) {
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
        val position = widget.item.state?.asLocation?.toLatLng()
        if (position != null) {
            setMarker(position, widget.item, widget.label, canDragMarker)
            moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoomLevel))
        }
    }
}

fun GoogleMap.setMarker(position: LatLng, item: Item, label: CharSequence?, canDrag: Boolean) {
    val marker = MarkerOptions()
            .draggable(canDrag)
            .position(position)
            .title(label?.toString())
    addMarker(marker)?.tag = item
}

fun Location.toLatLng(): LatLng {
    return LatLng(latitude, longitude)
}

fun Location.toMapsUrl(): String {
    return "https://www.google.de/maps/@$latitude,$longitude,16z"
}

class MapBottomSheet : AbstractWidgetBottomSheet(), GoogleMap.OnMarkerDragListener {
    private lateinit var mapView: MapView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_map, container, false)
        val title = view.findViewById<TextView>(R.id.title)

        title.text = widget.label
        title.isGone = widget.label.isEmpty()

        mapView = view.findViewById(R.id.mapview)
        mapView.onCreate(null)

        return view
    }

    override fun onDestroyView() {
        mapView.onDestroy()
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        mapView.getMapAsync { map ->
            map.setOnMarkerDragListener(this@MapBottomSheet)
            map.applyPositionAndLabel(widget, 16.0f, true)
        }
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onMarkerDragStart(marker: Marker) {
        // no-op, we're interested in drag end only
    }

    override fun onMarkerDrag(marker: Marker) {
        // no-op, we're interested in drag end only
    }

    override fun onMarkerDragEnd(marker: Marker) {
        val newState = String.format(Locale.US, "%f,%f", marker.position.latitude, marker.position.longitude)
        connection?.httpClient?.sendItemCommand(marker.tag as Item?, newState)
    }
}
