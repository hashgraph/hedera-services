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

import static com.hedera.test.factories.txns.CryptoCreateFactory.*;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

public enum CryptoCreateScenarios implements TxnHandlingScenario {
	CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoCreate().receiverSigRequired(false).get()
			));
		}
	},
	CRYPTO_CREATE_RECEIVER_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoCreate()
							.receiverSigRequired(true)
							.nonPayerKts(DEFAULT_ACCOUNT_KT)
							.get()
			));
		}
	},
	CRYPTO_CREATE_COMPLEX_PAYER_RECEIVER_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoCreate()
							.payer(COMPLEX_KEY_ACCOUNT_ID)
							.payerKt(COMPLEX_KEY_ACCOUNT_KT)
							.receiverSigRequired(true)
							.nonPayerKts(DEFAULT_ACCOUNT_KT)
							.get()
			));
		}
	}
}
