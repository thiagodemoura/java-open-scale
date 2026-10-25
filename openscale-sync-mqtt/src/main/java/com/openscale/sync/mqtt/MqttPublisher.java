package com.openscale.sync.mqtt;

/** Abstração mínima do cliente MQTT, para poder testar sem broker. */
public interface MqttPublisher extends AutoCloseable {

    void publish(String topic, byte[] payload, int qos, boolean retain) throws Exception;

    @Override
    void close();
}
