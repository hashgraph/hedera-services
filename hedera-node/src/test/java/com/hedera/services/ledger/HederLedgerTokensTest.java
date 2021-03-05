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

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.test.utils.IdUtils.tokenWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

public class HederLedgerTokensTest extends BaseHederaLedgerTest {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	public void delegatesToSetTokens() {
		// setup:
		var tokens = new MerkleAccountTokens();

		// when:
		subject.setAssociatedTokens(genesis, tokens);

		// then:
		verify(accountsLedger).set(genesis, TOKENS, tokens);
	}

	@Test
	public void getsTokenBalance() {
		// given:
		var balance = subject.getTokenBalance(misc, frozenId);

		// expect:
		assertEquals(miscFrozenTokenBalance, balance);
	}

	@Test
	public void recognizesAccountWithNonZeroTokenBalances() {
		// expect:
		assertFalse(subject.allTokenBalancesVanish(misc));
	}

	@Test
	public void ignoresNonZeroBalanceOfDeletedToken() {
		given(frozenToken.isDeleted()).willReturn(true);
		
		// expect:
		assertTrue(subject.allTokenBalancesVanish(misc));
	}

	@Test
	public void throwsIfSubjectHasNoUsableTokenRelsLedger() {
		subject.setTokenRelsLedger(HederaLedger.UNUSABLE_TOKEN_RELS_LEDGER);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.allTokenBalancesVanish(deletable));
	}

	@Test
	public void recognizesAccountWithZeroTokenBalances() {
		// expect:
		assertTrue(subject.allTokenBalancesVanish(deletable));
	}

	@Test
	public void refusesToAdjustWrongly() {
		given(tokenStore.adjustBalance(misc, tokenId, 555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.adjustTokenBalance(misc, tokenId, 555);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		assertEquals(0, subject.numTokenTouches);
	}

	@Test
	public void adjustsIfValid() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		// given:
		var status = subject.adjustTokenBalance(misc, tokenId, 555);

		// expect:
		assertEquals(OK, status);
		// and:
		assertEquals(
				AccountAmount.newBuilder().setAccountID(misc).setAmount(555).build(),
				subject.netTokenTransfers.get(tokenId).getAccountAmounts(0));
	}

	@Test
	public void injectsLedgerToTokenStore() {
		// expect:
		verify(tokenStore).setAccountsLedger(accountsLedger);
		verify(tokenStore).setHederaLedger(subject);
	}

	@Test
	public void delegatesToGetTokens() {
		// setup:
		var tokens = new MerkleAccountTokens();

		given(accountsLedger.get(genesis, AccountProperty.TOKENS)).willReturn(tokens);

		// when:
		var actual = subject.getAssociatedTokens(genesis);

		// then:
		Assertions.assertSame(actual, tokens);
	}

	@Test
	public void delegatesFreezeOps() {
		// when:
		subject.freeze(misc, frozenId);

		// then:
		verify(tokenStore).freeze(misc, frozenId);

		// and when:
		subject.unfreeze(misc, frozenId);

		// then:
		verify(tokenStore).unfreeze(misc, frozenId);
	}

	@Test
	public void delegatesKnowingOps() {
		// when:
		subject.grantKyc(misc, frozenId);

		// then:
		verify(tokenStore).grantKyc(misc, frozenId);

		// and when:
		subject.revokeKyc(misc, frozenId);

		// then:
		verify(tokenStore).revokeKyc(misc, frozenId);
	}

	@Test
	public void delegatesTokenChangeDrop() {
		subject.numTokenTouches = 2;
		subject.tokensTouched[0] = tokenWith(111);
		subject.tokensTouched[1] = tokenWith(222);
		// and:
		subject.netTokenTransfers.put(
				tokenWith(111),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.2"))));
		subject.netTokenTransfers.put(
				tokenWith(222),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.3"))));
		// when:
		subject.dropPendingTokenChanges();

		// then:
		verify(tokenRelsLedger).rollback();
		// and;
		assertEquals(0, subject.numTokenTouches);
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(111)).getAccountAmountsCount());
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(222)).getAccountAmountsCount());
	}

	@Test
	public void onlyRollsbackIfTokenRelsLedgerInTxn() {
		given(tokenRelsLedger.isInTransaction()).willReturn(false);

		// when:
		subject.dropPendingTokenChanges();

		verify(tokenRelsLedger, never()).rollback();
	}

	@Test
	public void forwardsTransactionalSemanticsToRelsLedgerIfPresent() {
		// setup:
		InOrder inOrder = inOrder(tokenRelsLedger);

		given(tokenRelsLedger.isInTransaction()).willReturn(true);

		// when:
		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		// then:
		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(tokenRelsLedger).isInTransaction();
		inOrder.verify(tokenRelsLedger).commit();
		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(tokenRelsLedger).isInTransaction();
		inOrder.verify(tokenRelsLedger).rollback();
	}
}
