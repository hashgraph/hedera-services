package com.hedera.services.pricing;

/*-
 * ‌
 * Hedera Services API Fees
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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.UNRECOGNIZED;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseOperationUsageTest {
	@Test
	void picksAppropriateOperation() {
		BaseOperationUsage mock = Mockito.spy(new BaseOperationUsage());

		mock.baseUsageFor(CryptoTransfer, DEFAULT);
		Mockito.verify(mock).hbarCryptoTransfer();

		mock.baseUsageFor(CryptoTransfer, TOKEN_FUNGIBLE_COMMON);
		Mockito.verify(mock).htsCryptoTransfer();

		mock.baseUsageFor(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES);
		Mockito.verify(mock).htsCryptoTransferWithCustomFee();

		mock.baseUsageFor(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE);
		Mockito.verify(mock).nftCryptoTransfer();

		mock.baseUsageFor(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES);
		Mockito.verify(mock).nftCryptoTransferWithCustomFee();

		mock.baseUsageFor(TokenAccountWipe, TOKEN_NON_FUNGIBLE_UNIQUE);
		Mockito.verify(mock).uniqueTokenWipe();

		mock.baseUsageFor(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE);
		Mockito.verify(mock).uniqueTokenBurn();

		mock.baseUsageFor(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);
		Mockito.verify(mock).uniqueTokenMint();

		mock.baseUsageFor(ConsensusSubmitMessage, DEFAULT);
		Mockito.verify(mock).submitMessage();

		mock.baseUsageFor(TokenFeeScheduleUpdate, DEFAULT);
		Mockito.verify(mock).feeScheduleUpdate();

		assertThrows(IllegalArgumentException.class,
				() -> mock.baseUsageFor(CryptoUpdate, DEFAULT));
		assertThrows(IllegalArgumentException.class,
				() -> mock.baseUsageFor(CryptoTransfer, UNRECOGNIZED));
		assertThrows(IllegalArgumentException.class,
				() -> mock.baseUsageFor(TokenMint, TOKEN_FUNGIBLE_COMMON));
		assertThrows(IllegalArgumentException.class,
				() -> mock.baseUsageFor(TokenAccountWipe, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES));
		assertThrows(IllegalArgumentException.class,
				() -> mock.baseUsageFor(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES));
	}
}
