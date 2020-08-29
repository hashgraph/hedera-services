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

import static com.hedera.test.factories.txns.TokenCreateFactory.*;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

public enum TokenCreateScenarios implements TxnHandlingScenario {
	TOKEN_CREATE_WITH_ADMIN_ONLY {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.nonPayerKts(TOKEN_ADMIN_KT)
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_ADMIN_AND_FREEZE {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate().frozen()
							.nonPayerKts(TOKEN_ADMIN_KT, TOKEN_FREEZE_KT)
							.get()
			));
		}
	},
	TOKEN_CREATE_MISSING_ADMIN {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate().missingAdmin().get()
			));
		}
	},
}
