package com.hedera.services.store.contracts.precompile;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static com.hedera.services.utils.EntityIdUtils.tokenParsedFromSolidityAddress;

@Singleton
public class DecodingFacade {
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

	@Inject
	public DecodingFacade() {
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.NftMint decodeMint(final Bytes input) {
		final var tokenAddress = Address.wrap(
				input.slice(ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH, ADDRESS_BYTES_LENGTH)
		);

		return new SyntheticTxnFactory.NftMint(
				tokenParsedFromSolidityAddress(tokenAddress),
				List.of(ByteString.copyFromUtf8("Hello World!")));
	}
}
