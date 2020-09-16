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
import com.hedera.test.factories.sigs.SigMapGenerator;

import java.util.Set;

import static com.hedera.test.factories.txns.SystemDeleteFactory.*;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

public enum PrecheckSigListFailScenarios implements TxnHandlingScenario {
	MISSING_SIG_LIST_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.skipPayerSig()
							.get()
			));
		}
	},
	SIMPLE_KEY_COMPLEX_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payer(MISC_ACCOUNT_ID)
							.get()
			));
		}
	},
	KEY_LIST_SIMPLE_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payerKt(MISC_ACCOUNT_KT)
							.get()
			));
		}
	},
	KEY_LIST_TOO_LONG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payer(RECEIVER_SIG_ID)
							.get()
			));
		}
	},
	THRESHOLD_KEY_SIMPLE_SIG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payer(DILIGENT_SIGNING_PAYER_ID)
							.get()
			));
		}
	},
	THRESHOLD_SIG_TOO_LONG_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payer(DILIGENT_SIGNING_PAYER_ID)
							.payerKt(LONG_THRESHOLD_KT)
							.get()
			));
		}
	}
}
