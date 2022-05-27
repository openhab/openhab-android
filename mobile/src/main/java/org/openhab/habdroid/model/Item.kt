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
    val groupNames: List<String>,
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

    @Suppress("unused")
    enum class Tag(val parent : Tag?) {
        // Legacy tags from openHAB 2
        ContactSensor(null),
        HeatingCoolingMode(null),
        TargetTemperature(null),

        // Fallback
        Unknown(null),

        // Semantic types
        Equipment(null),
        Location(null),
        Point(null),
        Property(null),
        Measurement(Point),

        // Semantic tags
        Alarm(Point),
        AlarmSystem(Equipment),
        Apartment(Location),
        Attic(Location),
        BackDoor(Equipment),
        Basement(Location),
        Bathroom(Location),
        Battery(Equipment),
        Bedroom(Location),
        Blinds(Equipment),
        Boiler(Equipment),
        BoilerRoom(Location),
        Building(Location),
        CO(Measurement),
        CO2(Measurement),
        Camera(Equipment),
        Car(Equipment),
        Carport(Location),
        CeilingFan(Equipment),
        Cellar(Location),
        CellarDoor(Equipment),
        CleaningRobot(Equipment),
        ColorTemperature(Measurement),
        Control(Point),
        Corridor(Location),
        Current(Measurement),
        DiningRoom(Location),
        Dishwasher(Equipment),
        Door(Equipment),
        Doorbell(Equipment),
        Driveway(Location),
        Dryer(Equipment),
        Duration(Measurement),
        Energy(Measurement),
        Entry(Location),
        FamilyRoom(Location),
        Fan(Equipment),
        FirstFloor(Location),
        Floor(Location),
        Freezer(Equipment),
        Frequency(Measurement),
        FrontDoor(Equipment),
        Garage(Location),
        GarageDoor(Equipment),
        Garden(Location),
        Gas(Measurement),
        Gate(Equipment),
        GroundFloor(Location),
        GuestRoom(Location),
        HVAC(Equipment),
        House(Location),
        Humidity(Measurement),
        Indoor(Location),
        InnerDoor(Equipment),
        Inverter(Equipment),
        Kitchen(Location),
        KitchenHood(Equipment),
        LaundryRoom(Location),
        LawnMower(Equipment),
        Level(Measurement),
        Light(Measurement),
        Lightbulb(Equipment),
        LightStripe(Equipment),
        LivingRoom(Location),
        Lock(Equipment),
        LowBattery(Point),
        MotionDetector(Equipment),
        NetworkAppliance(Equipment),
        Noise(Measurement),
        Office(Location),
        Oil(Measurement),
        OpenLevel(Point),
        OpenState(Point),
        Opening(Measurement),
        Outdoor(Location),
        Oven(Equipment),
        Patio(Location),
        Porch(Location),
        Power(Measurement),
        PowerOutlet(Equipment),
        Presence(Measurement),
        Pressure(Measurement),
        Projector(Equipment),
        Pump(Equipment),
        RadiatorControl(Equipment),
        Rain(Measurement),
        Receiver(Equipment),
        Refrigerator(Equipment),
        RemoteControl(Equipment),
        Room(Location),
        Screen(Equipment),
        SecondFloor(Location),
        Sensor(Equipment),
        Setpoint(Point),
        Shed(Location),
        SideDoor(Equipment),
        Siren(Equipment),
        Smartphone(Equipment),
        Smoke(Measurement),
        SmokeDetector(Equipment),
        SoundVolume(Measurement),
        Speaker(Equipment),
        Status(Point),
        SummerHouse(Location),
        Switch(Point),
        Tampered(Point),
        Television(Equipment),
        Temperature(Measurement),
        Terrace(Location),
        ThirdFloor(Location),
        Tilt(Point),
        Timestamp(Measurement),
        Ultraviolet(Measurement),
        Valve(Equipment),
        Veranda(Location),
        Vibration(Measurement),
        VoiceAssistant(Equipment),
        Voltage(Measurement),
        WallSwitch(Equipment),
        WashingMachine(Equipment),
        Water(Measurement),
        WeatherService(Equipment),
        WebService(Equipment),
        WhiteGood(Equipment),
        Wind(Measurement),
        Window(Equipment)
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
        name = finalName,
        label = finalName.trim(),
        category = null,
        type = type,
        groupType = groupType,
        link = link,
        readOnly = false,
        members = emptyList(),
        options = null,
        state = state.toParsedState(),
        tags = emptyList(),
        groupNames = emptyList(),
        minimum = null,
        maximum = null,
        step = null
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

    val groupNames = if (has("groupNames")) {
        getJSONArray("groupNames").mapString { it }
    } else {
        emptyList()
    }


    return Item(
        name,
        optStringOrNull("label")?.trim(),
        optStringOrNull("category")?.lowercase(Locale.US),
        getString("type").toItemType(),
        optString("groupType").toItemType(),
        optStringOrNull("link"),
        readOnly,
        members,
        options,
        state.toParsedState(numberPattern),
        tags,
        groupNames,
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
