package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFacadeTest {
	private final EncodingFacade subject = new EncodingFacade();

	private static final Bytes RETURN_FUNGIBLE_MINT_FOR_10_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"00000a0000000000000000000000000000000000000000000000000000" +
					"0000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"0000020000000000000000000000000000000000000000000000000000" +
					"00000000006000000000000000000000000000000000000000000000000" +
					"00000000000000002000000000000000000000000000000000000000000" +
					"00000000000000000000010000000000000000000000000000000000000000000000000000000000000002");
	private static final Bytes RETURN_BURN_FOR_49_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016" +
					"0000000000000000000000000000000000000000000000000000000000000031");
	private static final Bytes MINT_FAILURE_FROM_INVALID_TOKEN_ID = Bytes.fromHexString(
			"0x00000000000000000000000000000000000000000000000000000000000000a7" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"0000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes BURN_FAILURE_FROM_TREASURY_NOT_OWNER = Bytes.fromHexString(
			"0x00000000000000000000000000000000000000000000000000000000000000fc" +
					"0000000000000000000000000000000000000000000000000000000000000000");

	@Test
	void decodeReturnResultForFungibleMint() {
		final var decodedResult = subject.encodeMintSuccess(10, null);
		assertEquals(RETURN_FUNGIBLE_MINT_FOR_10_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForNonFungibleMint() {
		final var decodedResult = subject.encodeMintSuccess(2, new long[] { 1, 2 });
		assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForBurn() {
		final var decodedResult = subject.encodeBurnSuccess(49);
		assertEquals(RETURN_BURN_FOR_49_TOKENS, decodedResult);
	}

	@Test
	void createsExpectedMintFailureResult() {
		assertEquals(MINT_FAILURE_FROM_INVALID_TOKEN_ID, subject.encodeMintFailure(INVALID_TOKEN_ID));
	}

	@Test
	void createsExpectedBurnFailureResult() {
		assertEquals(BURN_FAILURE_FROM_TREASURY_NOT_OWNER, subject.encodeBurnFailure(TREASURY_MUST_OWN_BURNED_NFT));
	}
}

