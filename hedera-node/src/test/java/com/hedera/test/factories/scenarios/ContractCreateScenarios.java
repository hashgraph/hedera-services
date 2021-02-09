package com.hedera.test.factories.scenarios;

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

import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.test.factories.txns.ContractCreateFactory.*;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

public enum ContractCreateScenarios implements TxnHandlingScenario {
	CONTRACT_CREATE_WITH_ADMIN_KEY {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedContractCreate().useAdminKey(true).get()
			));
		}
	},
	CONTRACT_CREATE_NO_ADMIN_KEY {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedContractCreate().useAdminKey(false).get()
			));
		}
	},
	CONTRACT_CREATE_DEPRECATED_CID_ADMIN_KEY {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedContractCreate().useDeprecatedAdminKey(true).get()
			));
		}
	}
}
