package com.hedera.node.app.keys;

import com.hedera.node.app.spi.keys.HederaKey;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

/**
 * Utility class for {@link HederaKey}.
 */
public class HederaKeys {
	private HederaKeys(){
		throw new UnsupportedOperationException("Utility Class");
	}

	public static Key toProto(final HederaKey key){
		throw new NotImplementedException();
	}

	public static HederaKey fromProto(final Key key, int depth) {
		throw new NotImplementedException();
	}

	public static Optional<HederaKey> asHederaKey(final Key key) {
		try {
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
