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

package org.openhab.habdroid.background

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ControlTemplate
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.jdk9.flowPublish
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.toParsedState
import org.openhab.habdroid.ui.ColorItemActivity
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.SelectionItemActivity
import org.openhab.habdroid.util.DeviceControlSubtitleMode
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.PendingIntent_Immutable
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getDeviceControlSubtitle
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.orDefaultIfEmpty

@RequiresApi(Build.VERSION_CODES.R)
class ItemsControlsProviderService : ControlsProviderService() {
    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> = flowPublish {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return@flowPublish
        val allItems = loadItems(connection) ?: return@flowPublish
        val factory = ItemControlFactory(this@ItemsControlsProviderService, allItems, false)
        allItems
            .mapNotNull { factory.maybeCreateControl(it.value) }
            .forEach { control -> send(control) }
    }

    override fun createPublisherFor(itemNames: List<String>): Flow.Publisher<Control> = flowPublish {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return@flowPublish
        val allItems = loadItems(connection) ?: return@flowPublish
        val factory = ItemControlFactory(this@ItemsControlsProviderService, allItems, true)
        allItems.filterKeys { itemName -> itemName in itemNames }
            .mapNotNull { factory.maybeCreateControl(it.value) }
            .forEach { control -> send(control) }

        val eventSubscription = connection.httpClient.makeSse(
            // Support for both the "openhab" and the older "smarthome" root topic by using a wildcard
            connection.httpClient.buildUrl("rest/events?topics=*/items/*/statechanged")
        )

        try {
            while (isActive) {
                try {
                    val event = JSONObject(eventSubscription.getNextEvent())
                    if (event.optString("type") == "ALIVE") {
                        Log.d(TAG, "Got ALIVE event")
                        continue
                    }
                    val topic = event.getString("topic")
                    val topicPath = topic.split('/')
                    if (topicPath.size != 4) {
                        throw JSONException("Unexpected topic path $topic")
                    }
                    val item = allItems[topicPath[2]]
                    if (item != null) {
                        val payload = JSONObject(event.getString("payload"))
                        val newItem = item.copy(state = payload.getString("value").toParsedState())
                        factory.maybeCreateControl(newItem)?.let { control -> send(control) }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Failed parsing JSON of state change event", e)
                }
            }
        } finally {
            eventSubscription.cancel()
        }
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        GlobalScope.launch {
            ConnectionFactory.waitForInitialization()
            val connection = ConnectionFactory.primaryUsableConnection?.connection
            consumer.accept(performItemControl(connection, controlId, action))
        }
    }

    private suspend fun loadItems(connection: Connection?): Map<String, Item>? {
        if (connection == null) {
            Log.e(TAG, "Got no connection for loading items")
            return null
        }

        return try {
            val items = ItemClient.loadItems(connection) ?: emptyList()
            items.associateBy { item -> item.name }
        } catch (e: HttpClient.HttpException) {
            Log.e(TAG, "Could not load items", e)
            return null
        }
    }

    private suspend fun performItemControl(connection: Connection?, itemName: String, action: ControlAction): Int {
        if (connection == null) {
            return ControlAction.RESPONSE_FAIL
        }
        val state = when (action) {
            is BooleanAction -> {
                val item = ItemClient.loadItem(connection, itemName) ?: return ControlAction.RESPONSE_FAIL
                if (item.isOfTypeOrGroupType(Item.Type.Player)) {
                    if (action.newState) "PLAY" else "PAUSE"
                } else {
                    if (action.newState) "ON" else "OFF"
                }
            }
            is FloatAction -> action.newValue.roundToInt().toString()
            else -> {
                Log.e(TAG, "Unsupported action $action")
                return ControlAction.RESPONSE_FAIL
            }
        }
        return try {
            connection.httpClient
                .post("rest/items/$itemName", state)
                .asStatus()
            ControlAction.RESPONSE_OK
        } catch (e: HttpClient.HttpException) {
            Log.e(TAG, "Could not update state", e)
            ControlAction.RESPONSE_FAIL
        }
    }

    companion object {
        private val TAG = ItemsControlsProviderService::class.java.simpleName
    }

    private class ItemControlFactory constructor(
        private val context: Context,
        private val allItems: Map<String, Item>,
        private val stateful: Boolean
    ) {
        private val serverName: String
        private val primaryServerId: Int
        private val subtitleMode: DeviceControlSubtitleMode
        private val authRequired: Boolean

        init {
            val prefs = context.getPrefs()
            primaryServerId = prefs.getPrimaryServerId()
            serverName = ServerConfiguration.load(prefs, context.getSecretPrefs(), primaryServerId)
                ?.name
                .orDefaultIfEmpty(context.getString(R.string.app_name))
            subtitleMode = prefs.getDeviceControlSubtitle(context)
            authRequired = prefs.getBoolean(PrefKeys.DEVICE_CONTROL_AUTH_REQUIRED, true)
        }

        fun maybeCreateControl(item: Item): Control? {
            val label = item.label
            if (label.isNullOrEmpty()) return null
            val controlTemplate = getControlTemplate(item) ?: return null

            val location = getItemTagLabel(item, Item.Tag.Location).orEmpty()
            val equipment = getItemTagLabel(item, Item.Tag.Equipment).orEmpty()

            val subtitle = when (subtitleMode) {
                DeviceControlSubtitleMode.LOCATION -> location
                DeviceControlSubtitleMode.EQUIPMENT -> equipment
                DeviceControlSubtitleMode.LOCATION_AND_EQUIPMENT -> "$location $equipment"
                DeviceControlSubtitleMode.ITEM_NAME -> item.name
            }

            val zone = when (subtitleMode) {
                DeviceControlSubtitleMode.ITEM_NAME -> item.name.first().toString()
                else -> location
            }

            val (intent, requestCode) = getIntent(item)

            val pi = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Immutable
            )
            val statefulControl = Control.StatefulBuilder(item.name, pi)
                .setTitle(label)
                .setSubtitle(subtitle)
                .setZone(zone)
                .setStructure(serverName)
                .setDeviceType(item.getDeviceType())
                .setControlTemplate(controlTemplate)
                .setStatus(Control.STATUS_OK)

            getStatusText(item, controlTemplate)?.let {
                statefulControl.setStatusText(it)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                statefulControl.setAuthRequired(authRequired)
            }

            return if (stateful) {
                statefulControl.build()
            } else {
                Control.StatelessBuilder(statefulControl.build()).build()
            }
        }

        private fun getStatusText(item: Item, controlTemplate: ControlTemplate): String? {
            val typesWithState = listOf(
                ControlTemplate.TYPE_ERROR,
                ControlTemplate.TYPE_NO_TEMPLATE,
                ControlTemplate.TYPE_STATELESS
            )

            return when {
                controlTemplate.templateType !in typesWithState -> null
                !item.options.isNullOrEmpty() -> {
                    item.options
                        .firstOrNull { labeledValue -> labeledValue.value == item.state?.asString }
                        ?.label
                }
                item.isOfTypeOrGroupType(Item.Type.Number) -> item.state?.asNumber?.toString()
                else -> item.state?.asString
            }
        }

        private fun getIntent(item: Item) = when {
            !item.readOnly && item.options != null -> {
                val intent = Intent(context, SelectionItemActivity::class.java).apply {
                    putExtra(SelectionItemActivity.EXTRA_ITEM, item)
                }
                Pair(intent, item.hashCode())
            }
            !item.readOnly && item.isOfTypeOrGroupType(Item.Type.Color) -> {
                val intent = Intent(context, ColorItemActivity::class.java).apply {
                    putExtra(ColorItemActivity.EXTRA_ITEM, item)
                }
                Pair(intent, item.hashCode())
            }
            else -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_SERVER_ID, primaryServerId)
                }
                Pair(intent, primaryServerId)
            }
        }

        private fun getControlTemplate(item: Item): ControlTemplate? {
            val isTypeWithoutTile = listOf(Item.Type.Image, Item.Type.Location)
                .any { type -> item.isOfTypeOrGroupType(type) }

            return when {
                isTypeWithoutTile -> null
                item.readOnly -> ControlTemplate.getNoTemplateObject()
                item.options != null -> {
                    // Open app when clicking on tile
                    ControlTemplate.getNoTemplateObject()
                }
                item.isOfTypeOrGroupType(Item.Type.Switch) -> ToggleTemplate(
                    item.name,
                    ControlButton(item.state?.asBoolean ?: false, context.getString(R.string.nfc_action_toggle))
                )
                item.isOfTypeOrGroupType(Item.Type.Dimmer) || item.isOfTypeOrGroupType(Item.Type.Color) -> ToggleRangeTemplate(
                    "${item.name}_toggle",
                    ControlButton(item.state?.asBoolean ?: false, context.getString(R.string.nfc_action_toggle)),
                    createRangeTemplate(item, "%.0f%%")
                )
                item.isOfTypeOrGroupType(Item.Type.Rollershutter) -> createRangeTemplate(item, "%.0f%%")
                item.isOfTypeOrGroupType(Item.Type.Number) -> createRangeTemplate(
                    item,
                    item.state?.asNumber?.unit?.let { "%.0f $it" } ?: "%.0f"
                )
                item.isOfTypeOrGroupType(Item.Type.Player) -> ToggleTemplate(
                    item.name,
                    ControlButton(item.state?.asString == "PLAY", context.getString(R.string.nfc_action_toggle))
                )
                else -> ControlTemplate.getNoTemplateObject()
            }
        }

        private fun createRangeTemplate(item: Item, format: String): RangeTemplate {
            val currentValue = item.state?.asNumber?.value ?: 0F
            val minimum = min(currentValue, item.minimum ?: 0F)
            val maximum = max(currentValue, item.maximum ?: 100F)
            return RangeTemplate(
                item.name, minimum, maximum,
                currentValue, item.step ?: 1F,
                format
            )
        }

        private fun getItemTagLabel(item: Item, type: Item.Tag): String? {
            val groups = item.groupNames.mapNotNull { groupName -> allItems[groupName] }
            // First check if any of the groups is the requested type
            groups.forEach { group ->
                if (group.tags.any { tag -> tag == type }) {
                    return group.label
                }
                val tagByType = group.tags.firstOrNull { tag -> tag.parent == type }
                if (tagByType != null) {
                    return if (group.label.isNullOrBlank() && tagByType.labelResId != null) {
                        context.getString(tagByType.labelResId)
                    } else {
                        group.label
                    }
                }
            }

            // If none of the groups is location or equipment, recursively check parent groups
            return groups.firstNotNullOfOrNull { group -> getItemTagLabel(group, type) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
fun Item.getDeviceType() = when (category?.lowercase()?.substringAfterLast(':')) {
    "screen", "soundvolume", "receiver" -> DeviceTypes.TYPE_TV
    "lightbulb", "light", "slider" -> DeviceTypes.TYPE_LIGHT
    "lock" -> DeviceTypes.TYPE_LOCK
    "fan", "fan_box", "fan_ceiling" -> DeviceTypes.TYPE_FAN
    "blinds" -> DeviceTypes.TYPE_BLINDS
    "rollershutter" -> DeviceTypes.TYPE_SHUTTER
    "window" -> DeviceTypes.TYPE_WINDOW
    "dryer" -> DeviceTypes.TYPE_DRYER
    "washingmachine" -> DeviceTypes.TYPE_WASHER
    "camera" -> DeviceTypes.TYPE_CAMERA
    "switch", "wallswitch" -> DeviceTypes.TYPE_SWITCH
    "lawnmower" -> DeviceTypes.TYPE_MOWER
    "humidity" -> DeviceTypes.TYPE_HUMIDIFIER
    "heating", "temperature" -> DeviceTypes.TYPE_HEATER
    "poweroutlet" -> DeviceTypes.TYPE_OUTLET
    "door", "frontdoor" -> DeviceTypes.TYPE_DOOR
    "alarm" -> DeviceTypes.TYPE_SECURITY_SYSTEM
    "water" -> DeviceTypes.TYPE_SHOWER
    "garage", "garagedoor", "garage_detached", "garage_detached_selected" -> DeviceTypes.TYPE_GARAGE
    else -> when {
        // Confident mappings of Item type or tag to device type
        isOfTypeOrGroupType(Item.Type.Rollershutter) -> DeviceTypes.TYPE_BLINDS
        Item.Tag.HeatingCoolingMode in tags -> DeviceTypes.TYPE_THERMOSTAT
        Item.Tag.TargetTemperature in tags -> DeviceTypes.TYPE_THERMOSTAT
        Item.Tag.Alarm in tags -> DeviceTypes.TYPE_SECURITY_SYSTEM
        Item.Tag.AlarmSystem in tags -> DeviceTypes.TYPE_SECURITY_SYSTEM
        Item.Tag.Blinds in tags -> DeviceTypes.TYPE_BLINDS
        Item.Tag.Boiler in tags -> DeviceTypes.TYPE_WATER_HEATER
        Item.Tag.Camera in tags -> DeviceTypes.TYPE_CAMERA
        Item.Tag.Car in tags -> DeviceTypes.TYPE_GARAGE
        Item.Tag.Carport in tags -> DeviceTypes.TYPE_GARAGE
        Item.Tag.CeilingFan in tags -> DeviceTypes.TYPE_FAN
        Item.Tag.CellarDoor in tags -> DeviceTypes.TYPE_DOOR
        Item.Tag.CleaningRobot in tags -> DeviceTypes.TYPE_VACUUM
        Item.Tag.Dishwasher in tags -> DeviceTypes.TYPE_DISHWASHER
        Item.Tag.Door in tags -> DeviceTypes.TYPE_DOOR
        Item.Tag.Doorbell in tags -> DeviceTypes.TYPE_DOORBELL
        Item.Tag.Dryer in tags -> DeviceTypes.TYPE_DRYER
        Item.Tag.Fan in tags -> DeviceTypes.TYPE_FAN
        Item.Tag.Freezer in tags -> DeviceTypes.TYPE_REFRIGERATOR
        Item.Tag.FrontDoor in tags -> DeviceTypes.TYPE_DOOR
        Item.Tag.Garage in tags -> DeviceTypes.TYPE_GARAGE
        Item.Tag.GarageDoor in tags -> DeviceTypes.TYPE_DOOR
        Item.Tag.Gate in tags -> DeviceTypes.TYPE_GATE
        Item.Tag.HVAC in tags -> DeviceTypes.TYPE_AIR_FRESHENER
        Item.Tag.Humidity in tags -> DeviceTypes.TYPE_HUMIDIFIER
        Item.Tag.InnerDoor in tags -> DeviceTypes.TYPE_DOOR
        Item.Tag.KitchenHood in tags -> DeviceTypes.TYPE_HOOD
        Item.Tag.LawnMower in tags -> DeviceTypes.TYPE_MOWER
        Item.Tag.Light in tags -> DeviceTypes.TYPE_LIGHT
        Item.Tag.LightStripe in tags -> DeviceTypes.TYPE_LIGHT
        Item.Tag.Lightbulb in tags -> DeviceTypes.TYPE_LIGHT
        Item.Tag.Lock in tags -> DeviceTypes.TYPE_LOCK
        Item.Tag.NetworkAppliance in tags -> DeviceTypes.TYPE_SET_TOP
        Item.Tag.Oven in tags -> DeviceTypes.TYPE_MULTICOOKER
        Item.Tag.PowerOutlet in tags -> DeviceTypes.TYPE_OUTLET
        Item.Tag.Projector in tags -> DeviceTypes.TYPE_TV
        Item.Tag.RadiatorControl in tags -> DeviceTypes.TYPE_RADIATOR
        Item.Tag.Receiver in tags -> DeviceTypes.TYPE_TV
        Item.Tag.Refrigerator in tags -> DeviceTypes.TYPE_REFRIGERATOR
        Item.Tag.RemoteControl in tags -> DeviceTypes.TYPE_REMOTE_CONTROL
        Item.Tag.Screen in tags -> DeviceTypes.TYPE_TV
        Item.Tag.SideDoor in tags -> DeviceTypes.TYPE_DOOR
        Item.Tag.Siren in tags -> DeviceTypes.TYPE_SECURITY_SYSTEM
        Item.Tag.Switch in tags -> DeviceTypes.TYPE_SWITCH
        Item.Tag.Television in tags -> DeviceTypes.TYPE_TV
        Item.Tag.Temperature in tags -> DeviceTypes.TYPE_THERMOSTAT
        Item.Tag.Valve in tags -> DeviceTypes.TYPE_VALVE
        Item.Tag.Veranda in tags -> DeviceTypes.TYPE_PERGOLA
        Item.Tag.WallSwitch in tags -> DeviceTypes.TYPE_SWITCH
        Item.Tag.WashingMachine in tags -> DeviceTypes.TYPE_WASHER
        Item.Tag.WhiteGood in tags -> DeviceTypes.TYPE_WASHER
        Item.Tag.Window in tags -> DeviceTypes.TYPE_WINDOW

        // Items tagged with 'Control' might have a second more suitable tag, e.g. 'Light'
        Item.Tag.Control in tags -> DeviceTypes.TYPE_REMOTE_CONTROL

        // Fallback mappings of Item type or tag to device type
        Item.Tag.Bathroom in tags -> DeviceTypes.TYPE_SHOWER
        Item.Tag.BoilerRoom in tags -> DeviceTypes.TYPE_WATER_HEATER
        Item.Tag.ContactSensor in tags -> DeviceTypes.TYPE_GENERIC_OPEN_CLOSE
        Item.Tag.Kitchen in tags -> DeviceTypes.TYPE_WASHER
        Item.Tag.LaundryRoom in tags -> DeviceTypes.TYPE_WASHER
        Item.Tag.LivingRoom in tags -> DeviceTypes.TYPE_TV
        isOfTypeOrGroupType(Item.Type.Contact) -> DeviceTypes.TYPE_WINDOW
        isOfTypeOrGroupType(Item.Type.Player) -> DeviceTypes.TYPE_TV
        isOfTypeOrGroupType(Item.Type.Switch) -> DeviceTypes.TYPE_GENERIC_ON_OFF

        else -> DeviceTypes.TYPE_UNKNOWN
    }
}
