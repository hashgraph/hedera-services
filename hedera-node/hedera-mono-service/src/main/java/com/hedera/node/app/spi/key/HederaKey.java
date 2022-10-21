package com.hedera.node.app.spi.key;

import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;

import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

/**
 * Placeholder implementation for moving JKey
 */
public interface HederaKey {
	static Optional<HederaKey> asHederaKey(final Key key) {
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
