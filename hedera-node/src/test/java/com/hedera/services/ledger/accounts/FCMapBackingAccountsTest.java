package com.hedera.services.ledger.accounts;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class FCMapBackingAccountsTest {
	private final AccountID a = IdUtils.asAccount("0.0.2");
	private final MerkleEntityId aKey = MerkleEntityId.fromAccountId(a);
	private final AccountID b = IdUtils.asAccount("0.0.3");
	private final MerkleEntityId bKey = MerkleEntityId.fromAccountId(b);
	private final AccountID c = IdUtils.asAccount("0.0.4");
	private final MerkleEntityId cKey = MerkleEntityId.fromAccountId(c);

	private final MerkleAccount aAccount = MerkleAccountFactory.newAccount().balance(Long.MAX_VALUE).get();
	private final MerkleAccount cAccount = MerkleAccountFactory.newAccount().balance(Long.MAX_VALUE).get();

	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private FCMapBackingAccounts subject;

	@BeforeEach
	void setUp() {
		given(accounts.keySet()).willReturn(Set.of(aKey, bKey));

		subject = new FCMapBackingAccounts(() -> accounts);
	}

	@Test
	void usesGetWhenFetchingUnsafeRef() {
		given(accounts.get(aKey)).willReturn(aAccount);

		// then:
		Assertions.assertSame(aAccount, subject.getUnsafeRef(a));
	}

	@Test
	void usesCachedG4mWhenFetchingRef() {
		given(accounts.getForModify(aKey)).willReturn(aAccount);

		// when:
		final var firstA = subject.getRef(a);
		final var secondA = subject.getRef(a);

		// then:
		Assertions.assertSame(firstA, secondA);
		Assertions.assertSame(secondA, aAccount);
		verify(accounts, times(1)).getForModify(aKey);
	}

	@Test
	void putsIfNotAlreadyContained() {
		// when:
		subject.put(c, cAccount);

		// then:
		verify(accounts).put(cKey, cAccount);
		Assertions.assertEquals(Set.of(a, b, c), subject.idSet());
	}

	@Test
	void doesntPutIfAlreadyContained() {
		// when:
		subject.put(a, aAccount);

		// then:
		verify(accounts, never()).put(aKey, aAccount);
	}

	@Test
	void usesIdSetForContains() {
		// then:
		Assertions.assertTrue(subject.contains(a));
	}

	@Test
	void delegatesRemove() {
		// when:
		subject.remove(a);

		// then:
		verify(accounts).remove(aKey);
		// and:
		Assertions.assertEquals(Set.of(b), subject.idSet());
	}

	@Test
	void createsKeySet() {
		// expect:
		Assertions.assertEquals(Set.of(a, b), subject.idSet());
	}
}