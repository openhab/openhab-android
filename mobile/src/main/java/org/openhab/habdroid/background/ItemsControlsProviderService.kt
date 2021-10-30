/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
import org.openhab.habdroid.model.toParsedState
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient

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

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        val controls = SimplePublisher<Control>()
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
                maybeCreateStatefulControl(item)?.let { control -> controls.add(control) }
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
        val job = launchWithConnection { connection ->
            if (connection == null) {
                Log.e(TAG, "Got no connection for loading items")
                publisher.done = true
                return@launchWithConnection
            }

            itemNames.forEach { itemName ->
                try {
                    ItemClient.loadItem(connection, itemName)?.let { item ->
                        maybeCreateStatefulControl(item)?.let { control ->
                            items[item.name] = item
                            publisher.add(control)
                        }
                    }
                } catch (e: HttpClient.HttpException) {
                    Log.e(TAG, "Could not load item $itemName", e)
                }
            }

            eventStream = connection.httpClient.makeSse(
                // Support for both the "openhab" and the older "smarthome" root topic by using a wildcard
                connection.httpClient.buildUrl("rest/events?topics=*/items/*/statechanged"),
                StateChangeListener { itemName, state ->
                    val item = items[itemName] ?: return@StateChangeListener
                    val newItem = item.copy(state = state)
                    maybeCreateStatefulControl(newItem)?.let { control ->
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

    private fun maybeCreateStatefulControl(item: Item): Control? {
        if (item.label.isNullOrEmpty() || item.readOnly) return null

        val controlTemplate = when (item.type) {
            Item.Type.Switch -> ToggleTemplate(
                item.name,
                ControlButton(item.state?.asBoolean ?: false, getString(R.string.nfc_action_toggle))
            )
            Item.Type.Dimmer, Item.Type.Color -> ToggleRangeTemplate(
                "${item.name}_toggle",
                ControlButton(item.state?.asBoolean ?: false, getString(R.string.nfc_action_toggle)),
                RangeTemplate(
                    "${item.name}_range", 0F, 100F,
                    item.state?.asNumber?.value ?: 0F, 1F,
                    "%.0f%%"
                )
            )
            Item.Type.Rollershutter -> RangeTemplate(
                item.name, 0F, 100F,
                item.state?.asNumber?.value ?: 0F, 1F,
                "%.0f%%"
            )
            else -> return null // TODO support other types e.g. thermostat
        }

        val type = when (item.category?.lowercase()) {
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
                item.isOfTypeOrGroupType(Item.Type.Rollershutter) || item.tags.contains(Item.Tag.Blind) ->
                    DeviceTypes.TYPE_BLINDS
                item.tags.contains(Item.Tag.Thermostat) || item.tags.contains(Item.Tag.TargetTemperature) ->
                    DeviceTypes.TYPE_HEATER
                item.tags.contains(Item.Tag.Lighting) -> DeviceTypes.TYPE_LIGHT
                else -> DeviceTypes.TYPE_UNKNOWN
            }
        }

        val intent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Control.StatefulBuilder(item.name, intent)
            .setTitle(item.label)
            .setDeviceType(type)
            .setControlTemplate(controlTemplate)
            .setStatus(Control.STATUS_OK)
            .build()
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
