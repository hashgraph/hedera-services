package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.tokens.TokenCreationResult;
import com.hedera.services.tokens.TokenScope;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class TokenUpdateTransitionLogicTest {
	private TokenID target = IdUtils.asToken("1.2.666");
	private TokenRef targetRef = IdUtils.asIdRef("1.2.666");
	private AccountID oldTreasury = IdUtils.asAccount("1.2.4");
	private AccountID newTreasury = IdUtils.asAccount("1.2.5");
	private JKey adminKey = new JEd25519Key("w/e".getBytes());
	private TransactionBody tokenUpdateTxn;
	private MerkleToken token;

	private TokenStore store;
	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TokenUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		store = mock(TokenStore.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		token = mock(MerkleToken.class);
		given(token.adminKey()).willReturn(Optional.of(adminKey));
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(oldTreasury));
		given(store.resolve(targetRef)).willReturn(target);
		given(store.get(target)).willReturn(token);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenUpdateTransitionLogic(store, ledger, txnCtx);
	}

	@Test
	public void abortsOnInvalidRefForSafety() {
		givenValidTxnCtx(true);
		given(store.resolve(targetRef)).willReturn(TokenStore.MISSING_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_TOKEN_REF);
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx(false);
		givenToken(true, true);

		// and:
		given(store.update(any())).willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
		// and:
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfCreationFails() {
		givenValidTxnCtx();
		// and:
		given(store.update(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_TOKEN_SYMBOL);
		// and:
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void rollsbackNewTreasuryChangesIfUpdateFails() {
		givenValidTxnCtx(true);
		givenToken(true, true);
		// and:
		given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
		given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
		given(store.update(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).dropPendingTokenChanges();
		// and:
		verify(txnCtx).setStatus(INVALID_TOKEN_SYMBOL);
	}

	@Test
	public void abortsOnMissingNewTreasury() {
		givenValidTxnCtx(true);
		givenToken(true, true);
		// and:
		given(ledger.exists(newTreasury)).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(store, never()).update(any());
		// and:
		verify(txnCtx).setStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
	}

	@Test
	public void abortsOnDeletedNewTreasury() {
		givenValidTxnCtx(true);
		givenToken(true, true);
		// and:
		given(ledger.isDeleted(newTreasury)).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(store, never()).update(any());
		// and:
		verify(txnCtx).setStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
	}

	@Test
	public void abortsOnInvalidNewTreasury() {
		givenValidTxnCtx(true);
		givenToken(true, true);
		// and:
		given(ledger.unfreeze(newTreasury, target)).willReturn(INVALID_ACCOUNT_ID);

		// when:
		subject.doStateTransition();

		// then:
		verify(store, never()).update(any());
		verify(ledger).unfreeze(newTreasury, target);
		// and:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	public void abortsOnNotSetAdminKey() {
		givenValidTxnCtx(true);
		// and:
		given(token.adminKey()).willReturn(Optional.empty());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(UNAUTHORIZED);
	}

	@Test
	public void doesntReplaceIdenticalTreasury() {
		givenValidTxnCtx(true, true);
		givenToken(true, true);
		given(store.update(any())).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).getTokenBalance(oldTreasury, target);
		verify(ledger, never()).doTransfer(eq(oldTreasury), eq(newTreasury), anyLong());
		// and:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void followsHappyPathWithNewTreasury() {
		// setup:
		long oldTreasuryBalance = 1000;
		givenValidTxnCtx(true);
		givenToken(true, true);
		// and:
		given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
		given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
		given(store.update(any())).willReturn(OK);
		given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).unfreeze(newTreasury, target);
		verify(ledger).grantKyc(newTreasury, target);
		verify(ledger).getTokenBalance(oldTreasury, target);
		verify(ledger).doTransfer(oldTreasury, newTreasury, oldTreasuryBalance);
		// and:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void doesntGrantKycOrUnfreezeNewTreasuryIfNoKeyIsPresent() {
		givenValidTxnCtx(true);
		// and:
		givenToken(false, false);
		// and:
		given(store.update(any())).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).unfreeze(newTreasury, target);
		verify(ledger, never()).grantKyc(newTreasury, target);
		// and:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(false);
	}

	private void givenToken(boolean hasKyc, boolean hasFreeze) {
		given(token.hasKycKey()).willReturn(hasKyc);
		given(token.hasFreezeKey()).willReturn(hasFreeze);
	}

	private void givenValidTxnCtx(boolean withNewTreasury) {
		givenValidTxnCtx(withNewTreasury, false);
	}

	private void givenValidTxnCtx(boolean withNewTreasury, boolean useDuplicateTreasury) {
		var builder = TransactionBody.newBuilder()
				.setTokenUpdate(TokenManagement.newBuilder()
						.setToken(targetRef));
		if (withNewTreasury) {
			builder.getTokenUpdateBuilder()
					.setTreasury(useDuplicateTreasury ? oldTreasury : newTreasury);
		}
		tokenUpdateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(ledger.exists(newTreasury)).willReturn(true);
		given(ledger.isDeleted(newTreasury)).willReturn(false);
		given(ledger.exists(oldTreasury)).willReturn(true);
		given(ledger.isDeleted(oldTreasury)).willReturn(false);
	}
}
