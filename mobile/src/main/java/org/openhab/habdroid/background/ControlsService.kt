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
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ToggleTemplate
import android.util.Log
import kotlinx.coroutines.launch
import androidx.annotation.RequiresApi
import java.util.LinkedList
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.GlobalScope
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONObject
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.toParsedState


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
        publish()
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

@RequiresApi(Build.VERSION_CODES.R)
class ControlsService : ControlsProviderService() {

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        Log.d("OpenHabControlsProvi", "createPublisherForAllAvailable")

        val controls = SimplePublisher<Control>()

        val job = GlobalScope.launch {
            ConnectionFactory.waitForInitialization()
            val connection = ConnectionFactory.primaryUsableConnection?.connection
            if (connection == null) {
                controls.done = true
                return@launch
            }

            val items = ItemClient.loadItems(connection)
            if (items == null) {
                controls.done = true
                return@launch
            }
            items.forEach {
                createStatefulControl(it)?.let { controls.add(it) }
            }
            controls.done = true
        }

        controls.onCancel = {
            job.cancel()
        }

        return controls
    }

    override fun createPublisherFor(ids: MutableList<String>): Flow.Publisher<Control> {
        val publisher = SimplePublisher<Control>()
        var source: EventSource? = null
        val items = mutableMapOf<String, Item>()
        val job = GlobalScope.launch {
            ConnectionFactory.waitForInitialization()
            val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return@launch
            ids.forEach {
                ItemClient.loadItem(connection, it)?.let { item ->
                    createStatefulControl(item)?.let { control ->
                        items[item.name] = item
                        publisher.add(
                            control
                        )
                    }
                }
            }

            source = connection.httpClient.makeSse(
                connection.httpClient.buildUrl("rest/events?topics=smarthome/items/*/statechanged"),
                object : EventSourceListener() {
                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        val event = JSONObject(data)
                        val itemId = event.getString("topic").split('/')[2]
                        val item = items[itemId] ?: return
                        val payload = JSONObject(event.getString("payload"))
                        val state = payload.getString("value")
                        val newItem = item.copy(state = state.toParsedState())
                        createStatefulControl(newItem)?.let { control ->
                            publisher.add(
                                control
                            )
                        }
                    }
                }
            )
        }
        publisher.onCancel = {
            job.cancel()
            source?.cancel()
        }

        return publisher
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        GlobalScope.launch {
            ConnectionFactory.waitForInitialization()
            val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return@launch

            if (action is BooleanAction) {
                connection.httpClient
                    .post("rest/items/$controlId", if (action.newState) "ON" else "OFF")
                    .asStatus()
                consumer.accept(ControlAction.RESPONSE_OK)
            }
        }
    }

    private fun createStatefulControl(item: Item): Control? {
        if (item.label.isNullOrEmpty() || item.type != Item.Type.Switch || item.state == null) return null

        return Control.StatefulBuilder(
            item.name,
            PendingIntent.getActivity(
                this, 1, Intent(), PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
            .setTitle(item.label)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setControlTemplate(ToggleTemplate("tmp1", ControlButton(item.state.asBoolean, "turn on")))
            .setStatus(Control.STATUS_OK)
            .build()
    }
}
