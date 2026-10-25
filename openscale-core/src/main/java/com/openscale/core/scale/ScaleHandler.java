package com.openscale.core.scale;

import com.openscale.core.model.ScaleUser;

import java.util.Objects;
import java.util.UUID;

/**
 * Implementa o protocolo de um modelo (ou família) de balança.
 * Uma instância por conexão: o handler pode guardar estado entre frames.
 *
 * <p>Equivale ao {@code ScaleDeviceHandler} do openScale. Para portar outro
 * handler de lá, mapeie {@code setNotifyOn} para {@link ScaleContext#enableNotifications},
 * {@code writeTo} para {@link ScaleContext#write} e {@code publish} para
 * {@link ScaleContext#publish}.
 */
public abstract class ScaleHandler {

    private ScaleContext context;

    final void bind(ScaleContext context) {
        this.context = Objects.requireNonNull(context);
    }

    protected final ScaleContext ctx() {
        if (context == null) throw new IllegalStateException("handler não vinculado a uma sessão");
        return context;
    }

    /** Chamado após a conexão GATT: inscreva notificações e mande o handshake. */
    public abstract void onConnected(ScaleUser user);

    /** Cada notify/indicate recebido. */
    public abstract void onNotification(UUID characteristic, byte[] data, ScaleUser user);

    public void onDisconnected() {
    }
}
