package com.openscale.core.composition;

/** Indicadores de composição corporal devolvidos por {@link BodyComposition}. */
public enum Metric {
    WEIGHT("Peso", "kg"),
    BMI("IMC", "kg/m²"),
    BODY_FAT_PERCENT("Gordura corporal", "%"),
    FAT_MASS("Massa gorda", "kg"),
    VISCERAL_FAT("Gordura visceral", "índice"),
    MUSCLE_MASS("Massa muscular", "kg"),
    SKELETAL_MUSCLE("Músculo esquelético", "kg"),
    BONE_MASS("Massa óssea", "kg"),
    PROTEIN_PERCENT("Proteína", "%"),
    BODY_WATER_PERCENT("Água corporal", "%"),
    EXTRACELLULAR_WATER("Água extracelular (ECW)", "kg"),
    BMR("Metabolismo basal", "kcal/dia"),
    METABOLIC_AGE("Idade corporal", "anos"),
    FAT_FREE_MASS("Massa livre de gordura (FFM)", "kg");

    private final String label;
    private final String unit;

    Metric(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }
}
