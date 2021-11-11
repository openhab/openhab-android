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
        optStringOrNull("category"),
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

        "Equipment" -> Item.Tag.Equipment
        "Location" -> Item.Tag.Location
        "Point" -> Item.Tag.Point
        "Property" -> Item.Tag.Property

        "Alarm" -> Item.Tag.Alarm
        "AlarmSystem" -> Item.Tag.AlarmSystem
        "Apartment" -> Item.Tag.Apartment
        "Attic" -> Item.Tag.Attic
        "BackDoor" -> Item.Tag.BackDoor
        "Basement" -> Item.Tag.Basement
        "Bathroom" -> Item.Tag.Bathroom
        "Battery" -> Item.Tag.Battery
        "Bedroom" -> Item.Tag.Bedroom
        "Blinds" -> Item.Tag.Blinds
        "Boiler" -> Item.Tag.Boiler
        "BoilerRoom" -> Item.Tag.BoilerRoom
        "Building" -> Item.Tag.Building
        "CO" -> Item.Tag.CO
        "CO2" -> Item.Tag.CO2
        "Camera" -> Item.Tag.Camera
        "Car" -> Item.Tag.Car
        "Carport" -> Item.Tag.Carport
        "CeilingFan" -> Item.Tag.CeilingFan
        "Cellar" -> Item.Tag.Cellar
        "CellarDoor" -> Item.Tag.CellarDoor
        "CleaningRobot" -> Item.Tag.CleaningRobot
        "ColorTemperature" -> Item.Tag.ColorTemperature
        "Control" -> Item.Tag.Control
        "Corridor" -> Item.Tag.Corridor
        "Current" -> Item.Tag.Current
        "DiningRoom" -> Item.Tag.DiningRoom
        "Dishwasher" -> Item.Tag.Dishwasher
        "Door" -> Item.Tag.Door
        "Doorbell" -> Item.Tag.Doorbell
        "Driveway" -> Item.Tag.Driveway
        "Dryer" -> Item.Tag.Dryer
        "Duration" -> Item.Tag.Duration
        "Energy" -> Item.Tag.Energy
        "Entry" -> Item.Tag.Entry
        "FamilyRoom" -> Item.Tag.FamilyRoom
        "Fan" -> Item.Tag.Fan
        "FirstFloor" -> Item.Tag.FirstFloor
        "Floor" -> Item.Tag.Floor
        "Freezer" -> Item.Tag.Freezer
        "Frequency" -> Item.Tag.Frequency
        "FrontDoor" -> Item.Tag.FrontDoor
        "Garage" -> Item.Tag.Garage
        "GarageDoor" -> Item.Tag.GarageDoor
        "Garden" -> Item.Tag.Garden
        "Gas" -> Item.Tag.Gas
        "Gate" -> Item.Tag.Gate
        "GroundFloor" -> Item.Tag.GroundFloor
        "GuestRoom" -> Item.Tag.GuestRoom
        "HVAC" -> Item.Tag.HVAC
        "House" -> Item.Tag.House
        "Humidity" -> Item.Tag.Humidity
        "Indoor" -> Item.Tag.Indoor
        "InnerDoor" -> Item.Tag.InnerDoor
        "Inverter" -> Item.Tag.Inverter
        "Kitchen" -> Item.Tag.Kitchen
        "KitchenHood" -> Item.Tag.KitchenHood
        "LaundryRoom" -> Item.Tag.LaundryRoom
        "LawnMower" -> Item.Tag.LawnMower
        "Level" -> Item.Tag.Level
        "Light" -> Item.Tag.Light
        "LightStripe" -> Item.Tag.LightStripe
        "Lightbulb" -> Item.Tag.Lightbulb
        "LivingRoom" -> Item.Tag.LivingRoom
        "Lock" -> Item.Tag.Lock
        "LowBattery" -> Item.Tag.LowBattery
        "Measurement" -> Item.Tag.Measurement
        "MotionDetector" -> Item.Tag.MotionDetector
        "NetworkAppliance" -> Item.Tag.NetworkAppliance
        "Noise" -> Item.Tag.Noise
        "Office" -> Item.Tag.Office
        "Oil" -> Item.Tag.Oil
        "OpenLevel" -> Item.Tag.OpenLevel
        "OpenState" -> Item.Tag.OpenState
        "Opening" -> Item.Tag.Opening
        "Outdoor" -> Item.Tag.Outdoor
        "Oven" -> Item.Tag.Oven
        "Patio" -> Item.Tag.Patio
        "Porch" -> Item.Tag.Porch
        "Power" -> Item.Tag.Power
        "PowerOutlet" -> Item.Tag.PowerOutlet
        "Presence" -> Item.Tag.Presence
        "Pressure" -> Item.Tag.Pressure
        "Projector" -> Item.Tag.Projector
        "Pump" -> Item.Tag.Pump
        "RadiatorControl" -> Item.Tag.RadiatorControl
        "Rain" -> Item.Tag.Rain
        "Receiver" -> Item.Tag.Receiver
        "Refrigerator" -> Item.Tag.Refrigerator
        "RemoteControl" -> Item.Tag.RemoteControl
        "Room" -> Item.Tag.Room
        "Screen" -> Item.Tag.Screen
        "SecondFloor" -> Item.Tag.SecondFloor
        "Sensor" -> Item.Tag.Sensor
        "Setpoint" -> Item.Tag.Setpoint
        "Shed" -> Item.Tag.Shed
        "SideDoor" -> Item.Tag.SideDoor
        "Siren" -> Item.Tag.Siren
        "Smartphone" -> Item.Tag.Smartphone
        "Smoke" -> Item.Tag.Smoke
        "SmokeDetector" -> Item.Tag.SmokeDetector
        "SoundVolume" -> Item.Tag.SoundVolume
        "Speaker" -> Item.Tag.Speaker
        "Status" -> Item.Tag.Status
        "SummerHouse" -> Item.Tag.SummerHouse
        "Switch" -> Item.Tag.Switch
        "Tampered" -> Item.Tag.Tampered
        "Television" -> Item.Tag.Television
        "Temperature" -> Item.Tag.Temperature
        "Terrace" -> Item.Tag.Terrace
        "ThirdFloor" -> Item.Tag.ThirdFloor
        "Tilt" -> Item.Tag.Tilt
        "Timestamp" -> Item.Tag.Timestamp
        "Ultraviolet" -> Item.Tag.Ultraviolet
        "Valve" -> Item.Tag.Valve
        "Veranda" -> Item.Tag.Veranda
        "Vibration" -> Item.Tag.Vibration
        "VoiceAssistant" -> Item.Tag.VoiceAssistant
        "Voltage" -> Item.Tag.Voltage
        "WallSwitch" -> Item.Tag.WallSwitch
        "WashingMachine" -> Item.Tag.WashingMachine
        "Water" -> Item.Tag.Water
        "WeatherService" -> Item.Tag.WeatherService
        "WebService" -> Item.Tag.WebService
        "WhiteGood" -> Item.Tag.WhiteGood
        "Wind" -> Item.Tag.Wind
        "Window" -> Item.Tag.Window

        else -> Item.Tag.Unknown
    }
}
