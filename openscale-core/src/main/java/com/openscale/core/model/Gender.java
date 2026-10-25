package com.openscale.core.model;

public enum Gender {
    MALE,
    FEMALE;

    public boolean isMale() {
        return this == MALE;
    }

    public static Gender parse(String value) {
        if (value == null) return MALE;
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "F", "FEMALE", "FEMININO", "MULHER" -> FEMALE;
            default -> MALE;
        };
    }
}
