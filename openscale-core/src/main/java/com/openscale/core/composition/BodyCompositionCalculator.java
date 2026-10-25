package com.openscale.core.composition;

/**
 * Contribui com indicadores para uma {@link BodyComposition}. Os calculadores
 * rodam em ordem; cada um só preenche o que ainda está vazio
 * ({@link BodyComposition.Builder#offer}).
 *
 * <p>Para usar o algoritmo do fabricante (por exemplo o Wla25BodyComposition do
 * openScale, para a Relaxmedic), implemente esta interface e coloque-a antes
 * dos estimadores genéricos.
 */
@FunctionalInterface
public interface BodyCompositionCalculator {

    void contribute(BodyComposition.Builder builder);
}
