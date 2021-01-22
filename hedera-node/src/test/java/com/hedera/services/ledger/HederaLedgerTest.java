package com.hedera.services.ledger;

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

import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

public class HederaLedgerTest extends BaseHederaLedgerTest {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	public void delegatesDestroy() {
		// when:
		subject.destroy(genesis);

		// then:
		verify(accountsLedger).destroy(genesis);
	}

	@Test
	public void indicatesNoChangeSetIfNotInTx() {
		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(accountsLedger, never()).changeSetSoFar();
		assertEquals(HederaLedger.NO_ACTIVE_TXN_CHANGE_SET, summary);
	}

	@Test
	public void delegatesChangeSetIfInTxn() {
		// setup:
		String zeroingGenesis = "{0.0.2: [BALANCE -> 0]}";
		String creatingTreasury = "{0.0.2 <-> 0.0.1001: [TOKEN_BALANCE -> 1_000_000]}";

		given(accountsLedger.isInTransaction()).willReturn(true);
		given(accountsLedger.changeSetSoFar()).willReturn(zeroingGenesis);
		given(tokenRelsLedger.changeSetSoFar()).willReturn(creatingTreasury);

		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(accountsLedger).changeSetSoFar();
		assertEquals(String.format(
				"--- ACCOUNTS ---\n%s\n--- TOKEN RELATIONSHIPS ---\n%s",
				zeroingGenesis,
				creatingTreasury), summary);
	}

	@Test
	public void delegatesGet() {
		// setup:
		MerkleAccount fakeGenesis = new MerkleAccount();

		given(accountsLedger.get(genesis)).willReturn(fakeGenesis);

		// expect:
		assertTrue(fakeGenesis == subject.get(genesis));
	}

	@Test
	public void delegatesExists() {
		// given:
		AccountID missing = asAccount("55.66.77");

		// when:
		boolean hasMissing = subject.exists(missing);
		boolean hasGenesis = subject.exists(genesis);

		// then:
		verify(accountsLedger, times(2)).exists(any());
		assertTrue(hasGenesis);
		assertFalse(hasMissing);
	}


	@Test
	public void setsSelfOnHistorian() {
		// expect:
		verify(historian).setLedger(subject);
		verify(creator).setLedger(subject);
		verify(historian).setCreator(creator);
	}

	@Test
	public void delegatesToCorrectContractProperty() {
		// when:
		subject.isSmartContract(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_SMART_CONTRACT);
	}

	@Test
	public void delegatesToCorrectDeletionProperty() {
		// when:
		subject.isDeleted(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_DELETED);
	}

	@Test
	public void delegatesToCorrectExpiryProperty() {
		// when:
		subject.expiry(genesis);

		// then:
		verify(accountsLedger).get(genesis, EXPIRY);
	}

	@Test
	public void throwsOnUnderfundedCreate() {
		// expect:
		assertThrows(InsufficientFundsException.class, () ->
				subject.create(rand, RAND_BALANCE + 1, noopCustomizer));
	}

	@Test
	public void performsFundedCreate() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);
		// and:
		given(accountsLedger.existsPending(IdUtils.asAccount(String.format("0.0.%d", NEXT_ID)))).willReturn(true);

		// when:
		AccountID created = subject.create(rand, 1_000L, customizer);

		// then:
		assertEquals(NEXT_ID, created.getAccountNum());
		verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE - 1_000L);
		verify(accountsLedger).create(created);
		verify(accountsLedger).set(created, BALANCE, 1_000L);
		verify(customizer).customize(created, accountsLedger);
	}

	@Test
	public void performsUnconditionalSpawn() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);
		AccountID contract = asAccount("1.2.3");
		long balance = 1_234L;
		// and:
		given(accountsLedger.existsPending(contract)).willReturn(true);

		// when:
		subject.spawn(contract, balance, customizer);

		// then:
		verify(accountsLedger).create(contract);
		verify(accountsLedger).set(contract, BALANCE, balance);
		verify(customizer).customize(contract, accountsLedger);
	}

	@Test
	public void deletesGivenAccount() {
		// when:
		subject.delete(rand, misc);

		// expect:
		verify(accountsLedger).set(rand, BALANCE, 0L);
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + RAND_BALANCE);
		verify(accountsLedger).set(rand, IS_DELETED, true);
	}

	@Test
	public void throwsOnCustomizingDeletedAccount() {
		// expect:
		assertThrows(DeletedAccountException.class, () -> subject.customize(deleted, noopCustomizer));
	}

	@Test
	public void throwsOnDeleteCustomizingUndeletedAccount() {
		// expect:
		assertThrows(DeletedAccountException.class, () -> subject.customizeDeleted(rand, noopCustomizer));
	}

	@Test
	public void customizesGivenAccount() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);

		// when:
		subject.customize(rand, customizer);

		// then:
		verify(customizer).customize(rand, accountsLedger);

	}

	@Test
	public void customizesDeletedAccount() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);

		// when:
		subject.customizeDeleted(deleted, customizer);

		// then:
		verify(customizer).customize(deleted, accountsLedger);

	}

	@Test
	public void makesPossibleAdjustment() {
		// setup:
		long amount = -1 * GENESIS_BALANCE / 2;

		// when:
		subject.adjustBalance(genesis, amount);

		// then:
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + amount);
	}

	@Test
	public void throwsOnNegativeBalance() {
		// setup:
		long overdraftAdjustment = -1 * GENESIS_BALANCE - 1;
		InsufficientFundsException e = null;

		// when:
		try {
			subject.adjustBalance(genesis, overdraftAdjustment);
		} catch (InsufficientFundsException ibce) {
			e = ibce;
		}

		// then:
		assertEquals(messageFor(genesis, overdraftAdjustment), e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void forwardsGetBalanceCorrectly() {
		// when:
		long balance = subject.getBalance(genesis);

		// then
		assertEquals(GENESIS_BALANCE, balance);
	}

	@Test
	public void forwardsTransactionalSemantics() {
		// setup:
		subject.setTokenRelsLedger(HederaLedger.UNUSABLE_TOKEN_RELS_LEDGER);
		InOrder inOrder = inOrder(accountsLedger);

		// when:
		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		// then:
		inOrder.verify(accountsLedger).begin();
		inOrder.verify(accountsLedger).commit();
		inOrder.verify(accountsLedger).begin();
		inOrder.verify(accountsLedger).rollback();
	}
}
