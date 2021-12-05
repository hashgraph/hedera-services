package com.hedera.services.store.contracts.precompile;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
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

	public SyntheticTxnFactory.FungibleTokenTransfer decodeTransferToken(final Bytes input) {
		final var tokenAddress = Address.wrap(input.slice(16, 20));
		final var fromAddress = Address.wrap(input.slice(48, 20));
		final var toAddress = Address.wrap(input.slice(80, 20));
		final BigInteger amount = input.slice(100, 32).toBigInteger();

		return new SyntheticTxnFactory.FungibleTokenTransfer(
				amount.longValue(),
				tokenParsedFromSolidityAddress(tokenAddress),
				accountParsedFromSolidityAddress(fromAddress),
				accountParsedFromSolidityAddress(toAddress));
	}
}
