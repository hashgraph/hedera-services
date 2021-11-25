package com.hedera.services.store;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AccountStoreTest {
	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private BackingStore<AccountID, MerkleAccount> accounts;

	private AccountStore subject;

	@BeforeEach
	void setUp() {
		setupAccounts();

		subject = new AccountStore(validator, dynamicProperties, accounts);
	}

	@Test
	void objectContractWorks() {
		assertNotNull(subject.getValidator());
	}

	/* --- Account loading --- */

	@Test
	void loadsContractAsExpected() {
		miscMerkleAccount.setSmartContract(true);
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		Account account = subject.loadContract(miscId);

		assertEquals(Id.fromGrpcAccount(miscMerkleId.toGrpcAccountId()), account.getId());
	}

	@Test
	void failsLoadingContractWithInvalidId() {
		miscMerkleAccount.setSmartContract(false);
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		TxnUtils.assertFailsWith(() -> subject.loadContract(miscId), INVALID_CONTRACT_ID);
	}

	@Test
	void failsLoadingMissingAccount() {
		assertMiscAccountLoadFailsWith(INVALID_ACCOUNT_ID);
	}

	@Test
	void failsLoadingDeleted() {
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		miscMerkleAccount.setDeleted(true);

		assertMiscAccountLoadFailsWith(ACCOUNT_DELETED);
	}

	@Test
	void failsLoadingDetached() throws NegativeAccountBalanceException {
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		given(validator.isAfterConsensusSecond(expiry)).willReturn(false);
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);
		miscMerkleAccount.setBalance(0L);

		assertMiscAccountLoadFailsWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void canAlwaysLoadWithNonzeroBalance() {
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);

		// when:
		final var actualAccount = subject.loadAccount(miscId);

		// then:
		assertEquals(miscAccount, actualAccount);

		final var actualAccount2 = subject.loadAccountOrFailWith(miscId, FAIL_INVALID);
		assertEquals(miscAccount, actualAccount2);
	}

	@Test
	void persistenceUpdatesTokens() {
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		setupWithMutableAccount(miscMerkleId, miscMerkleAccount);
		// and:
		final var aThirdToken = new Token(new Id(0, 0, 888));
		// and:
		final var expectedReplacement = MerkleAccountFactory.newAccount()
				.balance(balance)
				.assocTokens(firstAssocTokenId, secondAssocTokenId, aThirdToken.getId())
				.expirationTime(expiry)
				.maxAutomaticAssociations(maxAutoAssociations)
				.alreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations)
				.proxy(proxy.asGrpcAccount())
				.get();

		// given:
		final var model = subject.loadAccount(miscId);

		// when:
		model.associateWith(List.of(aThirdToken), Integer.MAX_VALUE, false);
		// and:
		subject.commitAccount(model);

		// then:
		assertEquals(expectedReplacement, miscMerkleAccount);
//		verify(accounts, never()).replace(miscMerkleId, expectedReplacement);
		// and:
		assertNotSame(miscMerkleAccount.tokens().getIds(), model.getAssociatedTokens());
	}

	@Test
	void failsWithGivenCause() throws NegativeAccountBalanceException {
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		miscMerkleAccount.setDeleted(true);

		var ex = assertThrows(
				InvalidTransactionException.class, () -> subject.loadAccountOrFailWith(miscId, FAIL_INVALID));
		assertEquals(FAIL_INVALID, ex.getResponseCode());

		miscMerkleAccount.setDeleted(false);
		given(validator.isAfterConsensusSecond(expiry)).willReturn(false);
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);
		miscMerkleAccount.setBalance(0L);

		var ex2 = assertThrows(
				InvalidTransactionException.class, () -> subject.loadAccountOrFailWith(miscId, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, ex2.getResponseCode());
	}

	@Test
	void persistanceUpdatesAutoAssociations() {
		setupWithAccount(miscMerkleId, miscMerkleAccount);
		setupWithMutableAccount(miscMerkleId, miscMerkleAccount);
		var newMax = maxAutoAssociations + 5;
		var newUsedCount = alreadyUsedAutoAssociations - 10;
		// and:
		final var expectedReplacement = MerkleAccountFactory.newAccount()
				.balance(balance)
				.assocTokens(firstAssocTokenId, secondAssocTokenId)
				.expirationTime(expiry)
				.maxAutomaticAssociations(newMax)
				.alreadyUsedAutomaticAssociations(newUsedCount)
				.proxy(proxy.asGrpcAccount())
				.get();

		// given:
		final var model = subject.loadAccount(miscId);

		// when:
		model.setMaxAutomaticAssociations(newMax);
		// decrease the already Used automatic associations by 10
		for (int i = 0; i < 11; i++) {
			model.decrementUsedAutomaticAssocitions();
		}
		model.incrementUsedAutomaticAssocitions();

		// and:
		subject.commitAccount(model);

		// then:
		assertEquals(expectedReplacement, miscMerkleAccount);
	}

	private void setupWithAccount(EntityNum anId, MerkleAccount anAccount) {
		given(accounts.getImmutableRef(anId.toGrpcAccountId())).willReturn(anAccount);
	}

	private void setupWithMutableAccount(EntityNum anId, MerkleAccount anAccount) {
		given(accounts.getRef(anId.toGrpcAccountId())).willReturn(anAccount);
	}

	private void assertMiscAccountLoadFailsWith(ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadAccount(miscId));
		assertEquals(status, ex.getResponseCode());
	}

	private void setupAccounts() {
		miscMerkleAccount = MerkleAccountFactory.newAccount()
				.balance(balance)
				.assocTokens(firstAssocTokenId, secondAssocTokenId)
				.expirationTime(expiry)
				.maxAutomaticAssociations(maxAutoAssociations)
				.alreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations)
				.proxy(proxy.asGrpcAccount())
				.get();

		miscAccount.setExpiry(expiry);
		miscAccount.initBalance(balance);
		miscAccount.setAssociatedTokens(miscMerkleAccount.tokens().getIds());
		miscAccount.setMaxAutomaticAssociations(maxAutoAssociations);
		miscAccount.setAlreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations);
		miscAccount.setProxy(proxy);
		autoRenewAccount.setExpiry(expiry);
		autoRenewAccount.initBalance(balance);
	}

	private final long expiry = 1_234_567L;
	private final long balance = 1_000L;
	private final long miscAccountNum = 1_234L;
	private final long autoRenewAccountNum = 3_234L;
	private final long firstAssocTokenNum = 666L;
	private final long secondAssocTokenNum = 777L;
	private final long miscProxyAccount = 9_876L;
	private final int alreadyUsedAutoAssociations = 12;
	private final int maxAutoAssociations = 123;
	private final Id miscId = new Id(0, 0, miscAccountNum);
	private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
	private final Id firstAssocTokenId = new Id(0, 0, firstAssocTokenNum);
	private final Id secondAssocTokenId = new Id(0, 0, secondAssocTokenNum);
	private final Id proxy = new Id(0, 0, miscProxyAccount);
	private final EntityNum miscMerkleId = EntityNum.fromLong(miscAccountNum);
	private final Account miscAccount = new Account(miscId);
	private final Account autoRenewAccount = new Account(autoRenewId);

	private MerkleAccount miscMerkleAccount;
}
