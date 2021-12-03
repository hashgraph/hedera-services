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

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
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

	public SyntheticTxnFactory.NftBurn decodeBurn(final Bytes input) {
		final var tokenAddress = Address.wrap(
				input.slice(ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH, ADDRESS_BYTES_LENGTH)
		);

		return new SyntheticTxnFactory.NftBurn(tokenParsedFromSolidityAddress(tokenAddress), List.of(1L));
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

	public SyntheticTxnFactory.AssociateToken decodeAssociate(final Bytes input) {
		Address accountAddress = Address.wrap(input.slice(16, 20));
		Address tokenAddress = Address.wrap(input.slice(48, 20));

		return new SyntheticTxnFactory.AssociateToken(
				accountParsedFromSolidityAddress(accountAddress),
				tokenParsedFromSolidityAddress(tokenAddress));
	}

	public SyntheticTxnFactory.DissociateToken decodeDissociate(final Bytes input) {
		Address accountAddress = Address.wrap(input.slice(16, 20));
		Address tokenAddress = Address.wrap(input.slice(48, 20));

		return new SyntheticTxnFactory.DissociateToken(
				accountParsedFromSolidityAddress(accountAddress),
				tokenParsedFromSolidityAddress(tokenAddress));
	}
}
