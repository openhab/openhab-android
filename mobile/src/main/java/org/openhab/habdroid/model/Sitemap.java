package org.openhab.habdroid.model;

import android.os.Parcelable;
import android.support.annotation.NonNull;

public interface Sitemap extends Parcelable {
    String getName();
    String getLink();
    String getHomepageLink();
    String getIcon();
    @NonNull String getLabel();
    boolean isLeaf();
    String getIconPath();
}
