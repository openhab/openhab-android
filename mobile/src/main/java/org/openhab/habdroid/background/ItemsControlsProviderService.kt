/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.LinkedList
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.toParsedState
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.DeviceControlSubtitleMode
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.getDeviceControlSubtitle
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.orDefaultIfEmpty

@RequiresApi(Build.VERSION_CODES.R)
class ItemsControlsProviderService : ControlsProviderService() {

    class StateChangeListener(private val stateChangeCallback: (String, ParsedState) -> Unit) : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            try {
                val event = JSONObject(data)
                val topicPath = event.getString("topic").split('/')
                if (topicPath.size != 4) {
                    Log.e(TAG, "Failed parsing topic of state change event")
                    return
                }
                val itemName = topicPath[2]
                val payload = JSONObject(event.getString("payload"))
                val state = payload.getString("value").toParsedState() ?: return
                stateChangeCallback(itemName, state)
            } catch (e: JSONException) {
                Log.e(TAG, "Failed parsing JSON of state change event", e)
            }
        }
    }

    private val mainActivityPendingIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_SERVER_ID, getPrefs().getPrimaryServerId())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        val controls = SimplePublisher<Control>()
        val primaryServerName = ServerConfiguration.load(getPrefs(), getSecretPrefs(), getPrefs().getPrimaryServerId())
            ?.name
            .orDefaultIfEmpty(getString(R.string.app_name))
        val subtitleMode = getPrefs().getDeviceControlSubtitle(applicationContext)
        val job = GlobalScope.launch {
            ConnectionFactory.waitForInitialization()
            val connection = ConnectionFactory.primaryUsableConnection?.connection
            if (connection == null) {
                Log.e(TAG, "Got no connection for loading all items")
                controls.done = true
                return@launch
            }
            val items = try {
                ItemClient.loadItems(connection)
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Could not load items", e)
                controls.done = true
                return@launch
            }
            if (items == null) {
                Log.e(TAG, "Could not load items")
                controls.done = true
                return@launch
            }
            items.forEach { item ->
                maybeCreateControl(item, items, primaryServerName, subtitleMode, false)?.let { control ->
                    controls.add(control)
                }
            }
            controls.done = true
        }
        controls.onCancel = {
            job.cancel()
        }
        return controls
    }

    override fun createPublisherFor(itemNames: MutableList<String>): Flow.Publisher<Control> {
        val publisher = SimplePublisher<Control>()
        var eventStream: EventSource? = null
        val items = mutableMapOf<String, Item>()
        val primaryServerName = ServerConfiguration.load(getPrefs(), getSecretPrefs(), getPrefs().getPrimaryServerId())
            ?.name
            .orDefaultIfEmpty(getString(R.string.app_name))
        val subtitleMode = getPrefs().getDeviceControlSubtitle(applicationContext)
        val job = launchWithConnection { connection ->
            if (connection == null) {
                Log.e(TAG, "Got no connection for loading items")
                publisher.done = true
                return@launchWithConnection
            }

            val allItems = try {
                ItemClient.loadItems(connection) ?: emptyList()
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Could not load items", e)
                emptyList()
            }

            itemNames
                .map { itemName ->
                    allItems.first { item -> item.name == itemName }
                }
                .forEach { item ->
                    maybeCreateControl(item, allItems, primaryServerName, subtitleMode, true)?.let { control ->
                        items[item.name] = item
                        publisher.add(control)
                    }
            }

            eventStream = connection.httpClient.makeSse(
                // Support for both the "openhab" and the older "smarthome" root topic by using a wildcard
                connection.httpClient.buildUrl("rest/events?topics=*/items/*/statechanged"),
                StateChangeListener { itemName, state ->
                    val item = items[itemName] ?: return@StateChangeListener
                    val newItem = item.copy(state = state)
                    maybeCreateControl(newItem, allItems, primaryServerName, subtitleMode, true)?.let { control ->
                        publisher.add(control)
                    }
                }
            )
        }
        publisher.onCancel = {
            job.cancel()
            eventStream?.cancel()
            eventStream = null
        }
        return publisher
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        launchWithConnection { connection ->
            if (connection == null) {
                consumer.accept(ControlAction.RESPONSE_FAIL)
                return@launchWithConnection
            }

            val state = when (action) {
                is BooleanAction -> if (action.newState) "ON" else "OFF"
                is FloatAction -> action.newValue.roundToInt().toString()
                else -> {
                    Log.e(TAG, "Unsupported action $action")
                    return@launchWithConnection
                }
            }

            try {
                connection.httpClient
                    .post("rest/items/$controlId", state)
                    .asStatus()
                consumer.accept(ControlAction.RESPONSE_OK)
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Could not update state", e)
                consumer.accept(ControlAction.RESPONSE_FAIL)
            }
        }
    }

    private fun getItemTagLabel(item: Item, allItems: List<Item>, type: Item.Tag): String? {
        val groups = item.groupNames.map { groupName ->
            allItems.first { item -> item.name == groupName }
        }
        // First check if any of the groups is the requested type
        groups.forEach { group ->
            if (group.tags.any { tag -> tag == type }) {
                return group.label
            }
            val tagByType = group.tags.firstOrNull { tag -> tag.parent == type }
            if (tagByType != null) {
                return if (group.label.isNullOrBlank() && tagByType.labelResId != null) {
                    getString(tagByType.labelResId)
                } else {
                    group.label
                }
            }
        }

        // If none of the groups is location or equipment, recursively check parent groups
        return groups.firstNotNullOfOrNull { group -> getItemTagLabel(group, allItems, type) }
    }

    private fun getDeviceType(item: Item) =
        when (item.category?.lowercase()) {
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
                item.isOfTypeOrGroupType(Item.Type.Rollershutter) -> DeviceTypes.TYPE_BLINDS
                Item.Tag.HeatingCoolingMode in item.tags -> DeviceTypes.TYPE_THERMOSTAT
                Item.Tag.TargetTemperature in item.tags -> DeviceTypes.TYPE_THERMOSTAT
                Item.Tag.Alarm in item.tags -> DeviceTypes.TYPE_SECURITY_SYSTEM
                Item.Tag.AlarmSystem in item.tags -> DeviceTypes.TYPE_SECURITY_SYSTEM
                Item.Tag.Blinds in item.tags -> DeviceTypes.TYPE_BLINDS
                Item.Tag.Boiler in item.tags -> DeviceTypes.TYPE_WATER_HEATER
                Item.Tag.Camera in item.tags -> DeviceTypes.TYPE_CAMERA
                Item.Tag.Car in item.tags -> DeviceTypes.TYPE_GARAGE
                Item.Tag.Carport in item.tags -> DeviceTypes.TYPE_GARAGE
                Item.Tag.CeilingFan in item.tags -> DeviceTypes.TYPE_FAN
                Item.Tag.CellarDoor in item.tags -> DeviceTypes.TYPE_DOOR
                Item.Tag.CleaningRobot in item.tags -> DeviceTypes.TYPE_VACUUM
                Item.Tag.Control in item.tags -> DeviceTypes.TYPE_REMOTE_CONTROL
                Item.Tag.Dishwasher in item.tags -> DeviceTypes.TYPE_DISHWASHER
                Item.Tag.Door in item.tags -> DeviceTypes.TYPE_DOOR
                Item.Tag.Doorbell in item.tags -> DeviceTypes.TYPE_DOORBELL
                Item.Tag.Dryer in item.tags -> DeviceTypes.TYPE_DRYER
                Item.Tag.Fan in item.tags -> DeviceTypes.TYPE_FAN
                Item.Tag.Freezer in item.tags -> DeviceTypes.TYPE_REFRIGERATOR
                Item.Tag.FrontDoor in item.tags -> DeviceTypes.TYPE_DOOR
                Item.Tag.Garage in item.tags -> DeviceTypes.TYPE_GARAGE
                Item.Tag.GarageDoor in item.tags -> DeviceTypes.TYPE_DOOR
                Item.Tag.Gate in item.tags -> DeviceTypes.TYPE_GATE
                Item.Tag.HVAC in item.tags -> DeviceTypes.TYPE_AIR_FRESHENER
                Item.Tag.Humidity in item.tags -> DeviceTypes.TYPE_HUMIDIFIER
                Item.Tag.InnerDoor in item.tags -> DeviceTypes.TYPE_DOOR
                Item.Tag.KitchenHood in item.tags -> DeviceTypes.TYPE_HOOD
                Item.Tag.LawnMower in item.tags -> DeviceTypes.TYPE_MOWER
                Item.Tag.Light in item.tags -> DeviceTypes.TYPE_LIGHT
                Item.Tag.LightStripe in item.tags -> DeviceTypes.TYPE_LIGHT
                Item.Tag.Lightbulb in item.tags -> DeviceTypes.TYPE_LIGHT
                Item.Tag.Lock in item.tags -> DeviceTypes.TYPE_LOCK
                Item.Tag.NetworkAppliance in item.tags -> DeviceTypes.TYPE_SET_TOP
                Item.Tag.Oven in item.tags -> DeviceTypes.TYPE_MULTICOOKER
                Item.Tag.PowerOutlet in item.tags -> DeviceTypes.TYPE_OUTLET
                Item.Tag.Projector in item.tags -> DeviceTypes.TYPE_TV
                Item.Tag.Receiver in item.tags -> DeviceTypes.TYPE_TV
                Item.Tag.Refrigerator in item.tags -> DeviceTypes.TYPE_REFRIGERATOR
                Item.Tag.RemoteControl in item.tags -> DeviceTypes.TYPE_REMOTE_CONTROL
                Item.Tag.Screen in item.tags -> DeviceTypes.TYPE_TV
                Item.Tag.SideDoor in item.tags -> DeviceTypes.TYPE_DOOR
                Item.Tag.Siren in item.tags -> DeviceTypes.TYPE_SECURITY_SYSTEM
                Item.Tag.Switch in item.tags -> DeviceTypes.TYPE_SWITCH
                Item.Tag.Television in item.tags -> DeviceTypes.TYPE_TV
                Item.Tag.Temperature in item.tags -> DeviceTypes.TYPE_THERMOSTAT
                Item.Tag.Valve in item.tags -> DeviceTypes.TYPE_VALVE
                Item.Tag.Veranda in item.tags -> DeviceTypes.TYPE_PERGOLA
                Item.Tag.WallSwitch in item.tags -> DeviceTypes.TYPE_SWITCH
                Item.Tag.WashingMachine in item.tags -> DeviceTypes.TYPE_WASHER
                Item.Tag.WhiteGood in item.tags -> DeviceTypes.TYPE_WASHER
                Item.Tag.Window in item.tags -> DeviceTypes.TYPE_WINDOW

                // Fallback mappings of Item type or tag to device type
                Item.Tag.Bathroom in item.tags -> DeviceTypes.TYPE_SHOWER
                Item.Tag.BoilerRoom in item.tags -> DeviceTypes.TYPE_WATER_HEATER
                Item.Tag.ContactSensor in item.tags -> DeviceTypes.TYPE_GENERIC_OPEN_CLOSE
                Item.Tag.Kitchen in item.tags -> DeviceTypes.TYPE_WASHER
                Item.Tag.LaundryRoom in item.tags -> DeviceTypes.TYPE_WASHER
                Item.Tag.LivingRoom in item.tags -> DeviceTypes.TYPE_TV
                item.isOfTypeOrGroupType(Item.Type.Contact) -> DeviceTypes.TYPE_WINDOW
                item.isOfTypeOrGroupType(Item.Type.Player) -> DeviceTypes.TYPE_TV
                item.isOfTypeOrGroupType(Item.Type.Switch) -> DeviceTypes.TYPE_GENERIC_ON_OFF

                else -> DeviceTypes.TYPE_UNKNOWN
            }
        }

    private fun createRangeTemplate(item: Item, format: String): RangeTemplate {
        val currentValue = item.state?.asNumber?.value ?: 0F
        val minimum = min(currentValue, item.minimum?.toFloat() ?: 0F)
        val maximum = max(currentValue, item.maximum?.toFloat() ?: 100F)
        return RangeTemplate(
            item.name, minimum, maximum,
            currentValue, item.step?.toFloat() ?: 1F,
            format
        )
    }

    private fun maybeCreateControl(
        item: Item,
        allItems: List<Item>,
        serverName: String,
        subtitleMode: DeviceControlSubtitleMode,
        stateful: Boolean
    ): Control? {
        if (item.label.isNullOrEmpty() || item.readOnly) return null
        val controlTemplate = when (item.type) {
            Item.Type.Switch -> ToggleTemplate(
                item.name,
                ControlButton(item.state?.asBoolean ?: false, getString(R.string.nfc_action_toggle))
            )
            Item.Type.Dimmer, Item.Type.Color -> ToggleRangeTemplate(
                "${item.name}_toggle",
                ControlButton(item.state?.asBoolean ?: false, getString(R.string.nfc_action_toggle)),
                createRangeTemplate(item, "%.0f%%")
            )
            Item.Type.Rollershutter -> createRangeTemplate(item, "%.0f%%")
            Item.Type.Number -> createRangeTemplate(
                item,
                item.state?.asNumber?.unit?.let { "%.0f $it" } ?: "%.0f"
            )
            else -> return null
        }

        val location = getItemTagLabel(item, allItems, Item.Tag.Location).orEmpty()
        val equipment = getItemTagLabel(item, allItems, Item.Tag.Equipment).orEmpty()

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

        val statefulControl = Control.StatefulBuilder(item.name, mainActivityPendingIntent)
            .setTitle(item.label)
            .setSubtitle(subtitle)
            .setZone(zone)
            .setStructure(serverName)
            .setDeviceType(getDeviceType(item))
            .setControlTemplate(controlTemplate)
            .setStatus(Control.STATUS_OK)
            .build()

        return if (stateful) {
            statefulControl
        } else {
            Control.StatelessBuilder(statefulControl).build()
        }
    }

    private inline fun launchWithConnection(
        crossinline block: suspend CoroutineScope.(Connection?) -> Unit
    ): Job = GlobalScope.launch {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.primaryUsableConnection?.connection
        block(connection)
    }

    companion object {
        private val TAG = ItemsControlsProviderService::class.java.simpleName
    }
}

@RequiresApi(Build.VERSION_CODES.R)
class SimplePublisher<T>(var onCancel: (() -> Unit)? = null) : Flow.Publisher<T> {
    inner class SimpleSubscription : Flow.Subscription {
        override fun request(additionalRequested: Long) {
            requested += additionalRequested
            publish()
        }

        override fun cancel() {
            subscriber = null
            onCancel?.invoke()
            onCancel = null
        }
    }

    private var requested = 0L
    private var subscription: SimpleSubscription? = null
    private var subscriber: Flow.Subscriber<in T>? = null
    private var list = LinkedList<T>()
    var done = false
        set(value) {
            subscriber?.onComplete()
            field = value
        }

    override fun subscribe(subscriber: Flow.Subscriber<in T>) {
        if (subscription != null) return
        this.subscriber = subscriber
        subscription = SimpleSubscription()
        subscriber.onSubscribe(subscription)
    }

    fun add(element: T) {
        list.addFirst(element)
        publish()
    }

    private fun publish() {
        while (subscriber != null && !list.isEmpty() && requested > 0) {
            subscriber?.onNext(list.pollLast())
            requested -= 1
        }

        if (list.isEmpty() && done) {
            subscriber?.onComplete()
        }
    }
}
