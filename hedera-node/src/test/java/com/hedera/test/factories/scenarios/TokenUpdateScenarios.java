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
import static com.hedera.test.factories.txns.TokenUpdateFactory.newSignedTokenUpdate;
import static com.hedera.test.utils.IdUtils.asIdRef;

public enum TokenUpdateScenarios implements TxnHandlingScenario {
	UPDATE_WITH_NO_KEYS_AFFECTED {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_NO_SPECIAL_KEYS))
							.get()
			));
		}
	},
	UPDATE_REPLACING_ADMIN_KEY {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_NO_SPECIAL_KEYS))
							.newAdmin(TOKEN_REPLACE_KT)
							.get()
			));
		}
	},
	UPDATE_WITH_SUPPLY_KEYED_TOKEN {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_WITH_SUPPLY))
							.replacingSupply()
							.get()
			));
		}
	},
	UPDATE_WITH_KYC_KEYED_TOKEN {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_WITH_KYC))
							.replacingKyc()
							.get()
			));
		}
	},
	UPDATE_WITH_FREEZE_KEYED_TOKEN {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_WITH_FREEZE))
							.replacingFreeze()
							.get()
			));
		}
	},
	UPDATE_WITH_WIPE_KEYED_TOKEN {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_WITH_WIPE))
							.replacingWipe()
							.get()
			));
		}
	},
	UPDATE_WITH_MISSING_TOKEN {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(UNKNOWN_TOKEN))
							.get()
			));
		}
	},
	UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenUpdate()
							.updating(asIdRef(KNOWN_TOKEN_IMMUTABLE))
							.get()
			));
		}
	}
}
