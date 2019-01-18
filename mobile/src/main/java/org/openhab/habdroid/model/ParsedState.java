package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.auto.value.AutoValue;

import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

        public static NumberState withValue(NumberState state, @NonNull Number value) {
            return new NumberState(value, state != null ? state.mUnit : null,
                    state != null ? state.mFormat : null);
        }

        @Override
        public String toString() {
            return toString(Locale.getDefault());
        }

        /**
         * Like {@link #toString() toString}, but using a specific locale for formatting.
         */
        public String toString(Locale locale) {
            if (mFormat != null) {
                final String actualFormat = mFormat.replace("%unit%", mUnit != null ? mUnit : "");
                try {
                    return String.format(locale, actualFormat, mValue);
                } catch (UnknownFormatConversionException | IllegalFormatConversionException e) {
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
            // Skip decimals if value is integer
            return mValue.floatValue() == mValue.intValue()
                    ? String.valueOf(mValue.intValue()) : String.valueOf(mValue.floatValue());
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
                        if (in.readInt() != 0) {
                            return new NumberState(in.readInt(), in.readString(), in.readString());
                        } else {
                            return new NumberState(in.readFloat(), in.readString(), in.readString());
                        }
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
                return new NumberState(Float.parseFloat(number), unit, format);
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
