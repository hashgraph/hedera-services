package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class BackedAccountLookupTest {
	AccountID a = IdUtils.asAccount("0.0.75231");
	MerkleAccount aAccount;
	JKey aKey;

	FCMapBackingAccounts accounts;

	BackedAccountLookup subject;

	@BeforeEach
	public void setup() throws Exception {
		aKey = TxnHandlingScenario.MISC_TOPIC_ADMIN_KT.asJKey();
		accounts = mock(FCMapBackingAccounts.class);

		aAccount = mock(MerkleAccount.class);
		given(aAccount.getKey()).willReturn(aKey);
		given(aAccount.isReceiverSigRequired()).willReturn(true);

		subject = new BackedAccountLookup(accounts);
	}

	@Test
	public void throwsOnMissing() {
		given(accounts.contains(a)).willReturn(false);

		// expect:
		assertThrows(
				InvalidAccountIDException.class,
				() -> subject.lookup(a));
	}

	@Test
	public void returnsIfAvailable() throws Exception {
		given(accounts.contains(a)).willReturn(true);
		given(accounts.getRef(a)).willReturn(aAccount);

		// when:
		var meta = subject.lookup(a);

		// then:
		assertEquals(aKey, meta.getKey());
		assertTrue(meta.isReceiverSigRequired());
	}
}