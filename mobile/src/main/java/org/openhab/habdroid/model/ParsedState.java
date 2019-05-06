package org.openhab.habdroid.model;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoValue
public abstract class ParsedState implements Parcelable {
    public static class NumberState implements Parcelable {
        public final Number mValue;
        public final String mUnit;
        public final String mFormat;

        public NumberState(int value) {
            this(new Integer(value), null, null);
        }

        public NumberState(float value) {
            this(new Float(value), null, null);
        }

        private NumberState(@NonNull Number value, String unit, String format) {
            assert value instanceof Float || value instanceof Integer;
            mValue = value;
            mUnit = unit;
            mFormat = format;
        }

        /**
         * Returns a new NumberState instance, basing its contents on the passed-in previous state.
         * In particular, unit, format and number type (float/integer) will be taken from the
         * previous state. If previous state is integer, the new value will be rounded accordingly.
         * @param state Previous state to base on
         * @param value New numeric value
         * @return new NumberState instance
         */
        public static NumberState withValue(NumberState state, float value) {
            if (state == null) {
                return new NumberState(value);
            }
            // Cast is important here to suppress automatic type conversion
            // (https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.25)
            @SuppressWarnings("RedundantCast")
            final Number number = state.mValue instanceof Integer
                    ? (Number) new Integer(Math.round(value))
                    : (Number) new Float(value);
            return new NumberState(number, state.mUnit, state.mFormat);
        }

        @Override
        public String toString() {
            return toString(Locale.getDefault());
        }

        /**
         * Like {@link #toString() toString}, but using a specific locale for formatting.
         */
        public String toString(Locale locale) {
            if (mFormat != null && !mFormat.isEmpty()) {
                final String actualFormat = mFormat.replace("%unit%", mUnit != null ? mUnit : "");
                try {
                    return String.format(locale, actualFormat, mValue);
                } catch (IllegalFormatException e) {
                    // State format pattern doesn't match the actual data type
                    // -> ignore and fall back to our own formatting
                }
            }
            if (mUnit == null) {
                return formatValue();
            }
            return formatValue() + " " + mUnit;
        }

        public String formatValue() {
            return mValue.toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            if (mValue instanceof Float) {
                parcel.writeInt(1);
                parcel.writeFloat(mValue.floatValue());
            } else {
                parcel.writeInt(0);
                parcel.writeInt(mValue.intValue());
            }
            parcel.writeString(mUnit);
            parcel.writeString(mFormat);
        }

        public static final Parcelable.Creator<NumberState> CREATOR =
                new Parcelable.Creator<NumberState>() {
                    @Override
                    public NumberState createFromParcel(Parcel in) {
                        Number value = in.readInt() != 0 ? in.readFloat() : in.readInt();
                        return new NumberState(value, in.readString(), in.readString());
                    }
                    @Override
                    public NumberState[] newArray(int size) {
                        return new NumberState[size];
                    }
                };
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder asString(String state);
        abstract Builder asBoolean(boolean bool);
        abstract Builder asNumber(@Nullable NumberState number);
        abstract Builder asHsv(@Nullable float[] hsv);
        abstract Builder asBrightness(@Nullable Integer brightness);
        abstract Builder asLocation(@Nullable Location location);
        abstract ParsedState build();
    }

    /**
     * Parses a state string into the parsed representation.
     *
     * @param state State string to parse
     * @param numberPattern Format to use when parsing the input as number
     * @return null if state string is null, parsed representation otherwise
     */
    public static ParsedState from(String state, String numberPattern) {
        if (state == null) {
            return null;
        }
        return new AutoValue_ParsedState.Builder()
                .asString(state)
                .asBoolean(parseAsBoolean(state))
                .asNumber(parseAsNumber(state, numberPattern))
                .asHsv(parseAsHsv(state))
                .asBrightness(parseAsBrightness(state))
                .asLocation(parseAsLocation(state))
                .build();
    }

    public abstract String asString();
    public abstract boolean asBoolean();
    @Nullable
    public abstract NumberState asNumber();
    @SuppressWarnings("mutable")
    @Nullable
    public abstract float[] asHsv();
    @Nullable
    public abstract Integer asBrightness();
    @Nullable
    public abstract Location asLocation();

    private static boolean parseAsBoolean(String state) {
        // If state is ON for switches return True
        if (state.equals("ON")) {
            return true;
        }

        Integer brightness = parseAsBrightness(state);
        if (brightness != null) {
            return brightness != 0;
        }
        try {
            int decimalValue = Integer.valueOf(state);
            return decimalValue > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static NumberState parseAsNumber(String state, String format) {
        if ("ON".equals(state)) {
            return new NumberState(100);
        } else if ("OFF".equals(state)) {
            return new NumberState(0);
        } else {
            int spacePos = state.indexOf(' ');
            String number = spacePos >= 0 ? state.substring(0, spacePos) : state;
            String unit = spacePos >= 0 ? state.substring(spacePos + 1) : null;
            try {
                // Cast is important here to suppress automatic type conversion
                // (https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.25)
                @SuppressWarnings("RedundantCast")
                final Number parsedNumber = number.indexOf('.') >= 0
                        ? (Number) new Float(Float.parseFloat(number))
                        : (Number) new Integer(Integer.parseInt(number));
                return new NumberState(parsedNumber, unit, format);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static float[] parseAsHsv(String state) {
        String[] stateSplit = state.split(",");
        if (stateSplit.length == 3) { // We need exactly 3 numbers to operate this
            try {
                return new float[]{
                        Float.parseFloat(stateSplit[0]),
                        Float.parseFloat(stateSplit[1]) / 100,
                        Float.parseFloat(stateSplit[2]) / 100
                };
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    private static Location parseAsLocation(String state) {
        String[] splitState = state.split(",");
        // Valid states are either "latitude,longitude" or "latitude,longitude,elevation",
        // (we ignore elevation in the latter case)
        if (splitState.length == 2 || splitState.length == 3) {
            try {
                Location l = new Location("openhab");
                l.setLatitude(Float.valueOf(splitState[0]));
                l.setLongitude(Float.valueOf(splitState[1]));
                l.setTime(System.currentTimeMillis());
                // Do our best to avoid parsing e.g. HSV values into location by
                // sanity checking the values
                if (Math.abs(l.getLatitude()) <= 90 && Math.abs(l.getLongitude()) <= 90) {
                    return l;
                }
            } catch (NumberFormatException e) {
                // ignored
            }
        }
        return null;
    }

    private static Integer parseAsBrightness(String state) {
        Matcher hsbMatcher = HSB_PATTERN.matcher(state);
        if (hsbMatcher.find()) {
            try {
                return Float.valueOf(hsbMatcher.group(3)).intValue();
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    private static final Pattern HSB_PATTERN =
            Pattern.compile("^([0-9]*\\.?[0-9]+),([0-9]*\\.?[0-9]+),([0-9]*\\.?[0-9]+)$");
}
