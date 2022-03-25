package com.hedera.services.state.expiry.renewal;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountGCTest {
	@Mock
	private AliasManager aliasManager;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private TreasuryReturnHelper treasuryReturnHelper;
	@Mock
	private BackingStore<AccountID, MerkleAccount> backingAccounts;

	private AccountGC subject;

	@BeforeEach
	void setUp() {
		subject = new AccountGC(aliasManager, sigImpactHistorian, treasuryReturnHelper, backingAccounts);
	}

	@Test
	void removalWithNoTokensWorks() {
		given(aliasManager.forgetAlias(accountNoTokens.getAlias())).willReturn(true);
		final var expectedReturns = new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

		final var actualReturns = subject.expireBestEffort(num, accountNoTokens);

		assertEquals(expectedReturns, actualReturns);
		assertRemovalStepsTaken(num, accountNoTokens);
	}

	@Test
	@SuppressWarnings("unchecked")
	void removalWithTokensWorks() {
		final var counter = new AtomicInteger();
		doAnswer(invocationOnMock -> {
			final var tokenTypes = (List<EntityId>) invocationOnMock.getArgument(2);
			final var no = counter.getAndIncrement();
			if (no == 0) {
				tokenTypes.add(aToken.toEntityId());
			} else if (no == 1) {
				tokenTypes.add(bToken.toEntityId());
			} else {
				tokenTypes.add(cToken.toEntityId());
			}
			return null;
		}).when(treasuryReturnHelper).updateReturns(eq(num.toGrpcAccountId()), any(), any(), any());

		final var expectedReturns = new TreasuryReturns(
				List.of(aToken.toEntityId(), bToken.toEntityId(), cToken.toEntityId()),
				Collections.emptyList(), true);

		final var actualReturns = subject.expireBestEffort(num, accountWithTokens);

		assertEquals(expectedReturns, actualReturns);
		assertRemovalStepsTaken(num, accountNoTokens);
	}

	private void assertRemovalStepsTaken(final EntityNum num, final MerkleAccount account) {
		verify(aliasManager).forgetAlias(account.getAlias());
		verify(backingAccounts).remove(num.toGrpcAccountId());
		verify(sigImpactHistorian).markEntityChanged(num.longValue());
	}

	private final long expiredNum = 2L;
	private final long aNum = 1234L;
	private final long bNum = 4321L;
	private final long cNum = 5678L;
	private final EntityNum num = EntityNum.fromLong(expiredNum);
	private final EntityNum aToken = EntityNum.fromLong(aNum);
	private final EntityNum bToken = EntityNum.fromLong(bNum);
	private final EntityNum cToken = EntityNum.fromLong(cNum);
	private final ByteString anAlias = ByteString.copyFromUtf8("bbbb");
	private final MerkleAccount accountNoTokens = MerkleAccountFactory.newAccount()
			.balance(0)
			.alias(anAlias)
			.get();
	private final MerkleAccount accountWithTokens = MerkleAccountFactory.newAccount()
			.balance(0)
			.alias(anAlias)
			.get();

	{
		final var associations = new MerkleAccountTokens();
		associations.associateAll(Set.of(aToken.toGrpcTokenId(), bToken.toGrpcTokenId(), cToken.toGrpcTokenId()));
		accountWithTokens.setTokens(associations);
	}
}
