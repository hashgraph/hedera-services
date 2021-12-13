package com.hedera.services.store.contracts.precompile;

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

import com.hedera.test.factories.keys.KeyFactory;
import org.junit.jupiter.api.Test;

import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticTxnFactoryTest {
	private final SyntheticTxnFactory subject = new SyntheticTxnFactory();
	@Test
	void createsExpectedCryptoCreate() {
		final var balance = 10L;
		final var alias = KeyFactory.getDefaultInstance().newEd25519();
		final var result = subject.cryptoCreate(alias, balance);
		final var txnBody = result.build();

		assertTrue(txnBody.hasCryptoCreateAccount());
		assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
		assertEquals(THREE_MONTHS_IN_SECONDS,
				txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
		assertEquals(10L,
				txnBody.getCryptoCreateAccount().getInitialBalance());
		assertEquals(alias.toByteString(),
				txnBody.getCryptoCreateAccount().getKey().toByteString());
	}
}
