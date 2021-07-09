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

class BaseOperationUsageTest {
	final BaseOperationUsage subject = new BaseOperationUsage();

//	@Test
//	void baseHbarTransferAsExpected() {
//		var cryptoOpsUsageMock = mock(CryptoOpsUsage.class);
//		subject.CRYPTO_OPS_USAGE = cryptoOpsUsageMock;
//		final var expectedTxnUsageMeta = new BaseTransactionMeta(0, 2);
//		final var expectedXferUsageMeta = new CryptoTransferMeta(380, 0,
//				0, 0);
//		subject.baseUsageFor(CryptoTransfer, SubType.DEFAULT);
//
//		verify(cryptoOpsUsageMock).cryptoTransferUsage(any(), expectedXferUsageMeta, expectedTxnUsageMeta, any());
//	}
//
//	@Test
//	void htsCryptoTransfer() {
//		subject.baseUsageFor(CryptoTransfer, SubType.TOKEN_FUNGIBLE_COMMON);
//
//		verify(subject).hbarCryptoTransfer();
//	}
//
//	@Test
//	void baseNftTransferAsExpected() {
//		subject.baseUsageFor(CryptoTransfer, SubType.TOKEN_NON_FUNGIBLE_UNIQUE);
//
//		verify(subject).nftCryptoTransfer();
//	}
}
