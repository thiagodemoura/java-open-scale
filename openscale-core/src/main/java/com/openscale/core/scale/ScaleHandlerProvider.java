package com.openscale.core.scale;

import java.util.Set;

/**
 * Ponto de extensão para novas balanças, descoberto via {@link java.util.ServiceLoader}.
 *
 * <p>Para registrar uma balança, implemente esta interface e liste a classe em
 * {@code META-INF/services/com.openscale.core.scale.ScaleHandlerProvider}.
 */
public interface ScaleHandlerProvider {

    /** Nome legível, por exemplo "Relaxmedic". */
    String displayName();

    /** O handler reconhece o nome anunciado pelo dispositivo? */
    boolean supports(String advertisedName);

    Set<Capability> capabilities();

    /** Cria uma instância nova (com estado limpo) para uma conexão. */
    ScaleHandler create();
}
