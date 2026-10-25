package com.openscale.core.ble;

import java.util.UUID;

public final class BleUuids {

    public static final UUID CCCD = uuid16(0x2902);

    private BleUuids() {
    }

    /** Converte um UUID curto de 16 bits para o UUID base do Bluetooth SIG. */
    public static UUID uuid16(int shortUuid) {
        return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortUuid & 0xFFFF));
    }
}
