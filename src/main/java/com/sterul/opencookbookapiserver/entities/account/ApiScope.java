package com.sterul.opencookbookapiserver.entities.account;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What an api key may do. Named per feature, read and write apart. */
public enum ApiScope {

    SHOPPING_READ("shopping:read"),
    SHOPPING_WRITE("shopping:write", SHOPPING_READ);

    private final String value;
    private final ApiScope implied;

    ApiScope(String value) {
        this(value, null);
    }

    ApiScope(String value, ApiScope implied) {
        this.value = value;
        this.implied = implied;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ApiScope of(String value) {
        return Arrays.stream(values()).filter(scope -> scope.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scope " + value));
    }

    /** Writing is useless without reading what was written, so a write scope brings its read scope. */
    public static Set<ApiScope> withImplied(Set<ApiScope> chosen) {
        var all = EnumSet.noneOf(ApiScope.class);
        chosen.forEach(scope -> {
            all.add(scope);
            if (scope.implied != null) {
                all.add(scope.implied);
            }
        });
        return all;
    }
}
