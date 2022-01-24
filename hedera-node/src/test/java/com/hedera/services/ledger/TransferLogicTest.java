package com.hedera.services.ledger;

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

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferLogicTest {
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	@Mock
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private TokenStore tokenStore;
	@Mock
	private AutoCreationLogic autoCreationLogic;
	@Mock
	private UniqueTokenViewsManager tokenViewsManager;
	@Mock
	private AccountRecordsHistorian recordsHistorian;

	private TransferLogic subject;

	@BeforeEach
	void setUp() {
		final var backingAccounts = new HashMapBackingAccounts();
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());
		subject = new TransferLogic(
				accountsLedger, nftsLedger, tokenRelsLedger, tokenStore,
				sideEffectsTracker, tokenViewsManager, dynamicProperties, TEST_VALIDATOR,
				autoCreationLogic, recordsHistorian);
	}

	@Test
	void throwsIseOnNonEmptyAliasWithNullAutoCreationLogic() {
		final var firstAmount = 1_000L;
		final var firstAlias = ByteString.copyFromUtf8("fake");
		final var inappropriateTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount));

		subject = new TransferLogic(
				accountsLedger, nftsLedger, tokenRelsLedger, tokenStore,
				sideEffectsTracker, tokenViewsManager, dynamicProperties, TEST_VALIDATOR,
				null, recordsHistorian);

		final var triggerList = List.of(inappropriateTrigger);
		assertThrows(IllegalStateException.class, () -> subject.doZeroSum(triggerList));
	}

	@Test
	void cleansUpOnFailedAutoCreation() {
		final var mockCreation = IdUtils.asAccount("0.0.1234");
		final var firstAmount = 1_000L;
		final var firstAlias = ByteString.copyFromUtf8("fake");
		final var failingTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount));

		given(autoCreationLogic.create(failingTrigger, accountsLedger))
				.willReturn(Pair.of(INSUFFICIENT_ACCOUNT_BALANCE, 0L));
		accountsLedger.begin();
		accountsLedger.create(mockCreation);
		given(autoCreationLogic.reclaimPendingAliases()).willReturn(true);

		assertFailsWith(() -> subject.doZeroSum(List.of(failingTrigger)), INSUFFICIENT_ACCOUNT_BALANCE);

		verify(autoCreationLogic).reclaimPendingAliases();
		assertTrue(accountsLedger.getCreations().isEmpty());
	}

	@Test
	void createsAccountsAsExpected() {
		final var autoFee = 500L;
		final var firstAmount = 1_000L;
		final var secondAmount = 2_000;
		final var firstAlias = ByteString.copyFromUtf8("fake");
		final var secondAlias = ByteString.copyFromUtf8("mock");
		final var firstNewAccount = IdUtils.asAccount("0.0.1234");
		final var secondNewAccount = IdUtils.asAccount("0.0.1235");

		final var firstTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount));
		final var secondTrigger = BalanceChange.changingHbar(aliasedAa(secondAlias, secondAmount));
		given(autoCreationLogic.create(firstTrigger, accountsLedger)).willAnswer(invocationOnMock -> {
			accountsLedger.create(firstNewAccount);
			final var change = (BalanceChange) invocationOnMock.getArgument(0);
			change.replaceAliasWith(firstNewAccount);
			change.adjustUnits(-autoFee);
			change.setNewBalance(change.units());
			return Pair.of(OK, autoFee);
		});
		given(autoCreationLogic.create(secondTrigger, accountsLedger)).willAnswer(invocationOnMock -> {
			accountsLedger.create(secondNewAccount);
			final var change = (BalanceChange) invocationOnMock.getArgument(0);
			change.replaceAliasWith(secondNewAccount);
			change.adjustUnits(-autoFee);
			change.setNewBalance(change.units());
			return Pair.of(OK, autoFee);
		});
		final var changes = List.of(firstTrigger, secondTrigger);

		final var funding = IdUtils.asAccount("0.0.98");
		accountsLedger.begin();
		accountsLedger.create(funding);

		subject.doZeroSum(changes);

		assertEquals(2 * autoFee, (long) accountsLedger.get(funding, AccountProperty.BALANCE));
		verify(sideEffectsTracker).trackHbarChange(funding, 2 * autoFee);
		assertEquals(firstAmount - autoFee, (long) accountsLedger.get(firstNewAccount, AccountProperty.BALANCE));
		assertEquals(secondAmount - autoFee, (long) accountsLedger.get(secondNewAccount, AccountProperty.BALANCE));
		verify(autoCreationLogic).submitRecordsTo(recordsHistorian);
	}

	private AccountAmount aliasedAa(final ByteString alias, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(AccountID.newBuilder().setAlias(alias))
				.setAmount(amount)
				.build();
	}
}
