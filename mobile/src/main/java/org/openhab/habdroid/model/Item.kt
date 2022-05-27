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

    enum class TagType {
        Undefined,
        Class,
        Equipment,
        Location,
        Point,
        Measurement
    }

    @Suppress("unused")
    enum class Tag(val type: TagType) {
        // Legacy tags from openHAB 2
        ContactSensor(TagType.Undefined),
        HeatingCoolingMode(TagType.Undefined),
        TargetTemperature(TagType.Undefined),

        // Semantic types
        Equipment(TagType.Class),
        Location(TagType.Class),
        Point(TagType.Class),
        Property(TagType.Class),

        // Semantic tags
        Alarm(TagType.Point),
        AlarmSystem(TagType.Equipment),
        Apartment(TagType.Location),
        Attic(TagType.Location),
        BackDoor(TagType.Equipment),
        Basement(TagType.Location),
        Bathroom(TagType.Location),
        Battery(TagType.Equipment),
        Bedroom(TagType.Location),
        Blinds(TagType.Equipment),
        Boiler(TagType.Equipment),
        BoilerRoom(TagType.Location),
        Building(TagType.Location),
        CO(TagType.Measurement),
        CO2(TagType.Measurement),
        Camera(TagType.Equipment),
        Car(TagType.Equipment),
        Carport(TagType.Location),
        CeilingFan(TagType.Equipment),
        Cellar(TagType.Location),
        CellarDoor(TagType.Equipment),
        CleaningRobot(TagType.Equipment),
        ColorTemperature(TagType.Measurement),
        Control(TagType.Point),
        Corridor(TagType.Location),
        Current(TagType.Measurement),
        DiningRoom(TagType.Location),
        Dishwasher(TagType.Equipment),
        Door(TagType.Equipment),
        Doorbell(TagType.Equipment),
        Driveway(TagType.Location),
        Dryer(TagType.Equipment),
        Duration(TagType.Measurement),
        Energy(TagType.Measurement),
        Entry(TagType.Location),
        FamilyRoom(TagType.Location),
        Fan(TagType.Equipment),
        FirstFloor(TagType.Location),
        Floor(TagType.Location),
        Freezer(TagType.Equipment),
        Frequency(TagType.Measurement),
        FrontDoor(TagType.Equipment),
        Garage(TagType.Location),
        GarageDoor(TagType.Equipment),
        Garden(TagType.Location),
        Gas(TagType.Measurement),
        Gate(TagType.Equipment),
        GroundFloor(TagType.Location),
        GuestRoom(TagType.Location),
        HVAC(TagType.Equipment),
        House(TagType.Location),
        Humidity(TagType.Measurement),
        Indoor(TagType.Location),
        InnerDoor(TagType.Equipment),
        Inverter(TagType.Equipment),
        Kitchen(TagType.Location),
        KitchenHood(TagType.Equipment),
        LaundryRoom(TagType.Location),
        LawnMower(TagType.Equipment),
        Level(TagType.Measurement),
        Light(TagType.Measurement),
        Lightbulb(TagType.Equipment),
        LightStripe(TagType.Equipment),
        LivingRoom(TagType.Location),
        Lock(TagType.Equipment),
        LowBattery(TagType.Point),
        Measurement(TagType.Point),
        MotionDetector(TagType.Equipment),
        NetworkAppliance(TagType.Equipment),
        Noise(TagType.Measurement),
        Office(TagType.Location),
        Oil(TagType.Measurement),
        OpenLevel(TagType.Point),
        OpenState(TagType.Point),
        Opening(TagType.Measurement),
        Outdoor(TagType.Location),
        Oven(TagType.Equipment),
        Patio(TagType.Location),
        Porch(TagType.Location),
        Power(TagType.Measurement),
        PowerOutlet(TagType.Equipment),
        Presence(TagType.Measurement),
        Pressure(TagType.Measurement),
        Projector(TagType.Equipment),
        Pump(TagType.Equipment),
        RadiatorControl(TagType.Equipment),
        Rain(TagType.Measurement),
        Receiver(TagType.Equipment),
        Refrigerator(TagType.Equipment),
        RemoteControl(TagType.Equipment),
        Room(TagType.Location),
        Screen(TagType.Equipment),
        SecondFloor(TagType.Location),
        Sensor(TagType.Equipment),
        Setpoint(TagType.Point),
        Shed(TagType.Location),
        SideDoor(TagType.Equipment),
        Siren(TagType.Equipment),
        Smartphone(TagType.Equipment),
        Smoke(TagType.Measurement),
        SmokeDetector(TagType.Equipment),
        SoundVolume(TagType.Measurement),
        Speaker(TagType.Equipment),
        Status(TagType.Point),
        SummerHouse(TagType.Location),
        Switch(TagType.Point),
        Tampered(TagType.Point),
        Television(TagType.Equipment),
        Temperature(TagType.Measurement),
        Terrace(TagType.Location),
        ThirdFloor(TagType.Location),
        Tilt(TagType.Point),
        Timestamp(TagType.Measurement),
        Ultraviolet(TagType.Measurement),
        Valve(TagType.Equipment),
        Veranda(TagType.Location),
        Vibration(TagType.Measurement),
        VoiceAssistant(TagType.Equipment),
        Voltage(TagType.Measurement),
        WallSwitch(TagType.Equipment),
        WashingMachine(TagType.Equipment),
        Water(TagType.Measurement),
        WeatherService(TagType.Equipment),
        WebService(TagType.Equipment),
        WhiteGood(TagType.Equipment),
        Wind(TagType.Measurement),
        Window(TagType.Equipment),

        // Fallback
        Unknown(TagType.Undefined)
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
