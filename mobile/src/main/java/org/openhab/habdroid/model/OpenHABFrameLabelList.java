package org.openhab.habdroid.model;

import java.util.ArrayList;

public class OpenHABFrameLabelList extends ArrayList<String> {
    public static final String NONE = "none";

    private static OpenHABFrameLabelList INSTANCE;

    public static OpenHABFrameLabelList getInstance() {
        if (INSTANCE == null){
            INSTANCE = new OpenHABFrameLabelList();
        }
        return INSTANCE;
    }

    @Override
    public void clear() {
        super.clear();
        add(NONE);
    }
}
