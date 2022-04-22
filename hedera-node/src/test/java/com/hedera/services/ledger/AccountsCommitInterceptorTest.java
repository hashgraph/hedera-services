package com.hedera.services.ledger;

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
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


@ExtendWith(MockitoExtension.class)
class AccountsCommitInterceptorTest {
	@Mock
	private SideEffectsTracker sideEffectsTracker;

	private AccountsCommitInterceptor subject;

	@Test
	void doesntCompleteRemovals() {
		setupLiveInterceptor();

		assertFalse(subject.completesPendingRemovals());
	}

	@Test
	void rejectsNonZeroSumChange() {
		setupLiveInterceptor();

		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomAndBalanceChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomAndBalanceChanges(counterpartyBalance - amount - 1));

		assertThrows(IllegalStateException.class, () -> subject.preview(changes));
	}

	@Test
	void tracksAsExpected() {
		setupMockInterceptor();

		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomAndBalanceChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomAndBalanceChanges(counterpartyBalance - amount));

		subject.preview(changes);

		verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
		verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount);
	}

	@Test
	void noopWithoutBalancesChanges() {
		setupMockInterceptor();

		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, Map.of(AccountProperty.ALIAS, ByteString.copyFromUtf8("IGNORE THE VASE")));
		subject.preview(changes);

		verify(sideEffectsTracker).getNetHbarChange();
		verifyNoMoreInteractions(sideEffectsTracker);
	}

	private void setupMockInterceptor() {
		subject = new AccountsCommitInterceptor(sideEffectsTracker);
	}

	private void setupLiveInterceptor() {
		subject = new AccountsCommitInterceptor(new SideEffectsTracker());
	}

	private Map<AccountProperty, Object> randomAndBalanceChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.ALIAS, ByteString.copyFromUtf8("IGNORE THE VASE"));
	}

	private static final long amount = 1L;
	private static final long partyBalance = 111L;
	private static final long counterpartyBalance = 555L;
	private static final AccountID partyId = AccountID.newBuilder().setAccountNum(123).build();
	private static final AccountID counterpartyId = AccountID.newBuilder().setAccountNum(321).build();
	private static final MerkleAccount party = MerkleAccountFactory.newAccount()
			.balance(partyBalance)
			.get();
	private static final MerkleAccount counterparty = MerkleAccountFactory.newAccount()
			.balance(counterpartyBalance)
			.get();
}
