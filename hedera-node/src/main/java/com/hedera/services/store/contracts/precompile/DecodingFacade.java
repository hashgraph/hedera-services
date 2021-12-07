package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.primitives.Longs;
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
	private static final int LONG_LENGTH = 8;
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

	@Inject
	public DecodingFacade() {
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.BurnWrapper decodeBurn(final Bytes input) {
		final var tokenAddress = Address.wrap(input.slice(
				ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH,
				ADDRESS_BYTES_LENGTH));
		final var fungibleAmount = Longs.fromByteArray(input.slice(
				ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH + ADDRESS_BYTES_LENGTH,
				LONG_LENGTH).toArrayUnsafe());

		if (fungibleAmount > 0) {
			return SyntheticTxnFactory.BurnWrapper.forFungible(
					tokenParsedFromSolidityAddress(tokenAddress), fungibleAmount);
		} else {
			return SyntheticTxnFactory.BurnWrapper.forNonFungible(
					tokenParsedFromSolidityAddress(tokenAddress), List.of(1L));
		}
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.MintWrapper decodeMint(final Bytes input) {
		final var tokenAddress = Address.wrap(input.slice(
						ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH,
						ADDRESS_BYTES_LENGTH));
		final var fungibleAmount = Longs.fromByteArray(input.slice(
						ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH + ADDRESS_BYTES_LENGTH + 24,
						LONG_LENGTH).toArrayUnsafe());

		if (fungibleAmount > 0) {
			return SyntheticTxnFactory.MintWrapper.forFungible(
					tokenParsedFromSolidityAddress(tokenAddress), fungibleAmount);
		} else {
			return SyntheticTxnFactory.MintWrapper.forNonFungible(
					tokenParsedFromSolidityAddress(tokenAddress), List.of(ByteString.copyFromUtf8("Hello World!")));
		}
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.Association decodeAssociation(final Bytes input) {
		Address accountAddress = Address.wrap(input.slice(16, 20));
		Address tokenAddress = Address.wrap(input.slice(48, 20));

		return SyntheticTxnFactory.Association.singleAssociation(
				accountParsedFromSolidityAddress(accountAddress),
				tokenParsedFromSolidityAddress(tokenAddress));
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.Association decodeMultipleAssociations(final Bytes input) {
		Address accountAddress = Address.wrap(input.slice(16, 20));
		Address tokenAddress = Address.wrap(input.slice(48, 20));

		return SyntheticTxnFactory.Association.multiAssociation(
				accountParsedFromSolidityAddress(accountAddress),
				List.of(tokenParsedFromSolidityAddress(tokenAddress)));
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.Dissociation decodeDissociate(final Bytes input) {
		Address accountAddress = Address.wrap(input.slice(16, 20));
		Address tokenAddress = Address.wrap(input.slice(48, 20));

		return SyntheticTxnFactory.Dissociation.singleDissociation(
				accountParsedFromSolidityAddress(accountAddress),
				tokenParsedFromSolidityAddress(tokenAddress));
	}

	/* NOT A REAL IMPLEMENTATION */
	public SyntheticTxnFactory.Dissociation decodeMultipleDissociations(final Bytes input) {
		Address accountAddress = Address.wrap(input.slice(16, 20));
		Address tokenAddress = Address.wrap(input.slice(48, 20));

		return SyntheticTxnFactory.Dissociation.multiDissociation(
				accountParsedFromSolidityAddress(accountAddress),
				List.of(tokenParsedFromSolidityAddress(tokenAddress)));
	}

	/* NOT A REAL IMPLEMENTATION */
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
