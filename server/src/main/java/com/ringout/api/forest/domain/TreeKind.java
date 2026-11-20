package com.ringout.api.forest.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TreeKind {

    PINE("pine"),
    OAK("oak"),
    BIG_TREE("big_tree");

    @JsonValue
    private final String value;

    @JsonCreator
    public static TreeKind from(String value) {
        return Arrays.stream(values())
            .filter(kind -> kind.value.equals(value))
            .findFirst()
            .orElse(null);
    }
}
