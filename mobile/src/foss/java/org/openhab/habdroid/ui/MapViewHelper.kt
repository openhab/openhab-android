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
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.dpToPixel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController.Visibility
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.ArrayList
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object MapViewHelper {
    internal val TAG = MapViewHelper::class.java.simpleName

    fun createViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        connection: Connection,
        colorMapper: WidgetAdapter.ColorMapper
    ): WidgetAdapter.ViewHolder {
        val context = inflater.context
        Configuration.getInstance().load(context,
                PreferenceManager.getDefaultSharedPreferences(context))
        return OsmViewHolder(inflater, parent, connection, colorMapper)
    }

    private class OsmViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        connection: Connection,
        colorMapper: WidgetAdapter.ColorMapper
    ) : WidgetAdapter.AbstractMapViewHolder(inflater, parent, connection, colorMapper),
        Marker.OnMarkerDragListener {
        private val mapView = baseMapView as MapView
        private val handler: Handler = Handler()
        override val dialogManager = WidgetAdapter.DialogManager()

        init {
            with(mapView) {
                setTileSource(TileSourceFactory.MAPNIK)
                isVerticalMapRepetitionEnabled = false
                zoomController.setVisibility(Visibility.NEVER)
                setMultiTouchControls(false)
                setDestroyMode(false)
                overlays.add(CopyrightOverlay(itemView.context))
                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        openPopup()
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        return false
                    }
                }))
            }
        }

        override fun bindAfterDataSaverCheck(widget: Widget) {
            handler.post {
                mapView.applyPositionAndLabel(boundItem, labelView.text, 15.0f,
                    allowDrag = false, allowScroll = false, markerDragListener = this)
            }
        }

        override fun onStart() {
            mapView.onResume()
        }

        override fun onStop() {
            mapView.onPause()
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
            val item = if (marker.id == boundItem?.name) {
                boundItem
            } else {
                boundItem?.members?.firstOrNull { i -> i.name == marker.id }
            }
            connection.httpClient.sendItemCommand(item, newState)
        }

        override fun openPopup() {
            val mapView = MapView(itemView.context)

            val dialog = AlertDialog.Builder(itemView.context)
                    .setView(mapView)
                    .setCancelable(true)
                    .setNegativeButton(R.string.close, null)
                    .create()

            dialogManager.manage(dialog)
            with(dialog) {
                setOnDismissListener { mapView.onPause() }
                setCanceledOnTouchOutside(true)
                show()
                window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }

            with(mapView) {
                zoomController.setVisibility(Visibility.SHOW_AND_FADEOUT)
                setMultiTouchControls(true)
                isVerticalMapRepetitionEnabled = false
                overlays.add(CopyrightOverlay(itemView.context))
                onResume()
            }
            handler.post {
                mapView.applyPositionAndLabel(boundItem, labelView.text, 16.0f,
                    allowDrag = true, allowScroll = true, markerDragListener = this@OsmViewHolder)
            }
        }
    }
}

fun MapView.applyPositionAndLabel(
    item: Item?,
    itemLabel: CharSequence?,
    zoomLevel: Float,
    allowDrag: Boolean,
    allowScroll: Boolean,
    markerDragListener: Marker.OnMarkerDragListener
) {
    if (item == null) {
        return
    }
    val canDragMarker = allowDrag && !item.readOnly
    if (item.members.isNotEmpty()) {
        val positions = ArrayList<GeoPoint>()
        for (member in item.members) {
            val position = member.state?.asLocation?.toGeoPoint()
            if (position != null) {
                setMarker(position, member, member.label, canDragMarker, markerDragListener)
                positions.add(position)
            }
        }

        if (positions.isNotEmpty()) {
            var north = -90.0
            var south = 90.0
            var west = 180.0
            var east = -180.0
            for (position in positions) {
                north = max(position.latitude, north)
                south = min(position.latitude, south)

                west = min(position.longitude, west)
                east = max(position.longitude, east)
            }

            Log.d(MapViewHelper.TAG, "North $north, south $south, west $west, east $east")
            val boundingBox = BoundingBox(north, east, south, west)
            val extraPixel = context.resources.dpToPixel(24f).toInt()
            try {
                zoomToBoundingBox(boundingBox, false, extraPixel)
            } catch (e: Exception) {
                Log.d(MapViewHelper.TAG, "Error applying markers", e)
            }

            if (!allowScroll) {
                setScrollableAreaLimitLongitude(west, east, extraPixel)
                setScrollableAreaLimitLatitude(north, south, extraPixel)
            }
        }
    } else {
        val position = item.state?.asLocation?.toGeoPoint()
        if (position != null) {
            setMarker(position, item, itemLabel, canDragMarker, markerDragListener)
            controller.setZoom(zoomLevel.toDouble())
            controller.setCenter(position)
            if (!allowScroll) {
                setScrollableAreaLimitLatitude(position.latitude, position.latitude, 0)
                setScrollableAreaLimitLongitude(position.longitude, position.longitude, 0)
            }
        } else if (!allowScroll) {
            setScrollableAreaLimitLatitude(0.0, 0.0, 0)
            setScrollableAreaLimitLongitude(0.0, 0.0, 0)
        }
    }
}

fun MapView.setMarker(
    pos: GeoPoint,
    item: Item,
    label: CharSequence?,
    canDrag: Boolean,
    onMarkerDragListener: Marker.OnMarkerDragListener
) {
    val marker = Marker(this).apply {
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        isDraggable = canDrag
        position = pos
        title = label?.toString()
        id = item.name
        setOnMarkerDragListener(onMarkerDragListener)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_location_on_red_24dp)
    }
    overlays.add(marker)
}

fun Location.toGeoPoint(): GeoPoint {
    return GeoPoint(this)
}

fun Location.toMapsUrl(): String? {
    return "https://www.openstreetmap.org/#map=16/$latitude/$longitude"
}
