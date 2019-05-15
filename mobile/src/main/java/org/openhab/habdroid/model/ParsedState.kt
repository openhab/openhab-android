package org.openhab.habdroid.model

import android.location.Location
import android.os.Parcelable

import kotlinx.android.parcel.Parcelize

import java.util.IllegalFormatException
import java.util.Locale
import java.util.regex.Pattern

@Parcelize
data class ParsedState internal constructor(val asString: String, val asBoolean: Boolean,
                       val asNumber: NumberState?, val asHsv: FloatArray?,
                       val asBrightness: Int?, val asLocation: Location?) : Parcelable {
    companion object {
        internal fun parseAsBoolean(state: String): Boolean {
            // If state is ON for switches return True
            if (state == "ON") {
                return true
            }

            val brightness = parseAsBrightness(state)
            if (brightness != null) {
                return brightness != 0
            }
            return try {
                val decimalValue = Integer.valueOf(state)
                decimalValue > 0
            } catch (e: NumberFormatException) {
                false
            }
        }

        internal fun parseAsNumber(state: String, format: String?): NumberState? {
            return when (state) {
                "ON" -> NumberState(100)
                "OFF" -> NumberState(0)
                else -> {
                    val spacePos = state.indexOf(' ')
                    val number = if (spacePos >= 0) state.substring(0, spacePos) else state
                    val unit = if (spacePos >= 0) state.substring(spacePos + 1) else null
                    return try {
                        if (number.indexOf('.') > 0)
                            NumberState(number.toFloat(), unit, format)
                        else NumberState(number.toInt(), unit, format)
                    } catch (e: NumberFormatException) {
                        null
                    }
                }
            }
        }

        internal fun parseAsHsv(state: String): FloatArray? {
            val stateSplit = state.split(",")
            if (stateSplit.size == 3) { // We need exactly 3 numbers to operate this
                try {
                    return floatArrayOf(stateSplit[0].toFloat(), stateSplit[1].toFloat() / 100, stateSplit[2].toFloat() / 100)
                } catch (e: NumberFormatException) {
                    // fall through
                }

            }
            return null
        }

        internal fun parseAsLocation(state: String): Location? {
            val splitState = state.split(",")
            // Valid states are either "latitude,longitude" or "latitude,longitude,elevation",
            if (splitState.size == 2 || splitState.size == 3) {
                try {
                    val l = Location("openhab")
                    l.latitude = splitState[0].toDouble()
                    l.longitude = splitState[1].toDouble()
                    l.time = System.currentTimeMillis()
                    if (splitState.size == 3) {
                        l.altitude = splitState[2].toDouble()
                    }
                    // Do our best to avoid parsing e.g. HSV values into location by
                    // sanity checking the values
                    if (Math.abs(l.latitude) <= 90 && Math.abs(l.longitude) <= 180) {
                        return l
                    }
                } catch (e: NumberFormatException) {
                    // ignored
                }
            }
            return null
        }

        internal fun parseAsBrightness(state: String): Int? {
            val hsbMatcher = HSB_PATTERN.matcher(state)
            if (hsbMatcher.find()) {
                try {
                    return hsbMatcher.group(3).toInt()
                } catch (e: NumberFormatException) {
                    // fall through
                }

            }
            return null
        }

        private val HSB_PATTERN = Pattern.compile("^([0-9]*\\.?[0-9]+),([0-9]*\\.?[0-9]+),([0-9]*\\.?[0-9]+)$")
    }

    @Parcelize
    class NumberState internal constructor(val value: Number, val unit: String?, val format: String?) : Parcelable {
        constructor(value: Int) : this(value, null, null)
        constructor(value: Float) : this(value, null, null)

        override fun toString(): String {
            return toString(Locale.getDefault())
        }

        /**
         * Like [toString][.toString], but using a specific locale for formatting.
         */
        fun toString(locale: Locale): String {
            if (!format.isNullOrEmpty()) {
                val actualFormat = format.replace("%unit%", unit ?: "")
                try {
                    return String.format(locale, actualFormat, value)
                } catch (e: IllegalFormatException) {
                    // State format pattern doesn't match the actual data type
                    // -> ignore and fall back to our own formatting
                }

            }
            return if (unit == null) formatValue() else formatValue() + " " + unit
        }

        fun formatValue(): String {
            return value.toString()
        }

        companion object {

            /**
             * Returns a new NumberState instance, basing its contents on the passed-in previous state.
             * In particular, unit, format and number type (float/integer) will be taken from the
             * previous state. If previous state is integer, the new value will be rounded accordingly.
             * @param state Previous state to base on
             * @param value New numeric value
             * @return new NumberState instance
             */
            fun withValue(state: NumberState?, value: Float): NumberState {
                if (state == null) {
                    return NumberState(value)
                }
                if (state.value is Int) {
                    return NumberState(Math.round(value), state.unit, state.format)
                }
                return NumberState(value, state.unit, state.format)
            }
        }
    }
}

/**
 * Parses a state string into the parsed representation.
 *
 * @param numberPattern Format to use when parsing the input as number
 * @return null if state string is null, parsed representation otherwise
 */
fun String?.toParsedState(numberPattern: String? = null): ParsedState? {
    if (this == null) {
        return null
    }
    return ParsedState(this, ParsedState.parseAsBoolean(this),
            ParsedState.parseAsNumber(this, numberPattern),
            ParsedState.parseAsHsv(this), ParsedState.parseAsBrightness(this),
            ParsedState.parseAsLocation(this))
}

