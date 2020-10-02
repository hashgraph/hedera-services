package com.hedera.test.factories.scenarios;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.test.factories.txns.CryptoTransferFactory.*;
import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.test.factories.txns.CryptoCreateFactory.*;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TinyBarsFromTo.*;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE_ID;;


public enum CryptoTransferScenarios implements TxnHandlingScenario {
	CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoTransfer()
							.nonPayerKts(DEFAULT_PAYER_KT)
							.transfers(tinyBarsFromTo(DEFAULT_PAYER_ID, NO_RECEIVER_SIG_ID, 1_000L))
							.get()
			));
		}
	},
	CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoTransfer()
							.nonPayerKts(RECEIVER_SIG_KT)
							.transfers(tinyBarsFromTo(DEFAULT_PAYER_ID, RECEIVER_SIG_ID, 1_000L))
							.get()
			));
		}
	},
	CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoTransfer()
							.nonPayerKts(RECEIVER_SIG_KT)
							.transfers(tinyBarsFromTo(DEFAULT_PAYER_ID, MISSING_ACCOUNT_ID, 1_000L))
							.get()
			));
		}
	},
	VALID_QUERY_PAYMENT_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoTransfer()
							.nonPayerKts(MISC_ACCOUNT_KT, RECEIVER_SIG_KT)
							.transfers(
									tinyBarsFromTo(DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L),
									tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L)
							).get()
			));
		}
	},
	QUERY_PAYMENT_MISSING_SIGS_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoTransfer()
							.nonPayerKts(MISC_ACCOUNT_KT)
							.transfers(
									tinyBarsFromTo(DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L),
									tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L)
							).get()
			));
		}
	},
	QUERY_PAYMENT_INVALID_SENDER_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoTransfer()
							.nonPayerKts(MISC_ACCOUNT_KT)
							.transfers(
									tinyBarsFromTo(DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L),
									tinyBarsFromTo(MISSING_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L)
							).get()
			));
		}
	}
}
