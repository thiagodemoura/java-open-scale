package com.openscale.core.model;

/** Tipos de medição suportados, com a unidade usada internamente. */
public enum MeasurementType {
    WEIGHT("weight", "kg"),
    BMI("bmi", ""),
    BODY_FAT("fat", "%"),
    WATER("water", "%"),
    MUSCLE("muscle", "%"),
    BONE("bone", "kg"),
    LBM("lbm", "kg"),
    VISCERAL_FAT("visceral_fat", ""),
    PROTEIN("protein", "%"),
    BMR("bmr", "kcal"),
    METABOLIC_AGE("metabolic_age", "anos"),
    IMPEDANCE("impedance", "Ω");

    private final String key;
    private final String unit;

    MeasurementType(String key, String unit) {
        this.key = key;
        this.unit = unit;
    }

    /** Chave estável usada em JSON, CSV e MQTT. */
    public String key() {
        return key;
    }

    public String unit() {
        return unit;
    }
}
