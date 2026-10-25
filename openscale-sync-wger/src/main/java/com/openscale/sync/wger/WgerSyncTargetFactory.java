package com.openscale.sync.wger;

import com.openscale.core.sync.SyncConfig;
import com.openscale.core.sync.SyncTarget;
import com.openscale.core.sync.SyncTargetFactory;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * <pre>
 * sync.wger.enabled=true
 * sync.wger.baseUrl=https://wger.de
 * sync.wger.token=SUA_CHAVE_DA_API
 * sync.wger.authScheme=Token        (ou Bearer para JWT)
 * sync.wger.dateMode=AUTO           (AUTO, DATETIME ou DATE)
 * sync.wger.zone=America/Fortaleza
 * </pre>
 */
public final class WgerSyncTargetFactory implements SyncTargetFactory {

    @Override
    public String id() {
        return "wger";
    }

    @Override
    public Optional<SyncTarget> create(SyncConfig c) {
        return Optional.of(new WgerSyncTarget(
                c.get(id(), "baseUrl", "https://wger.de"),
                c.require(id(), "token"),
                c.get(id(), "authScheme", "Token"),
                ZoneId.of(c.get(id(), "zone", ZoneId.systemDefault().getId())),
                WgerSyncTarget.DateMode.valueOf(c.get(id(), "dateMode", "AUTO").toUpperCase(Locale.ROOT))));
    }
}
