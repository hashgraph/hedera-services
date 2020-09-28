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

public enum SystemDeleteScenarios implements TxnHandlingScenario {
	SYSTEM_DELETE_FILE_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete().file(MISC_FILE_ID).get()
			));
		}
	},
	FULL_PAYER_SIGS_VIA_MAP_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.payer(DILIGENT_SIGNING_PAYER_ID)
							.payerKt(DILIGENT_SIGNING_PAYER_KT)
							.nonPayerKts(MISC_FILE_WACL_KT)
							.file(MISC_FILE_ID).get()
			));
		}
	},
	FULL_PAYER_SIGS_VIA_LIST_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payer(DILIGENT_SIGNING_PAYER_ID)
							.payerKt(DILIGENT_SIGNING_PAYER_KT)
							.nonPayerKts(MISC_FILE_WACL_KT)
							.file(MISC_FILE_ID).get()
			));
		}
	},
	MISSING_PAYER_SIGS_VIA_MAP_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.payer(TOKEN_TREASURY_ID)
							.payerKt(TOKEN_TREASURY_KT)
							.nonPayerKts(MISC_FILE_WACL_KT)
							.file(MISC_FILE_ID).get()
			));
		}
	},
	MISSING_PAYER_SIGS_VIA_LIST_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.useSigList()
							.payer(TOKEN_TREASURY_ID)
							.payerKt(TOKEN_TREASURY_KT)
							.nonPayerKts(MISC_FILE_WACL_KT)
							.file(MISC_FILE_ID).get()
			));
		}
	},
	INVALID_PAYER_SIGS_VIA_MAP_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			SigMapGenerator buggySigMapGen = SigMapGenerator.withUniquePrefixes();
			buggySigMapGen.setInvalidEntries(Set.of(1));

			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.fee(1_234L)
							.sigMapGen(buggySigMapGen)
							.payer(DILIGENT_SIGNING_PAYER_ID)
							.payerKt(DILIGENT_SIGNING_PAYER_KT)
							.nonPayerKts(MISC_FILE_WACL_KT)
							.file(MISC_FILE_ID).get()
			));
		}
	},
	AMBIGUOUS_SIG_MAP_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			SigMapGenerator ambigSigMapGen = SigMapGenerator.withAmbiguousPrefixes();

			return new PlatformTxnAccessor(from(
					newSignedSystemDelete()
							.fee(1_234L)
							.keyFactory(overlapFactory)
							.sigMapGen(ambigSigMapGen)
							.payer(FROM_OVERLAP_PAYER_ID)
							.payerKt(FROM_OVERLAP_PAYER_KT)
							.nonPayerKts(MISC_FILE_WACL_KT)
							.file(MISC_FILE_ID).get()
			));
		}
	}
}
