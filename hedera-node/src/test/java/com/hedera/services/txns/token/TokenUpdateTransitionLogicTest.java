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
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABlE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class TokenUpdateTransitionLogicTest {
	long thisSecond = 1_234_567L;
	private Instant now = Instant.ofEpochSecond(thisSecond);
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
	private Predicate<TokenUpdateTransactionBody> expiryOnlyCheck;

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

		expiryOnlyCheck = (Predicate<TokenUpdateTransactionBody>) mock(Predicate.class);
		given(expiryOnlyCheck.test(any())).willReturn(false);

		subject = new TokenUpdateTransitionLogic(store, ledger, txnCtx, expiryOnlyCheck);
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
		given(store.update(any(), anyLong())).willThrow(IllegalStateException.class);

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
		given(store.update(any(), anyLong())).willReturn(INVALID_TOKEN_SYMBOL);

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
		given(store.update(any(), anyLong())).willReturn(INVALID_TOKEN_SYMBOL);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).dropPendingTokenChanges();
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong(), anyBoolean());
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
		verify(store, never()).update(any(), anyLong());
		// and:
		verify(txnCtx).setStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
		// and:
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong(), anyBoolean());
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
		verify(store, never()).update(any(), anyLong());
		// and:
		verify(txnCtx).setStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
		// and:
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong(), anyBoolean());
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
		verify(store, never()).update(any(), anyLong());
		verify(ledger).unfreeze(newTreasury, target);
		// and:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
		// and:
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong(), anyBoolean());
	}

	@Test
	public void permitsExtendingExpiry() {
		givenValidTxnCtx(false);
		// and:
		given(token.adminKey()).willReturn(Optional.empty());
		given(expiryOnlyCheck.test(any())).willReturn(true);
		given(store.update(any(), anyLong())).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void abortsOnNotSetAdminKey() {
		givenValidTxnCtx(true);
		// and:
		given(token.adminKey()).willReturn(Optional.empty());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TOKEN_IS_IMMUTABlE);
	}

	@Test
	public void abortsOnAlreadyDeletedToken() {
		givenValidTxnCtx(true);
		// and:
		given(token.isDeleted()).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TOKEN_WAS_DELETED);
	}

	@Test
	public void doesntReplaceIdenticalTreasury() {
		givenValidTxnCtx(true, true);
		givenToken(true, true);
		given(store.update(any(), anyLong())).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).getTokenBalance(oldTreasury, target);
		// and:
		verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong(), anyBoolean());
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
		given(store.update(any(), anyLong())).willReturn(OK);
		given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
		given(ledger.doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance, true)).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).unfreeze(newTreasury, target);
		verify(ledger).grantKyc(newTreasury, target);
		verify(ledger).getTokenBalance(oldTreasury, target);
		verify(ledger).doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance, true);
		// and:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void doesntGrantKycOrUnfreezeNewTreasuryIfNoKeyIsPresent() {
		givenValidTxnCtx(true);
		// and:
		givenToken(false, false);
		// and:
		given(store.update(any(), anyLong())).willReturn(OK);
		given(ledger.doTokenTransfer(eq(target), eq(oldTreasury), eq(newTreasury), anyLong(), eq(true))).willReturn(OK);

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
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(targetRef));
		if (withNewTreasury) {
			builder.getTokenUpdateBuilder()
					.setTreasury(useDuplicateTreasury ? oldTreasury : newTreasury);
		}
		tokenUpdateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(ledger.exists(newTreasury)).willReturn(true);
		given(ledger.isDeleted(newTreasury)).willReturn(false);
		given(ledger.exists(oldTreasury)).willReturn(true);
		given(ledger.isDeleted(oldTreasury)).willReturn(false);
	}
}
