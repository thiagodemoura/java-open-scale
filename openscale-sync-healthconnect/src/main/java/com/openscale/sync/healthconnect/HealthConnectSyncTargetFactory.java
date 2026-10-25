package com.openscale.sync.healthconnect;

import com.openscale.core.sync.SyncConfig;
import com.openscale.core.sync.SyncTarget;
import com.openscale.core.sync.SyncTargetFactory;

import java.time.ZoneId;
import java.util.Optional;

/**
 * <pre>
 * sync.healthconnect.enabled=true
 * sync.healthconnect.writer=log                       (ou o nome de uma classe HealthConnectWriter)
 * sync.healthconnect.zone=America/Fortaleza
 * </pre>
 * Fora do Android, use {@code log}. No Android, prefira criar o
 * {@link HealthConnectSyncTarget} diretamente passando o seu writer.
 */
public final class HealthConnectSyncTargetFactory implements SyncTargetFactory {

    @Override
    public String id() {
        return "healthconnect";
    }

    @Override
    public Optional<SyncTarget> create(SyncConfig c) {
        String writerName = c.get(id(), "writer", "log");
        HealthConnectWriter writer;
        if (writerName.equalsIgnoreCase("log")) {
            writer = new LoggingHealthConnectWriter();
        } else {
            try {
                writer = (HealthConnectWriter) Class.forName(writerName).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalArgumentException("Writer inválido: " + writerName, e);
            }
        }
        ZoneId zone = ZoneId.of(c.get(id(), "zone", ZoneId.systemDefault().getId()));
        return Optional.of(new HealthConnectSyncTarget(writer, zone));
    }
}
