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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingTokenRels;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftOwningAccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hedera.services.state.merkle.MerkleNftType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.nft.HederaNftStore;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.verify;

public class HederaLedgerLiveTest extends BaseHederaLedgerTest {
	long thisSecond = 1_234_567L;

	@BeforeEach
	void setup() {
		commonSetup();

		var accounts = new HashMapBackingAccounts();
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				() -> new MerkleAccount(),
				accounts,
				new ChangeSummaryManager<>());
		FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();

		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				() -> new MerkleTokenRelStatus(),
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
		tokenStore = new HederaTokenStore(
				ids,
				TestContextValidator.TEST_VALIDATOR,
				new MockGlobalDynamicProps(),
				() -> tokens,
				tokenRelsLedger);

		nftOwnershipLedger = new TransactionalLedger<>(
				NftOwningAccountProperty.class,
				() -> new MerkleEntityId(),
				null,
				new ChangeSummaryManager<>());
		FCMap<MerkleEntityId, MerkleNftType> nfts = new FCMap<>();
		nftStore = new HederaNftStore(
				ids,
				null,
				null,
				() -> nfts,
				nftOwnershipLedger);

		subject = new HederaLedger(nftStore, tokenStore, ids, creator, historian, accountsLedger);
	}

	@Test
	public void throwsOnCommittingInconsistentAdjustments() {
		// when:
		subject.begin();
		subject.adjustBalance(genesis, -1L);

		// then:
		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	public void resetsNetTransfersAfterCommit() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();
		// and:
		subject.begin();
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));

		// then:
		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	public void doesntIncludeZeroAdjustsInNetTransfers() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);

		// then:
		assertEquals(0L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	public void doesntAllowDestructionOfRealCurrency() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.destroy(a);

		// then:
		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	public void allowsDestructionOfEphemeralCurrency() {
		// when:
		subject.begin();
		AccountID a = asAccount("1.2.3");
		subject.spawn(a, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.destroy(a);
		subject.commit();

		// then:
		assertFalse(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	public void recordsCreationOfAccountDeletedInSameTxn() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);
		int numNetTransfers = subject.netTransfersInTxn().getAccountAmountsCount();
		subject.commit();

		// then:
		assertEquals(0, numNetTransfers);
		assertTrue(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	public void addsRecordsAndEntitiesBeforeCommitting() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();

		// then:
		verify(historian).addNewRecords();
		verify(historian).addNewEntities();
	}

	@Test
	public void resetsNetTransfersAfterRollback() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.rollback();
		// and:
		subject.begin();
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));

		// then:
		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	public void returnsNetTransfersInBalancedTxn() {
		setup();
		// and:
		TokenID tA, tB;

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		AccountID c = subject.create(genesis, 3_000L, new HederaAccountCustomizer().memo("c"));
		AccountID d = subject.create(genesis, 4_000L, new HederaAccountCustomizer().memo("d"));
		// and:
		var rA = tokenStore.createProvisionally(stdWith("MINE", "MINE", a), a, thisSecond);
		tA = rA.getCreated().get();
		tokenStore.commitCreation();
		var rB = tokenStore.createProvisionally(stdWith("YOURS", "YOURS", b), b, thisSecond);
		tB = rB.getCreated().get();
		tokenStore.commitCreation();
		// and:
		tokenStore.associate(a, List.of(tA, tB));
		tokenStore.associate(b, List.of(tA, tB));
		tokenStore.associate(c, List.of(tA, tB));
		tokenStore.associate(d, List.of(tA, tB));
		// and:
		subject.doTransfer(d, a, 1_000L);
		subject.delete(d, b);
		subject.adjustBalance(c, 1_000L);
		subject.adjustBalance(genesis, -1_000L);
		subject.doTransfers(TxnUtils.withAdjustments(a, -500L, b, 250L, c, 250L));
		// and:
		subject.adjustTokenBalance(a, tA, +10_000);
		subject.adjustTokenBalance(a, tA, -5_000);
		subject.adjustTokenBalance(a, tB, +1);
		subject.adjustTokenBalance(a, tB, -1);

		subject.adjustTokenBalance(b, tB, +10_000);
		subject.adjustTokenBalance(c, tB, +50);
		subject.adjustTokenBalance(c, tB, +50);
		subject.adjustTokenBalance(c, tB, -50);
		subject.adjustTokenBalance(c, tA, +5000);
		subject.freeze(a, tB);
		subject.adjustTokenBalance(a, tB, +1_000_000);
		accountsLedger.changeSetSoFar();

		// then:
		assertThat(
				subject.netTransfersInTxn().getAccountAmountsList(),
				containsInAnyOrder(
						AccountAmount.newBuilder().setAccountID(a).setAmount(1_500L).build(),
						AccountAmount.newBuilder().setAccountID(b).setAmount(5_250L).build(),
						AccountAmount.newBuilder().setAccountID(c).setAmount(4_250L).build(),
						AccountAmount.newBuilder().setAccountID(genesis).setAmount(-11_000L).build()));
		// and:
		assertThat(subject.netTokenTransfersInTxn(),
				contains(
						construct(tA, aa(a, +5_000), aa(c, +5_000)),
						construct(tB, aa(b, +10_000), aa(c, +50))
				));
	}

	@Test
	public void recognizesPendingCreates() {
		setup();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1L, new HederaAccountCustomizer().memo("a"));

		// then:
		assertTrue(subject.isPendingCreation(a));
		assertFalse(subject.isPendingCreation(genesis));
	}

	private TokenCreateTransactionBody stdWith(String symbol, String tokenName, AccountID account) {
		var key = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		return TokenCreateTransactionBody.newBuilder()
				.setAdminKey(key)
				.setFreezeKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
				.setSymbol(symbol)
				.setName(tokenName)
				.setInitialSupply(0)
				.setTreasury(account)
				.setExpiry(Timestamp.newBuilder().setSeconds(2 * thisSecond))
				.setDecimals(0)
				.setFreezeDefault(false)
				.build();
	}

	private TokenTransferList construct(TokenID token, AccountAmount... xfers) {
		return TokenTransferList.newBuilder()
				.setToken(token)
				.addAllTransfers(List.of(xfers))
				.build();
	}

}
