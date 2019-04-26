package org.openhab.habdroid.model;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import org.openhab.habdroid.background.BackgroundTasksManager;
import org.openhab.habdroid.ui.WriteTagActivity;

import static org.openhab.habdroid.background.BackgroundTasksManager.WORKER_TAG_PREFIX_NFC;

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
    public abstract boolean isOpenHabTag();
    public abstract boolean isSitemapTag();
    public abstract boolean hasLabelAndMappedState();

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder sitemap(String sitemap);
        abstract Builder item(String item);
        abstract Builder label(String label);
        abstract Builder state(String state);
        abstract Builder mappedState(String mappedState);
        abstract Builder isOpenHabTag(boolean isOpenHabTag);
        abstract Builder isSitemapTag(boolean isSitemapTag);
        abstract Builder hasLabelAndMappedState(boolean hasLabelAndMappedState);

        abstract NfcTag build();
    }

    public static NfcTag fromTagData(Uri uri) {
        String sitemap = uri.getPath();
        String item = TextUtils.isEmpty(uri.getQueryParameter("item")) ?
                uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_ITEM_NAME)
                : uri.getQueryParameter("item");
        String label = uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_ITEM_LABEL);
        String state = TextUtils.isEmpty(uri.getQueryParameter("command")) ?
                uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_STATE)
                : uri.getQueryParameter("command");
        String mappedState = uri.getQueryParameter(WriteTagActivity.QUERY_PARAMETER_MAPPED_STATE);

        return new AutoValue_NfcTag.Builder()
                .sitemap(sitemap)
                .item(item)
                .label(label)
                .state(state)
                .mappedState(mappedState)
                .isOpenHabTag("openhab".equals(uri.getScheme()))
                .isSitemapTag(!TextUtils.isEmpty(sitemap) && TextUtils.isEmpty(item))
                .hasLabelAndMappedState(TextUtils.isEmpty(label) || TextUtils.isEmpty(mappedState))
                .build();
    }

    public boolean mustOpenSitemap() {
        if (!isOpenHabTag()) {
            return false;
        }
        if (isSitemapTag()) {
            return true;
        }
        BackgroundTasksManager.enqueueItemUpload(WORKER_TAG_PREFIX_NFC + item(), item(), state());
        return false;
    }
}
