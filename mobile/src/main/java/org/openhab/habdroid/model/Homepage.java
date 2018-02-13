package org.openhab.habdroid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class Homepage {
    @Getter
    @JsonProperty
    private String link;
    @Getter
    @JsonProperty
    private boolean leaf;
    @Getter
    @JsonProperty
    private boolean timeout;
    @Getter
    @JsonProperty
    private List<OpenHABWidget> widgets;
}
