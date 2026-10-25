package com.openscale.sync.healthconnect;

import java.util.List;

/**
 * Grava registros no Health Connect. A implementação real vive no app Android,
 * porque a API do Health Connect só existe lá. Exemplo em Kotlin no README.
 */
@FunctionalInterface
public interface HealthConnectWriter {

    void insert(List<HealthConnectRecord> records) throws Exception;
}
