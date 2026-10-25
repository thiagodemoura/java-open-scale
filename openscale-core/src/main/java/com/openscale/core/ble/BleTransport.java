package com.openscale.core.ble;

import java.util.UUID;

/**
 * Transporte BLE GATT abstrato. O núcleo não depende de nenhuma pilha Bluetooth:
 * a implementação real fica a cargo de quem usa a biblioteca, por exemplo
 * <ul>
 *   <li>Android: {@code BluetoothGatt} (ver o RelaxmedicScale.java da versão anterior);</li>
 *   <li>Linux: BlueZ via D-Bus (biblioteca {@code com.github.hypfvieh:bluez-dbus});</li>
 *   <li>Testes e demonstração: {@link SimulatedBleTransport}.</li>
 * </ul>
 *
 * <p><b>Contrato:</b> a implementação deve <b>serializar</b> as operações GATT
 * (uma pendente por vez), na ordem em que foram pedidas. Os handlers chamam
 * {@link #enableNotifications} e {@link #write} em sequência sem esperar.
 */
public interface BleTransport extends AutoCloseable {

    /** Callbacks do transporte para a sessão. */
    interface Listener {
        /** Conexão estabelecida e serviços descobertos. */
        void onConnected(String deviceName);

        /** Valor recebido por notify ou indicate. */
        void onNotification(UUID characteristic, byte[] value);

        void onDisconnected();

        default void onError(String message, Throwable cause) {
        }
    }

    void setListener(Listener listener);

    /** Inicia a conexão com o dispositivo; o resultado chega em {@link Listener#onConnected}. */
    void connect();

    /** Liga notify ou indicate (o que a característica suportar) via CCCD 0x2902. */
    void enableNotifications(UUID service, UUID characteristic);

    void write(UUID service, UUID characteristic, byte[] value, boolean withResponse);

    void disconnect();

    @Override
    default void close() {
        disconnect();
    }
}
