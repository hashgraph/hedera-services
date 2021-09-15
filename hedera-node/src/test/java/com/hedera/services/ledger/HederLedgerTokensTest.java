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
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class HederLedgerTokensTest extends BaseHederaLedgerTestHelper {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	void delegatesToSetTokens() {
		final var tokens = new MerkleAccountTokens();

		subject.setAssociatedTokens(genesis, tokens);

		verify(accountsLedger).set(genesis, TOKENS, tokens);
	}

	@Test
	void getsTokenBalance() {
		final var balance = subject.getTokenBalance(misc, frozenId);

		assertEquals(miscFrozenTokenBalance, balance);
	}

	@Test
	void recognizesAccountWithNonZeroTokenBalances() {
		assertFalse(subject.allTokenBalancesVanish(misc));
	}

	@Test
	void ignoresNonZeroBalanceOfDeletedToken() {
		given(frozenToken.isDeleted()).willReturn(true);

		assertTrue(subject.allTokenBalancesVanish(misc));
	}

	@Test
	void throwsIfSubjectHasNoUsableTokenRelsLedger() {
		subject.setTokenRelsLedger(null);

		assertThrows(IllegalStateException.class, () -> subject.allTokenBalancesVanish(deletable));
	}

	@Test
	void recognizesAccountWithZeroTokenBalances() {
		assertTrue(subject.allTokenBalancesVanish(deletable));
	}

	@Test
	void refusesToAdjustWrongly() {
		given(tokenStore.adjustBalance(misc, tokenId, 555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		final var status = subject.adjustTokenBalance(misc, tokenId, 555);

		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		assertEquals(0, subject.numTouches);
	}

	@Test
	void adjustsIfValid() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		final var status = subject.adjustTokenBalance(misc, tokenId, 555);

		assertEquals(OK, status);
		assertEquals(
				AccountAmount.newBuilder().setAccountID(misc).setAmount(555).build(),
				subject.netTokenTransfers.get(tokenId).getAccountAmounts(0));
	}

	@Test
	void injectsLedgerToTokenStore() {
		verify(tokenStore).setAccountsLedger(accountsLedger);
		verify(tokenStore).setHederaLedger(subject);
		verify(creator).setLedger(subject);
	}

	@Test
	void delegatesToGetTokens() {
		final var tokens = new MerkleAccountTokens();
		given(accountsLedger.get(genesis, AccountProperty.TOKENS)).willReturn(tokens);

		final var actual = subject.getAssociatedTokens(genesis);

		Assertions.assertSame(tokens, actual);
	}

	@Test
	void delegatesFreezeOps() {
		subject.freeze(misc, frozenId);
		verify(tokenStore).freeze(misc, frozenId);

		subject.unfreeze(misc, frozenId);
		verify(tokenStore).unfreeze(misc, frozenId);
	}

	@Test
	void delegatesKnowingOps() {
		subject.grantKyc(misc, frozenId);
		verify(tokenStore).grantKyc(misc, frozenId);

		subject.revokeKyc(misc, frozenId);
		verify(tokenStore).revokeKyc(misc, frozenId);
	}

	@Test
	void delegatesTokenChangeDrop() {
		final var manager = mock(UniqTokenViewsManager.class);
		subject.setTokenViewsManager(manager);

		subject.numTouches = 2;
		subject.tokensTouched[0] = tokenWith(111);
		subject.tokensTouched[1] = tokenWith(222);
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
		given(tokenStore.get(tokenId)).willReturn(token);
		given(tokenStore.get(frozenId).tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
		given(tokenStore.get(tokenId).tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
		given(nftsLedger.isInTransaction()).willReturn(true);
		given(manager.isInTransaction()).willReturn(true);

		subject.dropPendingTokenChanges();

		verify(tokenRelsLedger).rollback();
		verify(nftsLedger).rollback();
		verify(manager).rollback();
		verify(accountsLedger).undoChangesOfType(NUM_NFTS_OWNED);

		assertEquals(0, subject.numTouches);
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(111)).getAccountAmountsCount());
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(222)).getAccountAmountsCount());
	}

	@Test
	void onlyRollsbackIfTokenRelsLedgerInTxn() {
		given(tokenRelsLedger.isInTransaction()).willReturn(false);
		subject.setTokenViewsManager(mock(UniqTokenViewsManager.class));

		subject.dropPendingTokenChanges();

		verify(tokenRelsLedger, never()).rollback();
	}

	@Test
	void forwardsTransactionalSemanticsToTokenLedgersIfPresent() {
		final var manager = mock(UniqTokenViewsManager.class);
		final var inOrder = inOrder(tokenRelsLedger, nftsLedger, manager);
		given(tokenRelsLedger.isInTransaction()).willReturn(true);
		given(nftsLedger.isInTransaction()).willReturn(true);
		given(manager.isInTransaction()).willReturn(true);
		subject.setTokenViewsManager(manager);

		subject.begin();
		subject.commit();
		subject.begin();
		subject.rollback();

		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(nftsLedger).begin();
		inOrder.verify(manager).begin();
		inOrder.verify(tokenRelsLedger).isInTransaction();
		inOrder.verify(tokenRelsLedger).commit();
		inOrder.verify(nftsLedger).isInTransaction();
		inOrder.verify(nftsLedger).commit();
		inOrder.verify(manager).isInTransaction();
		inOrder.verify(manager).commit();

		inOrder.verify(tokenRelsLedger).begin();
		inOrder.verify(nftsLedger).begin();
		inOrder.verify(manager).begin();
		inOrder.verify(tokenRelsLedger).isInTransaction();
		inOrder.verify(tokenRelsLedger).rollback();
		inOrder.verify(nftsLedger).isInTransaction();
		inOrder.verify(nftsLedger).rollback();
		inOrder.verify(manager).isInTransaction();
		inOrder.verify(manager).rollback();
	}
}
