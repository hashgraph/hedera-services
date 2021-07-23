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

import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

public class HederaLedgerTestHelper extends BaseHederaLedgerTestHelper {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	void delegatesDestroy() {
		// when:
		subject.destroy(genesis);

		// then:
		verify(accountsLedger).destroy(genesis);
	}

	@Test
	void indicatesNoChangeSetIfNotInTx() {
		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(accountsLedger, never()).changeSetSoFar();
		assertEquals(HederaLedger.NO_ACTIVE_TXN_CHANGE_SET, summary);
	}

	@Test
	void delegatesChangeSetIfInTxn() {
		// setup:
		String zeroingGenesis = "{0.0.2: [BALANCE -> 0]}";
		String creatingTreasury = "{0.0.2 <-> 0.0.1001: [TOKEN_BALANCE -> 1_000_000]}";
		String changingOwner = "{NftId{shard=0, realm=0, num=10000, serialNo=1234}: " +
				"[OWNER -> EntityId{shard=3, realm=4, num=5}]}";

		given(accountsLedger.isInTransaction()).willReturn(true);
		given(accountsLedger.changeSetSoFar()).willReturn(zeroingGenesis);
		given(tokenRelsLedger.changeSetSoFar()).willReturn(creatingTreasury);
		given(nftsLedger.changeSetSoFar()).willReturn(changingOwner);

		// when:
		String summary = subject.currentChangeSet();

		// then:
		verify(accountsLedger).changeSetSoFar();
		final var desired = "--- ACCOUNTS ---\n" +
				"{0.0.2: [BALANCE -> 0]}\n" +
				"--- TOKEN RELATIONSHIPS ---\n" +
				"{0.0.2 <-> 0.0.1001: [TOKEN_BALANCE -> 1_000_000]}\n" +
				"--- NFTS ---\n" +
				"{NftId{shard=0, realm=0, num=10000, serialNo=1234}: [OWNER -> EntityId{shard=3, realm=4, num=5}]}";
		assertEquals(desired, summary);
	}

	@Test
	void delegatesGet() {
		// setup:
		MerkleAccount fakeGenesis = new MerkleAccount();

		given(accountsLedger.getFinalized(genesis)).willReturn(fakeGenesis);

		// expect:
		assertTrue(fakeGenesis == subject.get(genesis));
	}

	@Test
	void delegatesExists() {
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
	void setsCreatorOnHistorian() {
		// expect:
		verify(historian).setCreator(creator);
	}

	@Test
	void delegatesToCorrectContractProperty() {
		// when:
		subject.isSmartContract(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_SMART_CONTRACT);
	}

	@Test
	void delegatesToCorrectDeletionProperty() {
		// when:
		subject.isDeleted(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_DELETED);
	}

	@Test
	void delegatesToCorrectSigReqProperty() {
		// when:
		subject.isReceiverSigRequired(genesis);

		// then:
		verify(accountsLedger).get(genesis, IS_RECEIVER_SIG_REQUIRED);
	}

	@Test
	void recognizesDetached() {
		// setup:
		validator = mock(OptionValidator.class);
		given(validator.isAfterConsensusSecond(anyLong())).willReturn(false);
		given(accountsLedger.get(genesis, BALANCE)).willReturn(0L);
		// and:
		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProps, accountsLedger);

		// when:
		var result = subject.isDetached(genesis);

		// then:
		assertTrue(result);
	}

	@Test
	void recognizesCannotBeDetachedIfContract() {
		// setup:
		validator = mock(OptionValidator.class);
		given(validator.isAfterConsensusSecond(anyLong())).willReturn(false);
		given(accountsLedger.get(genesis, BALANCE)).willReturn(0L);
		given(accountsLedger.get(genesis, IS_SMART_CONTRACT)).willReturn(true);
		// and:
		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProps, accountsLedger);

		// when:
		var result = subject.isDetached(genesis);

		// then:
		assertFalse(result);
	}

	@Test
	void recognizesCannotBeDetachedIfAutoRenewDisabled() {
		// setup:
		validator = mock(OptionValidator.class);
		given(validator.isAfterConsensusSecond(anyLong())).willReturn(false);
		given(accountsLedger.get(genesis, BALANCE)).willReturn(0L);
		// and:
		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProps, accountsLedger);
		// and:
		dynamicProps.disableAutoRenew();

		// when:
		var result = subject.isDetached(genesis);

		// then:
		assertFalse(result);
	}

	@Test
	void delegatesToCorrectExpiryProperty() {
		// when:
		subject.expiry(genesis);

		// then:
		verify(accountsLedger).get(genesis, EXPIRY);
	}

	@Test
	void delegatesToCorrectAutoRenewProperty() {
		// when:
		subject.autoRenewPeriod(genesis);

		// then:
		verify(accountsLedger).get(genesis, AUTO_RENEW_PERIOD);
	}

	@Test
	void delegatesToCorrectProxyProperty() {
		// when:
		subject.proxy(genesis);

		// then:
		verify(accountsLedger).get(genesis, PROXY);
	}

	@Test
	void throwsOnUnderfundedCreate() {
		// expect:
		assertThrows(InsufficientFundsException.class, () ->
				subject.create(rand, RAND_BALANCE + 1, noopCustomizer));
	}

	@Test
	void performsFundedCreate() {
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
	void performsUnconditionalSpawn() {
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
	void deletesGivenAccount() {
		// when:
		subject.delete(rand, misc);

		// expect:
		verify(accountsLedger).set(rand, BALANCE, 0L);
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + RAND_BALANCE);
		verify(accountsLedger).set(rand, IS_DELETED, true);
	}

	@Test
	void throwsOnCustomizingDeletedAccount() {
		// expect:
		assertThrows(DeletedAccountException.class, () -> subject.customize(deleted, noopCustomizer));
	}

	@Test
	void throwsOnDeleteCustomizingUndeletedAccount() {
		// expect:
		assertThrows(DeletedAccountException.class, () -> subject.customizeDeleted(rand, noopCustomizer));
	}

	@Test
	void customizesGivenAccount() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);

		// when:
		subject.customize(rand, customizer);

		// then:
		verify(customizer).customize(rand, accountsLedger);

	}

	@Test
	void customizesDeletedAccount() {
		// given:
		HederaAccountCustomizer customizer = mock(HederaAccountCustomizer.class);

		// when:
		subject.customizeDeleted(deleted, customizer);

		// then:
		verify(customizer).customize(deleted, accountsLedger);

	}

	@Test
	void makesPossibleAdjustment() {
		// setup:
		long amount = -1 * GENESIS_BALANCE / 2;

		// when:
		subject.adjustBalance(genesis, amount);

		// then:
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + amount);
	}

	@Test
	void throwsOnNegativeBalance() {
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
	void forwardsGetBalanceCorrectly() {
		// when:
		long balance = subject.getBalance(genesis);

		// then
		assertEquals(GENESIS_BALANCE, balance);
	}

	@Test
	void forwardsTransactionalSemantics() {
		// setup:
		subject.setTokenRelsLedger(null);
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
