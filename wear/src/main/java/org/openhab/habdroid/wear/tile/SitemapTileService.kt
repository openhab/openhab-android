/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

package org.openhab.habdroid.wear.tile

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.dp
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import org.json.JSONException
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.connection.WearConnectionManager
import org.openhab.habdroid.wear.ui.WearMainActivity
import org.openhab.habdroid.wear.util.buildSourceId
import org.openhab.habdroid.wear.util.findWearSitemap
import org.openhab.habdroid.wear.util.loadPageWidgets
import org.openhab.habdroid.wear.wearApplication

/**
 * Colors derived from the openHAB orange, used if the watch doesn't provide dynamic colors
 */
private val OpenHabTileColorScheme = ColorScheme(
    primary = 0xFFFFB59D.toInt().argb,
    primaryDim = 0xFFF5A68B.toInt().argb,
    primaryContainer = 0xFF862200.toInt().argb,
    onPrimary = 0xFF5F1500.toInt().argb,
    onPrimaryContainer = 0xFFFFDBD0.toInt().argb
)

/**
 * Shows the first switches and texts of the sitemap selected for the watch. Switches can be toggled on the tile,
 * so users control which items are shown by the order of the widgets in the sitemap.
 */
class SitemapTileService : Material3TileService(defaultColorScheme = OpenHabTileColorScheme) {
    private data class TileContent(val title: String, val widgets: List<Widget>, val message: String?)

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val content = loadContent(requestParams.currentState.lastClickableId)
        val layout = primaryLayout(
            titleSlot = {
                text(content.title.layoutString)
            },
            mainSlot = {
                mainContent(content)
            },
            bottomSlot = {
                iconEdgeButton(
                    onClick = clickable(
                        launchAction(ComponentName(context, WearMainActivity::class.java))
                    ),
                    iconContent = {
                        icon(imageResource(androidImageResource(R.drawable.ic_openhab_monochrome_24dp)))
                    }
                )
            }
        )
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .build()
    }

    private fun MaterialScope.mainContent(content: TileContent): LayoutElement {
        if (content.message != null) {
            return text(
                text = content.message.layoutString,
                maxLines = 3,
                typography = Typography.BODY_MEDIUM
            )
        }
        // Two rows with two buttons each
        val rows = content.widgets.chunked(2).map { rowWidgets ->
            buttonGroup {
                rowWidgets.forEach { widget -> buttonGroupItem { widgetButton(widget) } }
            }
        }
        val elements = rows.flatMapIndexed { index, row ->
            buildList {
                if (index > 0) {
                    add(spacer(height = 4f.dp))
                }
                add(row)
            }
        }
        return column(*elements.toTypedArray(), width = expand())
    }

    private fun MaterialScope.widgetButton(widget: Widget): LayoutElement {
        val isSwitch = widget.isToggleable()
        val isOn = isSwitch && widget.state?.asBoolean == true
        val state = widget.stateFromLabel ?: widget.state?.asString?.takeIf { !isSwitch }
        return button(
            onClick = clickable(id = CLICKABLE_PREFIX + widget.id),
            labelContent = {
                text(
                    text = widget.label.layoutString,
                    maxLines = 1,
                    overflow = TEXT_OVERFLOW_ELLIPSIZE
                )
            },
            secondaryLabelContent = state?.let {
                {
                    text(
                        text = it.layoutString,
                        maxLines = 1,
                        overflow = TEXT_OVERFLOW_ELLIPSIZE
                    )
                }
            },
            width = expand(),
            height = expand(),
            colors = if (isOn) filledButtonColors() else filledTonalButtonColors()
        )
    }

    private suspend fun loadContent(clickedId: String): TileContent {
        val appName = getString(R.string.app_name)
        val connectionManager = wearApplication.connectionManager
        val connected = connectionManager.awaitConnection()
        if (connected == null) {
            val message = if (connectionManager.state.value is WearConnectionManager.State.NotConfigured) {
                getString(R.string.tile_not_configured)
            } else {
                getString(R.string.tile_not_connected)
            }
            return TileContent(appName, emptyList(), message)
        }
        val sitemap = connected.properties.findWearSitemap(connected.config.sitemapName)
            ?: return TileContent(appName, emptyList(), getString(R.string.error_no_sitemaps))

        val widgets = try {
            connected.connection.loadPageWidgets(sitemap.homepageLink)
        } catch (e: HttpClient.HttpException) {
            Log.w(TAG, "Loading ${sitemap.homepageLink} failed", e)
            return TileContent(sitemap.label, emptyList(), getString(R.string.tile_not_connected))
        } catch (e: JSONException) {
            Log.w(TAG, "Parsing ${sitemap.homepageLink} failed", e)
            return TileContent(sitemap.label, emptyList(), getString(R.string.tile_not_connected))
        }

        var tileWidgets = widgets
            .filter { widget -> widget.visibility && widget.isShownOnTile() }
            .take(MAX_WIDGETS)
        val clickedWidget = tileWidgets
            .firstOrNull { widget -> CLICKABLE_PREFIX + widget.id == clickedId }

        if (clickedWidget?.isToggleable() == true) {
            val command = if (clickedWidget.state?.asBoolean == true) "OFF" else "ON"
            Log.d(TAG, "Send $command to ${clickedWidget.item?.name}")
            connected.connection.httpClient
                .sendItemCommand(clickedWidget.item, command, buildSourceId(sitemap, sitemap.homepageLink))
                ?.join()
            // The server needs some time to update the state, so assume the command was successful
            tileWidgets = tileWidgets.map { widget ->
                if (widget.id == clickedWidget.id) widget.withSwitchState(command == "ON") else widget
            }
        }

        if (tileWidgets.isEmpty()) {
            return TileContent(sitemap.label, emptyList(), getString(R.string.tile_no_widgets))
        }
        return TileContent(sitemap.label, tileWidgets, null)
    }

    private fun Widget.isToggleable() = type == Widget.Type.Switch &&
        mappings.isEmpty() &&
        item?.isOfTypeOrGroupType(Item.Type.Rollershutter) != true

    private fun Widget.isShownOnTile() = isToggleable() ||
        (type in setOf(Widget.Type.Text, Widget.Type.Default) && !stateFromLabel.isNullOrEmpty())

    private fun Widget.withSwitchState(isOn: Boolean): Widget {
        val newState = state?.copy(asBoolean = isOn, asString = if (isOn) "ON" else "OFF")
        return copy(state = newState)
    }

    companion object {
        private val TAG = SitemapTileService::class.java.simpleName
        private const val RESOURCES_VERSION = "1"
        private const val CLICKABLE_PREFIX = "widget:"
        private const val MAX_WIDGETS = 4
        private const val FRESHNESS_INTERVAL_MILLIS = 15 * 60 * 1000L

        fun requestUpdate(context: Context) {
            TileService.getUpdater(context).requestUpdate(SitemapTileService::class.java)
        }
    }
}
