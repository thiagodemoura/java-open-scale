package com.openscale.core.sync;

import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;

/**
 * Destino de sincronização de medições (Health Connect, MQTT, Wger, CSV...).
 * Equivale aos "sync providers" do openScale-sync.
 */
public interface SyncTarget extends AutoCloseable {

    /** Identificador curto, por exemplo "mqtt". */
    String id();

    /** Envia uma medição. Deve ser idempotente quando o destino permitir. */
    void sync(ScaleMeasurement measurement, ScaleUser user) throws SyncException;

    @Override
    default void close() {
    }
}
