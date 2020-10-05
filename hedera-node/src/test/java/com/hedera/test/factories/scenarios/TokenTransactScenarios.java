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

import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TokenTransactFactory.newSignedTokenTransact;

public enum TokenTransactScenarios implements TxnHandlingScenario {
	TOKEN_TRANSACT_WITH_EXTANT_SENDERS {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenTransact()
							.adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
							.adjusting(SECOND_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
							.adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_SPECIAL_KEYS, +2_000)
							.nonPayerKts(FIRST_TOKEN_SENDER_KT, SECOND_TOKEN_SENDER_KT)
							.get()
			));
		}
	},
	TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenTransact()
							.adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
							.adjusting(SECOND_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
							.adjusting(RECEIVER_SIG, KNOWN_TOKEN_NO_SPECIAL_KEYS, +2_000)
							.nonPayerKts(
									FIRST_TOKEN_SENDER_KT,
									SECOND_TOKEN_SENDER_KT,
									RECEIVER_SIG_KT)
							.get()
			));
		}
	},
	TOKEN_TRANSACT_WITH_MISSING_SENDERS {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenTransact()
							.adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
							.adjusting(MISSING_ACCOUNT, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
							.adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_SPECIAL_KEYS, +2_000)
							.nonPayerKts(FIRST_TOKEN_SENDER_KT, SECOND_TOKEN_SENDER_KT)
							.get()
			));
		}
	},
}
