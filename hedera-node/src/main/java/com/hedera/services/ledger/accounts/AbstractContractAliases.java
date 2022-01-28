package com.hedera.services.ledger.accounts;

import com.google.common.primitives.Longs;
import org.hyperledger.besu.datatypes.Address;

import java.util.Arrays;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

public abstract class AbstractContractAliases implements ContractAliases {
	/* A placeholder to store the 12-byte prefix (4-byte shard and 8-byte realm) that marks an EVM
	 * address as a "mirror" address that follows immediately from a <shard>.<realm>.<num> id. */
	private static byte[] mirrorPrefix = null;

	protected boolean isMirror(final Address address) {
		return isMirror(address.toArrayUnsafe());
	}

	protected boolean isMirror(final byte[] address) {
		if (mirrorPrefix == null) {
			mirrorPrefix = new byte[12];
			System.arraycopy(Longs.toByteArray(STATIC_PROPERTIES.getShard()), 4, mirrorPrefix, 0, 4);
			System.arraycopy(Longs.toByteArray(STATIC_PROPERTIES.getRealm()), 0, mirrorPrefix, 4, 8);
		}
		return Arrays.equals(mirrorPrefix, 0, 12, address, 0, 12);
	}
}
