package org.openhab.habdroid.model;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import org.openhab.habdroid.ui.WriteTagActivity;

@AutoValue
public abstract class NfcTag {
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
        if (uri == null || !"openhab".equals(uri.getScheme())) {
            return null;
        }
        String sitemap = uri.getPath();
        String item = TextUtils.isEmpty(uri.getQueryParameter("item"))
                ? uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_ITEM_NAME)
                : uri.getQueryParameter("item");
        String label = uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_ITEM_LABEL);
        String state = TextUtils.isEmpty(uri.getQueryParameter("command"))
                ? uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_STATE)
                : uri.getQueryParameter("command");
        String mappedState = uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_MAPPED_STATE);

        return new AutoValue_NfcTag.Builder()
                .sitemap(!TextUtils.isEmpty(sitemap) && TextUtils.isEmpty(item) ? sitemap : null)
                .item(item)
                .label(label)
                .state(state)
                .mappedState(mappedState)
                .build();
    }
}
