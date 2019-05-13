package org.openhab.habdroid.model;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NfcTag {
    public static final String SCHEME = "openhab";
    public static final String QUERY_PARAMETER_ITEM_NAME = "i";
    public static final String DEPRECATED_QUERY_PARAMETER_ITEM_NAME = "item";
    public static final String QUERY_PARAMETER_STATE = "s";
    public static final String DEPRECATED_QUERY_PARAMETER_STATE = "command";
    public static final String QUERY_PARAMETER_MAPPED_STATE = "m";
    public static final String QUERY_PARAMETER_ITEM_LABEL = "l";

    @Nullable
    public abstract String sitemap();
    @Nullable
    public abstract String item();
    @Nullable
    public abstract String label();
    @Nullable
    public abstract String state();
    @Nullable
    public abstract String mappedState();

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder sitemap(String sitemap);
        abstract Builder item(String item);
        abstract Builder label(String label);
        abstract Builder state(String state);
        abstract Builder mappedState(String mappedState);

        abstract NfcTag build();
    }

    public static @Nullable NfcTag fromTagData(Uri uri) {
        if (uri == null || !SCHEME.equals(uri.getScheme())) {
            return null;
        }
        String sitemap = uri.getPath();
        String item = TextUtils.isEmpty(uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_ITEM_NAME))
                ? uri.getQueryParameter(QUERY_PARAMETER_ITEM_NAME)
                : uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_ITEM_NAME);
        String label = uri.getQueryParameter(QUERY_PARAMETER_ITEM_LABEL);
        String state = TextUtils.isEmpty(uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_STATE))
                ? uri.getQueryParameter(QUERY_PARAMETER_STATE)
                : uri.getQueryParameter(DEPRECATED_QUERY_PARAMETER_STATE);
        String mappedState = uri.getQueryParameter(QUERY_PARAMETER_MAPPED_STATE);

        return new AutoValue_NfcTag.Builder()
                .sitemap(!TextUtils.isEmpty(sitemap) && TextUtils.isEmpty(item) ? sitemap : null)
                .item(item)
                .label(label)
                .state(state)
                .mappedState(mappedState)
                .build();
    }
}
