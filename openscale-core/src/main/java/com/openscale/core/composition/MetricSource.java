package com.openscale.core.composition;

/** De onde veio o valor de um indicador. */
public enum MetricSource {
    /** Medido pela balança ou calculado pelo algoritmo do fabricante/handler. */
    SCALE,
    /** Conta exata sobre outros valores (IMC, massa gorda = peso × gordura%). */
    CALCULATED,
    /** Equação científica genérica; útil para tendência, não bate com o app do fabricante. */
    ESTIMATED,
    /** Não há como obter com os dados disponíveis. */
    UNAVAILABLE
}
