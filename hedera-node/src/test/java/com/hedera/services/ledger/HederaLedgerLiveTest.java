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

import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

class HederaLedgerLiveTest extends BaseHederaLedgerTestHelper {
	private static final long thisSecond = 1_234_567L;

	@BeforeEach
	void setup() {
		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
		nftsLedger = (TransactionalLedger<NftId, NftProperty, MerkleUniqueToken>) mock(TransactionalLedger.class);
		tokenRelsLedger = (TransactionalLedger<
				Pair<AccountID, TokenID>,
				TokenRelProperty,
				MerkleTokenRelStatus>) mock(TransactionalLedger.class);
		tokenStore = mock(HederaTokenStore.class);
		commonSetup();
		subject = new HederaLedger(tokenStore, creator, validator, historian, dynamicProps, accountsLedger);
	}
	

	@Test
	void throwsOnCommittingInconsistentAdjustments() {
		subject.begin();
		mockAccountOK(genesis);

		subject.adjustBalance(genesis, -1L);

		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}


	@Test
	void resetsNetTransfersAfterCommit() {
		mockAccountOK(genesis);
		mockAccountOK(misc);
		subject.begin();
		subject.adjustBalance(genesis, -1L);
		subject.adjustBalance(misc, 1L);
//		subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();

		subject.begin();
		subject.adjustBalance(misc, -10L);
		subject.adjustBalance(genesis, 10L);
//		subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));

		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void doesntIncludeZeroAdjustsInNetTransfers() {
		subject.begin();
		mockAccountOK(misc);
		mockAccountOK(genesis);

		/* "create" an account with 10 initial supply, paid by genesis */
		subject.adjustBalance(misc, -10);
		subject.adjustBalance(genesis, 10);

		/* "delete" the account with genesis as a beneficiary */
		subject.adjustBalance(misc, 10);
		subject.adjustBalance(genesis, -10);

		assertEquals(0L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void doesntAllowDestructionOfRealCurrency() {
		subject.begin();
		mockAccountOK(misc);
		// "delete" an account without beneficiary
		subject.adjustBalance(misc, -10);

		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	void allowsDestructionOfEphemeralCurrency() {
		subject.begin();
		final var a = asAccount("1.2.3");
		subject.spawn(a, 1_000L, new HederaAccountCustomizer().memo("a"));
		mockAccountOK(a);
		subject.destroy(a);
		subject.commit();

		assertFalse(subject.exists(a));
	}

	@Test
	void addsRecordsAndEntitiesBeforeCommitting() {
		subject.begin();
		mockAccountOK(misc);
		mockAccountOK(genesis);
		subject.adjustBalance(misc, -10);
		subject.adjustBalance(genesis, 10);
		subject.commit();

		verify(historian).finalizeExpirableTransactionRecord();
		verify(historian).noteNewExpirationEvents();
	}

	@Test
	void resetsNetTransfersAfterRollback() {
		mockAccountOK(misc);
		mockAccountOK(genesis);

		subject.begin();
		subject.adjustBalance(misc, -10);
		subject.adjustBalance(genesis, 10);
		subject.rollback();

		subject.begin();
		subject.adjustBalance(misc, -10);
		subject.adjustBalance(genesis, 10);

		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void returnsNetTransfersInBalancedTxn() {
		subject.begin();
		final var a = IdUtils.asAccount("0.0.11");
		final var b = IdUtils.asAccount("0.0.22");
		final var c = IdUtils.asAccount("0.0.33");
		final var d = IdUtils.asAccount("0.0.44");
		mockAccountOK(a);
		mockAccountOK(b);
		mockAccountOK(c);
		mockAccountOK(d);
		mockAccountOK(genesis);

		subject.doTransfer(d, a, 1_000L);
		subject.delete(d, b);
		subject.adjustBalance(c, 1_000L);
		subject.adjustBalance(genesis, -1_000L);
		subject.doTransfers(TxnUtils.withAdjustments(a, -500L, b, 250L, c, 250L));

		assertThat(
				subject.netTransfersInTxn().getAccountAmountsList(),
				containsInAnyOrder(
						AccountAmount.newBuilder().setAccountID(a).setAmount(500L).build(),
						AccountAmount.newBuilder().setAccountID(b).setAmount(100_250L).build(),
						AccountAmount.newBuilder().setAccountID(c).setAmount(1_250L).build(),
						AccountAmount.newBuilder().setAccountID(genesis).setAmount(-1_000L).build(),
						AccountAmount.newBuilder().setAccountID(d).setAmount(-101_000).build())
				);
	}

	private TokenCreateTransactionBody stdWith(final String symbol, final String tokenName, final AccountID account) {
		final var key = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
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
}
