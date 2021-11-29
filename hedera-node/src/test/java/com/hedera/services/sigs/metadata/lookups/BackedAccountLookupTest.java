package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BackedAccountLookupTest {
	private final AccountID id = IdUtils.asAccount("1.2.3");
	private final MerkleAccount account = MerkleAccountFactory.newAccount()
			.receiverSigRequired(true)
			.accountKeys(TxnHandlingScenario.MISC_ADMIN_KT.asJKeyUnchecked())
			.get();

	@Mock
	private BackingStore<AccountID, MerkleAccount> accounts;

	private BackedAccountLookup subject;

	@BeforeEach
	void setUp() {
		subject = new BackedAccountLookup(accounts);
	}

	@Test
	void usesRefForImpureLookup() {
		given(accounts.contains(id)).willReturn(true);
		given(accounts.getImmutableRef(id)).willReturn(account);

		// when:
		final var result = subject.safeLookup(id);

		// then:
		assertTrue(result.metadata().isReceiverSigRequired());
		assertSame(account.getAccountKey(), result.metadata().getKey());
	}

	@Test
	void asserSafelookupToString() {
		given(accounts.contains(id)).willReturn(true);
		given(accounts.getImmutableRef(id)).willReturn(account);

		// when:
		final var result = subject.safeLookup(id);

		// then:
		assertEquals("SafeLookupResult{failure=NONE}", result.toString());
	}

	@Test
	void assertFailledSafeLookup() {
		given(accounts.contains(id)).willReturn(false);

		// when:
		final var result = subject.safeLookup(id);

		// then:
		assertFalse(result.succeeded());
	}
}
