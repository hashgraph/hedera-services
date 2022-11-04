package com.hedera.node.app;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;

import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

// This class should not exist. Right now we have code that needs to map from a JKey to a
// HederaKey, but that should be an implementation detail handled somewhere in the token
// service. It shouldn't be on HederaKey (which shouldn't know anything about JKey).
// So some utilities exist here that we can use for now, but these need to be removed.
// They're just scaffolding while we refactor.
public class Utils {
    // This method shouldn't be here. It needs to find a new home.
    public static Optional<HederaKey> asHederaKey(final Key key) {
        try {
            // TODO: Need to move JKey after refactoring, adding equals & hashcode into this package
            final var fcKey = mapKey(key);
            if (!fcKey.isValid()) {
                return Optional.empty();
            }
            return Optional.of(fcKey);
        } catch (DecoderException ignore) {
            return Optional.empty();
        }
    }
}
