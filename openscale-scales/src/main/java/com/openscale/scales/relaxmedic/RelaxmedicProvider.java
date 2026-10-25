package com.openscale.scales.relaxmedic;

import com.openscale.core.scale.Capability;
import com.openscale.core.scale.ScaleHandler;
import com.openscale.core.scale.ScaleHandlerProvider;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class RelaxmedicProvider implements ScaleHandlerProvider {

    @Override
    public String displayName() {
        return "Relaxmedic";
    }

    @Override
    public boolean supports(String advertisedName) {
        if (advertisedName == null) return false;
        String name = advertisedName.toLowerCase(Locale.ROOT);
        // Outras famílias 0xFFB0 (Swan/Icomon/YG) usam protocolos parecidos, mas não iguais.
        if (name.startsWith("swan") || name.equals("icomon") || name.equals("yg")) return false;
        return name.startsWith("relaxmedic");
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.LIVE_WEIGHT, Capability.USER_SYNC);
    }

    @Override
    public ScaleHandler create() {
        return new RelaxmedicHandler();
    }
}
