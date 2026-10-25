package com.openscale.core.scale;

import com.openscale.core.model.ScaleMeasurement;

import java.util.UUID;

/** O que um handler pode fazer: falar com a balança e reportar resultados. */
public interface ScaleContext {

    void enableNotifications(UUID service, UUID characteristic);

    void write(UUID service, UUID characteristic, byte[] value, boolean withResponse);

    /** Medição definitiva, que será repassada aos alvos de sincronização. */
    void publish(ScaleMeasurement measurement);

    /** Peso provisório enquanto a balança estabiliza. */
    void liveWeight(double kg);

    /** Mensagem informativa para o usuário ("suba na balança" etc.). */
    void info(String message);

    void debug(String message);
}
