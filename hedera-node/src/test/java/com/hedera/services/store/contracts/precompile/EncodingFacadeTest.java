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

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFacadeTest {
	private final EncodingFacade subject = new EncodingFacade();

	private static final Bytes RETURN_FUNGIBLE_MINT_FOR_10_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS = Bytes.fromHexString(
			"0x000000000000000000000000000000000000000000000000000000000000001600000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002");
	private static final Bytes RETURN_BURN_FOR_49_TOKENS = Bytes.fromHexString(
			"0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000031");

	@Test
	void decodeReturnResultForFungibleMint() {
		final var decodedResult = subject.getMintSuccessfulResultFromReceipt(10, null);
		assertEquals(RETURN_FUNGIBLE_MINT_FOR_10_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForNonFungibleMint() {
		final var decodedResult = subject.getMintSuccessfulResultFromReceipt(2, new long[]{1,2});
		assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForBurn() {
		final var decodedResult = subject.getBurnSuccessfulResultFromReceipt(49);
		assertEquals(RETURN_BURN_FOR_49_TOKENS, decodedResult);
	}
}

