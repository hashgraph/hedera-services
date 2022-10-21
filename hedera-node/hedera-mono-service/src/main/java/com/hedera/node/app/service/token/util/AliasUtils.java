package com.hedera.node.app.service.token.util;

import com.google.common.primitives.Longs;
import java.util.Arrays;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.accounts.AbstractContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.services.utils.EntityIdUtils.numFromEvmAddress;

/**
 * Utility class needed for token service implementations
 */
public final class AliasUtils {
	public static final Long MISSING_NUM = 0L;

	private AliasUtils() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static boolean isMirror(final byte[] address) {
		byte[] mirrorPrefix = null;
		if (address.length != EVM_ADDRESS_LEN) {
			return false;
		}
		if (mirrorPrefix == null) {
			mirrorPrefix = new byte[12];
			System.arraycopy(
					Longs.toByteArray(STATIC_PROPERTIES.getShard()), 4, mirrorPrefix, 0, 4);
			System.arraycopy(
					Longs.toByteArray(STATIC_PROPERTIES.getRealm()), 0, mirrorPrefix, 4, 8);
		}
		return Arrays.equals(mirrorPrefix, 0, 12, address, 0, 12);
	}

	public static Long fromMirror(final byte[] evmAddress) {
		return numFromEvmAddress(evmAddress);
	}
}
