package com.openscale.core.scale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/** Lista de balanças conhecidas. Carrega os providers do classpath via ServiceLoader. */
public final class ScaleHandlerRegistry {

    private final List<ScaleHandlerProvider> providers = new ArrayList<>();

    public static ScaleHandlerRegistry fromClasspath() {
        ScaleHandlerRegistry r = new ScaleHandlerRegistry();
        ServiceLoader.load(ScaleHandlerProvider.class).forEach(r::register);
        return r;
    }

    public ScaleHandlerRegistry register(ScaleHandlerProvider provider) {
        providers.add(provider);
        return this;
    }

    /** Primeiro provider que reconhece o nome anunciado. */
    public Optional<ScaleHandlerProvider> find(String advertisedName) {
        if (advertisedName == null) return Optional.empty();
        return providers.stream().filter(p -> p.supports(advertisedName)).findFirst();
    }

    public List<ScaleHandlerProvider> all() {
        return Collections.unmodifiableList(providers);
    }
}
