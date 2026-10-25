package com.openscale.core.scale;

import com.openscale.core.ble.BleTransport;
import com.openscale.core.model.ScaleMeasurement;
import com.openscale.core.model.ScaleUser;
import com.openscale.core.sync.SyncDispatcher;
import com.openscale.core.sync.SyncResult;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Liga um transporte BLE a um handler de balança e, opcionalmente, aos alvos
 * de sincronização. É o ponto de entrada típico:
 *
 * <pre>{@code
 * ScaleSession session = new ScaleSession(transport, provider, user, listener)
 *         .withSync(dispatcher);
 * session.start();
 * }</pre>
 */
public final class ScaleSession implements AutoCloseable {

    /** Eventos da sessão para a interface do usuário. */
    public interface Listener {
        default void onInfo(String message) {
        }

        default void onLiveWeight(double kg) {
        }

        void onMeasurement(ScaleMeasurement measurement);

        default void onSynced(ScaleMeasurement measurement, List<SyncResult> results) {
        }

        default void onDisconnected() {
        }

        default void onError(String message, Throwable cause) {
        }

        default void onDebug(String message) {
        }
    }

    private final BleTransport transport;
    private final ScaleHandlerProvider provider;
    private final ScaleHandler handler;
    private final ScaleUser user;
    private final Listener listener;
    private SyncDispatcher dispatcher;

    public ScaleSession(BleTransport transport, ScaleHandlerProvider provider, ScaleUser user, Listener listener) {
        this.transport = Objects.requireNonNull(transport);
        this.provider = Objects.requireNonNull(provider);
        this.user = Objects.requireNonNull(user);
        this.listener = Objects.requireNonNull(listener);
        this.handler = provider.create();
        this.handler.bind(new Context());
    }

    /** Envia cada medição publicada para estes alvos. */
    public ScaleSession withSync(SyncDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        return this;
    }

    public void start() {
        transport.setListener(new BleTransport.Listener() {
            @Override
            public void onConnected(String deviceName) {
                listener.onInfo("Conectado: " + deviceName + " (" + provider.displayName() + ")");
                handler.onConnected(user);
            }

            @Override
            public void onNotification(UUID characteristic, byte[] value) {
                try {
                    handler.onNotification(characteristic, value, user);
                } catch (RuntimeException e) {
                    listener.onError("Erro ao decodificar frame", e);
                }
            }

            @Override
            public void onDisconnected() {
                handler.onDisconnected();
                listener.onDisconnected();
            }

            @Override
            public void onError(String message, Throwable cause) {
                listener.onError(message, cause);
            }
        });
        transport.connect();
    }

    public ScaleUser user() {
        return user;
    }

    @Override
    public void close() {
        transport.disconnect();
    }

    private final class Context implements ScaleContext {
        @Override
        public void enableNotifications(UUID service, UUID characteristic) {
            transport.enableNotifications(service, characteristic);
        }

        @Override
        public void write(UUID service, UUID characteristic, byte[] value, boolean withResponse) {
            transport.write(service, characteristic, value, withResponse);
        }

        @Override
        public void publish(ScaleMeasurement measurement) {
            ScaleMeasurement m = measurement.withBmi(user);
            listener.onMeasurement(m);
            if (dispatcher != null) {
                listener.onSynced(m, dispatcher.dispatch(m, user));
            }
        }

        @Override
        public void liveWeight(double kg) {
            listener.onLiveWeight(kg);
        }

        @Override
        public void info(String message) {
            listener.onInfo(message);
        }

        @Override
        public void debug(String message) {
            listener.onDebug(message);
        }
    }
}
