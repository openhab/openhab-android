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

package org.openhab.habdroid.model

import android.os.Parcelable
import java.util.Locale
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.forEach
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.mapString
import org.openhab.habdroid.util.optDoubleOrNull
import org.openhab.habdroid.util.optStringOrNull
import org.w3c.dom.Node

@Parcelize
data class Item internal constructor(
    val name: String,
    val label: String?,
    val category: String?,
    val type: Type,
    val groupType: Type?,
    val link: String?,
    val readOnly: Boolean,
    val members: List<Item>,
    val options: List<LabeledValue>?,
    val state: ParsedState?,
    val tags: List<Tag>,
    val minimum: Double?,
    val maximum: Double?,
    val step: Double?,
) : Parcelable {
    enum class Type {
        None,
        Color,
        Contact,
        DateTime,
        Dimmer,
        Group,
        Image,
        Location,
        Number,
        NumberWithDimension,
        Player,
        Rollershutter,
        StringItem,
        Switch
    }

    enum class Tag {
        // Legacy tags from openHAB 2
        ContactSensor,
        HeatingCoolingMode,
        TargetTemperature,

        // Semantic types
        Equipment,
        Location,
        Point,
        Property,

        // Semantic tags
        Alarm,
        AlarmSystem,
        Apartment,
        Attic,
        BackDoor,
        Basement,
        Bathroom,
        Battery,
        Bedroom,
        Blinds,
        Boiler,
        BoilerRoom,
        Building,
        CO,
        CO2,
        Camera,
        Car,
        Carport,
        CeilingFan,
        Cellar,
        CellarDoor,
        CleaningRobot,
        ColorTemperature,
        Control,
        Corridor,
        Current,
        DiningRoom,
        Dishwasher,
        Door,
        Doorbell,
        Driveway,
        Dryer,
        Duration,
        Energy,
        Entry,
        FamilyRoom,
        Fan,
        FirstFloor,
        Floor,
        Freezer,
        Frequency,
        FrontDoor,
        Garage,
        GarageDoor,
        Garden,
        Gas,
        Gate,
        GroundFloor,
        GuestRoom,
        HVAC,
        House,
        Humidity,
        Indoor,
        InnerDoor,
        Inverter,
        Kitchen,
        KitchenHood,
        LaundryRoom,
        LawnMower,
        Level,
        Light,
        LightStripe,
        Lightbulb,
        LivingRoom,
        Lock,
        LowBattery,
        Measurement,
        MotionDetector,
        NetworkAppliance,
        Noise,
        Office,
        Oil,
        OpenLevel,
        OpenState,
        Opening,
        Outdoor,
        Oven,
        Patio,
        Porch,
        Power,
        PowerOutlet,
        Presence,
        Pressure,
        Projector,
        Pump,
        RadiatorControl,
        Rain,
        Receiver,
        Refrigerator,
        RemoteControl,
        Room,
        Screen,
        SecondFloor,
        Sensor,
        Setpoint,
        Shed,
        SideDoor,
        Siren,
        Smartphone,
        Smoke,
        SmokeDetector,
        SoundVolume,
        Speaker,
        Status,
        SummerHouse,
        Switch,
        Tampered,
        Television,
        Temperature,
        Terrace,
        ThirdFloor,
        Tilt,
        Timestamp,
        Ultraviolet,
        Valve,
        Veranda,
        Vibration,
        VoiceAssistant,
        Voltage,
        WallSwitch,
        WashingMachine,
        Water,
        WeatherService,
        WebService,
        WhiteGood,
        Wind,
        Window,

        // Fallback
        Unknown
    }

    fun isOfTypeOrGroupType(type: Type): Boolean {
        return this.type == type || groupType == type
    }

    fun canBeToggled(): Boolean {
        return isOfTypeOrGroupType(Type.Color) ||
            isOfTypeOrGroupType(Type.Contact) ||
            isOfTypeOrGroupType(Type.Dimmer) ||
            isOfTypeOrGroupType(Type.Rollershutter) ||
            isOfTypeOrGroupType(Type.Switch) ||
            isOfTypeOrGroupType(Type.Player)
    }

    companion object {
        @Throws(JSONException::class)
        fun updateFromEvent(item: Item?, jsonObject: JSONObject?): Item? {
            if (jsonObject == null) {
                return item
            }
            val parsedItem = jsonObject.toItem()
            // Events don't contain the link property, so preserve that if previously present
            val link = item?.link ?: parsedItem.link
            return parsedItem.copy(link = link, label = parsedItem.label?.trim())
        }
    }
}

fun Node.toItem(): Item? {
    var name: String? = null
    var state: String? = null
    var link: String? = null
    var type = Item.Type.None
    var groupType = Item.Type.None
    childNodes.forEach { node ->
        when (node.nodeName) {
            "type" -> type = node.textContent.toItemType()
            "groupType" -> groupType = node.textContent.toItemType()
            "name" -> name = node.textContent
            "state" -> state = node.textContent
            "link" -> link = node.textContent
        }
    }

    val finalName = name ?: return null
    if (state == "Uninitialized" || state == "Undefined") {
        state = null
    }

    return Item(
        finalName,
        finalName.trim(),
        null,
        type,
        groupType,
        link,
        false,
        emptyList(),
        null,
        state.toParsedState(),
        emptyList(),
        null,
        null,
        null
    )
}

@Throws(JSONException::class)
fun JSONObject.toItem(): Item {
    val name = getString("name")
    var state: String? = optString("state", "")
    if (state == "NULL" || state == "UNDEF" || state.equals("undefined", ignoreCase = true)) {
        state = null
    }

    val stateDescription = optJSONObject("stateDescription")
    val readOnly = stateDescription != null && stateDescription.optBoolean("readOnly", false)

    val options = if (stateDescription?.has("options") == true) {
        stateDescription.getJSONArray("options").map { obj -> obj.toLabeledValue("value", "label") }
    } else {
        null
    }

    val members = if (has("members")) {
        getJSONArray("members").map { obj -> obj.toItem() }
    } else {
        emptyList()
    }

    val numberPattern = stateDescription?.optString("pattern")?.let { pattern ->
        // Remove transformation instructions (e.g. for 'MAP(foo.map):%s' keep only '%s')
        val matchResult = """^[A-Z]+(\(.*\))?:(.*)$""".toRegex().find(pattern)
        if (matchResult != null) {
            matchResult.groupValues[2]
        } else {
            pattern
        }
    }

    val tags = if (has("tags")) {
        getJSONArray("tags").mapString { it.toItemTag() }
    } else {
        emptyList()
    }

    return Item(
        name,
        optString("label", name).trim(),
        optStringOrNull("category")?.lowercase(Locale.getDefault()),
        getString("type").toItemType(),
        optString("groupType").toItemType(),
        optStringOrNull("link"),
        readOnly,
        members,
        options,
        state.toParsedState(numberPattern),
        tags,
        stateDescription?.optDoubleOrNull("minimum"),
        stateDescription?.optDoubleOrNull("maximum"),
        stateDescription?.optDoubleOrNull("step")
    )
}

fun String?.toItemType(): Item.Type {
    var type = this ?: return Item.Type.None

    // Earlier OH2 versions returned e.g. 'Switch' as 'SwitchItem'
    if (type.endsWith("Item")) {
        type = type.substring(0, type.length - 4)
    }
    // types can have subtypes (e.g. 'Number:Temperature'); split off those
    val colonPos = type.indexOf(':')
    if (colonPos > 0) {
        type = type.substring(0, colonPos)
    }

    if (type == "String") {
        return Item.Type.StringItem
    }
    if (type == "Number" && colonPos > 0) {
        return Item.Type.NumberWithDimension
    }
    return try {
        Item.Type.valueOf(type)
    } catch (e: IllegalArgumentException) {
        Item.Type.None
    }
}

fun String?.toItemTag(): Item.Tag {
    this ?: return Item.Tag.Unknown

    try {
        return Item.Tag.valueOf(this)
    } catch (e: IllegalArgumentException) {
        // No 1:1 mapping possible, fall through
    }

    return when (this) {
        "Lighting" -> Item.Tag.Light
        "Switchable" -> Item.Tag.Switch
        "ContactSensor" -> Item.Tag.ContactSensor
        "CurrentTemperature" -> Item.Tag.Temperature
        "CurrentHumidity" -> Item.Tag.Humidity
        "Thermostat" -> Item.Tag.Temperature
        "homekit:HeatingCoolingMode", "homekit:TargetHeatingCoolingMode" -> Item.Tag.HeatingCoolingMode
        "homekit:TargetTemperature", "TargetTemperature" -> Item.Tag.TargetTemperature
        "WindowCovering" -> Item.Tag.Blinds
        else -> Item.Tag.Unknown
    }
}
