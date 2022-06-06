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
import org.openhab.habdroid.R
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
    enum class Tag(val parent: Tag?, val labelResId: Int?) {
        // Legacy tags from openHAB 2
        ContactSensor(null, null),
        HeatingCoolingMode(null, null),
        TargetTemperature(null, null),

        // Fallback
        Unknown(null, null),

        // Semantic types
        Equipment(null, null),
        Location(null, null),
        Point(null, null),
        Property(null, null),

        // Semantic tags: point types
        Alarm(Point, null),
        Control(Point, null),
        Measurement(Point, null),
        LowBattery(Point, null),
        OpenLevel(Point, null),
        OpenState(Point, null),
        Setpoint(Point, null),
        Status(Point, null),
        Switch(Point, null),
        Tampered(Point, null),
        Tilt(Point, null),

        // Semantic tags: measurement types
        CO(Measurement, null),
        CO2(Measurement, null),
        ColorTemperature(Measurement, null),
        Current(Measurement, null),
        Duration(Measurement, null),
        Energy(Measurement, null),
        Frequency(Measurement, null),
        Gas(Measurement, null),
        Humidity(Measurement, null),
        Level(Measurement, null),
        Light(Measurement, null),
        Noise(Measurement, null),
        Oil(Measurement, null),
        Opening(Measurement, null),
        Power(Measurement, null),
        Presence(Measurement, null),
        Pressure(Measurement, null),
        Rain(Measurement, null),
        Smoke(Measurement, null),
        SoundVolume(Measurement, null),
        Temperature(Measurement, null),
        Timestamp(Measurement, null),
        Ultraviolet(Measurement, null),
        Vibration(Measurement, null),
        Voltage(Measurement, null),
        Water(Measurement, null),
        Wind(Measurement, null),

        // Semantic tags: location
        Apartment(Location, R.string.item_tag_location_apartment),
        Attic(Location, R.string.item_tag_location_attic),
        Basement(Location, R.string.item_tag_location_basement),
        Bathroom(Location, R.string.item_tag_location_bathroom),
        Bedroom(Location, R.string.item_tag_location_bedroom),
        BoilerRoom(Location, R.string.item_tag_location_boilerroom),
        Building(Location, R.string.item_tag_location_building),
        Carport(Location, R.string.item_tag_location_carport),
        Cellar(Location, R.string.item_tag_location_cellar),
        Corridor(Location, R.string.item_tag_location_corridor),
        DiningRoom(Location, R.string.item_tag_location_diningroom),
        Driveway(Location, R.string.item_tag_location_driveway),
        Entry(Location, R.string.item_tag_location_entry),
        FamilyRoom(Location, R.string.item_tag_location_familyroom),
        FirstFloor(Location, R.string.item_tag_location_firstfloor),
        Floor(Location, R.string.item_tag_location_floor),
        Garage(Location, R.string.item_tag_location_garage),
        Garden(Location, R.string.item_tag_location_garden),
        GroundFloor(Location, R.string.item_tag_location_groundfloor),
        GuestRoom(Location, R.string.item_tag_location_guestroom),
        House(Location, R.string.item_tag_location_house),
        Indoor(Location, R.string.item_tag_location_indoor),
        Kitchen(Location, R.string.item_tag_location_kitchen),
        LaundryRoom(Location, R.string.item_tag_location_laundryroom),
        LivingRoom(Location, R.string.item_tag_location_livingroom),
        Office(Location, R.string.item_tag_location_office),
        Outdoor(Location, R.string.item_tag_location_outdoor),
        Patio(Location, R.string.item_tag_location_patio),
        Porch(Location, R.string.item_tag_location_porch),
        Room(Location, R.string.item_tag_location_room),
        SecondFloor(Location, R.string.item_tag_location_secondfloor),
        Shed(Location, R.string.item_tag_location_shed),
        SummerHouse(Location, R.string.item_tag_location_summerhouse),
        Terrace(Location, R.string.item_tag_location_terrace),
        ThirdFloor(Location, R.string.item_tag_location_thirdfloor),
        Veranda(Location, R.string.item_tag_location_veranda),

        // Semantic tags: equipment
        AlarmSystem(Equipment, R.string.item_tag_equipment_alarmsystem),
        BackDoor(Equipment, R.string.item_tag_equipment_backdoor),
        Battery(Equipment, R.string.item_tag_equipment_battery),
        Blinds(Equipment, R.string.item_tag_equipment_blinds),
        Boiler(Equipment, R.string.item_tag_equipment_boiler),
        Camera(Equipment, R.string.item_tag_equipment_camera),
        Car(Equipment, R.string.item_tag_equipment_car),
        CeilingFan(Equipment, R.string.item_tag_equipment_ceilingfan),
        CellarDoor(Equipment, R.string.item_tag_equipment_cellardoor),
        CleaningRobot(Equipment, R.string.item_tag_equipment_cleaningrobot),
        Dishwasher(Equipment, R.string.item_tag_equipment_dishwasher),
        Door(Equipment, R.string.item_tag_equipment_door),
        Doorbell(Equipment, R.string.item_tag_equipment_doorbell),
        Dryer(Equipment, R.string.item_tag_equipment_dryer),
        Fan(Equipment, R.string.item_tag_equipment_fan),
        Freezer(Equipment, R.string.item_tag_equipment_freezer),
        FrontDoor(Equipment, R.string.item_tag_equipment_frontdoor),
        GarageDoor(Equipment, R.string.item_tag_equipment_garagedoor),
        Gate(Equipment, R.string.item_tag_equipment_gate),
        HVAC(Equipment, R.string.item_tag_equipment_hvac),
        InnerDoor(Equipment, R.string.item_tag_equipment_innerdoor),
        Inverter(Equipment, R.string.item_tag_equipment_inverter),
        KitchenHood(Equipment, R.string.item_tag_equipment_kitchenhood),
        LawnMower(Equipment, R.string.item_tag_equipment_lawnmower),
        Lightbulb(Equipment, R.string.item_tag_equipment_lightbulb),
        LightStripe(Equipment, R.string.item_tag_equipment_lightstripe),
        Lock(Equipment, R.string.item_tag_equipment_lock),
        MotionDetector(Equipment, R.string.item_tag_equipment_motiondetector),
        NetworkAppliance(Equipment, R.string.item_tag_equipment_networkappliance),
        Oven(Equipment, R.string.item_tag_equipment_oven),
        PowerOutlet(Equipment, R.string.item_tag_equipment_poweroutlet),
        Projector(Equipment, R.string.item_tag_equipment_projector),
        Pump(Equipment, R.string.item_tag_equipment_pump),
        RadiatorControl(Equipment, R.string.item_tag_equipment_radiatorcontrol),
        Receiver(Equipment, R.string.item_tag_equipment_receiver),
        Refrigerator(Equipment, R.string.item_tag_equipment_refrigerator),
        RemoteControl(Equipment, R.string.item_tag_equipment_remotecontrol),
        Screen(Equipment, R.string.item_tag_equipment_screen),
        Sensor(Equipment, R.string.item_tag_equipment_sensor),
        SideDoor(Equipment, R.string.item_tag_equipment_sidedoor),
        Siren(Equipment, R.string.item_tag_equipment_siren),
        Smartphone(Equipment, R.string.item_tag_equipment_smartphone),
        SmokeDetector(Equipment, R.string.item_tag_equipment_smokedetector),
        Speaker(Equipment, R.string.item_tag_equipment_speaker),
        Television(Equipment, R.string.item_tag_equipment_television),
        Valve(Equipment, R.string.item_tag_equipment_valve),
        VoiceAssistant(Equipment, R.string.item_tag_equipment_voiceassistant),
        WallSwitch(Equipment, R.string.item_tag_equipment_wallswitch),
        WashingMachine(Equipment, R.string.item_tag_equipment_washingmachine),
        WeatherService(Equipment, R.string.item_tag_equipment_weatherservice),
        WebService(Equipment, R.string.item_tag_equipment_webservice),
        WhiteGood(Equipment, R.string.item_tag_equipment_whitegood),
        Window(Equipment, R.string.item_tag_equipment_window)
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
