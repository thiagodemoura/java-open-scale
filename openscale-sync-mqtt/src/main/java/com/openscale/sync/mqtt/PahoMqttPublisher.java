package com.openscale.sync.mqtt;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import java.nio.charset.StandardCharsets;

/** Cliente MQTT 5 (Eclipse Paho), conectado sob demanda e reconectado se cair. */
public final class PahoMqttPublisher implements MqttPublisher {

    private final String serverUri;
    private final String clientId;
    private final String username;
    private final String password;
    private MqttClient client;

    public PahoMqttPublisher(String serverUri, String clientId, String username, String password) {
        this.serverUri = serverUri;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
    }

    private synchronized MqttClient connected() throws MqttException {
        if (client == null) {
            client = new MqttClient(serverUri, clientId, new MemoryPersistence());
        }
        if (!client.isConnected()) {
            MqttConnectionOptions opts = new MqttConnectionOptions();
            opts.setCleanStart(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(10);
            if (username != null) opts.setUserName(username);
            if (password != null) opts.setPassword(password.getBytes(StandardCharsets.UTF_8));
            client.connect(opts);
        }
        return client;
    }

    @Override
    public void publish(String topic, byte[] payload, int qos, boolean retain) throws Exception {
        MqttMessage msg = new MqttMessage(payload);
        msg.setQos(qos);
        msg.setRetained(retain);
        connected().publish(topic, msg);
    }

    @Override
    public synchronized void close() {
        if (client == null) return;
        try {
            if (client.isConnected()) client.disconnect();
            client.close();
        } catch (MqttException ignored) {
            // nada a fazer
        }
        client = null;
    }
}
