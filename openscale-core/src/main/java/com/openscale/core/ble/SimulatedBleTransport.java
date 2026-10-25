package com.openscale.core.ble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Transporte em memória: registra o que o handler escreve e permite injetar
 * frames como se viessem da balança. Útil para testes, para a CLI de
 * demonstração e para reproduzir capturas reais (por exemplo do nRF Connect).
 */
public class SimulatedBleTransport implements BleTransport {

    /** Uma escrita feita pelo handler. */
    public record Write(UUID service, UUID characteristic, byte[] value, boolean withResponse) {
    }

    private final String deviceName;
    private Listener listener;
    private boolean connected;
    private final Set<UUID> subscribed = new LinkedHashSet<>();
    private final List<Write> writes = new ArrayList<>();
    private Consumer<Write> onWrite = w -> { };

    public SimulatedBleTransport(String deviceName) {
        this.deviceName = deviceName;
    }

    /** Callback chamado a cada escrita; permite responder como a balança faria. */
    public void onWrite(Consumer<Write> callback) {
        this.onWrite = callback == null ? w -> { } : callback;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void connect() {
        connected = true;
        if (listener != null) listener.onConnected(deviceName);
    }

    @Override
    public void enableNotifications(UUID service, UUID characteristic) {
        subscribed.add(characteristic);
    }

    @Override
    public void write(UUID service, UUID characteristic, byte[] value, boolean withResponse) {
        Write w = new Write(service, characteristic, value.clone(), withResponse);
        writes.add(w);
        onWrite.accept(w);
    }

    @Override
    public void disconnect() {
        if (!connected) return;
        connected = false;
        if (listener != null) listener.onDisconnected();
    }

    /** Simula um valor chegando da balança. Ignorado se não houver inscrição. */
    public void inject(UUID characteristic, byte[] value) {
        if (!connected) throw new IllegalStateException("não conectado");
        if (!subscribed.contains(characteristic)) return;
        if (listener != null) listener.onNotification(characteristic, value.clone());
    }

    public boolean isSubscribed(UUID characteristic) {
        return subscribed.contains(characteristic);
    }

    public List<Write> writes() {
        return Collections.unmodifiableList(writes);
    }

    public String deviceName() {
        return deviceName;
    }
}
