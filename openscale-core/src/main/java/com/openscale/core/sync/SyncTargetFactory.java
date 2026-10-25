package com.openscale.core.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Ponto de extensão para novos destinos de sincronização, descoberto via ServiceLoader
 * ({@code META-INF/services/com.openscale.core.sync.SyncTargetFactory}).
 */
public interface SyncTargetFactory {

    /** Mesmo id usado nas propriedades {@code sync.<id>.*}. */
    String id();

    /** Cria o alvo se estiver habilitado na configuração. */
    Optional<SyncTarget> create(SyncConfig config);

    /** Cria todos os alvos habilitados que estiverem no classpath. */
    static List<SyncTarget> createEnabled(SyncConfig config) {
        List<SyncTarget> out = new ArrayList<>();
        for (SyncTargetFactory f : ServiceLoader.load(SyncTargetFactory.class)) {
            if (config.enabled(f.id())) f.create(config).ifPresent(out::add);
        }
        return out;
    }

    static List<String> available() {
        List<String> ids = new ArrayList<>();
        ServiceLoader.load(SyncTargetFactory.class).forEach(f -> ids.add(f.id()));
        return ids;
    }
}
