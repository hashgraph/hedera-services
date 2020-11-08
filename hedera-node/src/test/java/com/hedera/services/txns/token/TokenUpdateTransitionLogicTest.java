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
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
	private AccountID oldTreasury = IdUtils.asAccount("1.2.4");
	private AccountID newTreasury = IdUtils.asAccount("1.2.5");
	private String symbol = "SYMBOL";
	private String name = "Name";
	private JKey adminKey = new JEd25519Key("w/e".getBytes());
	private TransactionBody tokenUpdateTxn;
	private MerkleToken token;

	private OptionValidator validator;
	private TokenStore store;
	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private Predicate<TokenUpdateTransactionBody> expiryOnlyCheck;

	private TokenUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		validator = mock(OptionValidator.class);
		store = mock(TokenStore.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		token = mock(MerkleToken.class);
		given(token.adminKey()).willReturn(Optional.of(adminKey));
		given(token.treasury()).willReturn(EntityId.ofNullableAccountId(oldTreasury));
		given(store.resolve(target)).willReturn(target);
		given(store.get(target)).willReturn(token);
		given(store.associationExists(newTreasury, target)).willReturn(true);
		given(store.associationExists(oldTreasury, target)).willReturn(true);
		withAlwaysValidValidator();

		txnCtx = mock(TransactionContext.class);

		expiryOnlyCheck = (Predicate<TokenUpdateTransactionBody>) mock(Predicate.class);
		given(expiryOnlyCheck.test(any())).willReturn(false);

		subject = new TokenUpdateTransitionLogic(validator, store, ledger, txnCtx, expiryOnlyCheck);
	}

	@Test
	public void abortsOnInvalidIdForSafety() {
		givenValidTxnCtx(true);
		given(store.resolve(target)).willReturn(TokenStore.MISSING_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_TOKEN_ID);
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
	public void abortsOnUnassociatedNewTreasury() {
		givenValidTxnCtx(true);
		givenToken(true, true);
		// and:
		given(store.associationExists(newTreasury, target)).willReturn(false);

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
		verify(txnCtx).setStatus(TOKEN_IS_IMMUTABLE);
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

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsMissingToken() {
		givenMissingToken();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsTooLongSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsInvalidSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// expect:
		assertEquals(INVALID_TOKEN_SYMBOL, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsTooLongName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsInvalidAdminKey() {
		givenInvalidAdminKey();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsInvalidKycKey() {
		givenInvalidKycKey();

		// expect:
		assertEquals(INVALID_KYC_KEY, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsInvalidWipeKey() {
		givenInvalidWipeKey();

		// expect:
		assertEquals(INVALID_WIPE_KEY, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsInvalidSupplyKey() {
		givenInvalidSupplyKey();

		// expect:
		assertEquals(INVALID_SUPPLY_KEY, subject.syntaxCheck().apply(tokenUpdateTxn));
	}

	@Test
	public void rejectsInvalidFreezeKey() {
		givenInvalidFreezeKey();

		// expect:
		assertEquals(INVALID_FREEZE_KEY, subject.syntaxCheck().apply(tokenUpdateTxn));
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
						.setSymbol(symbol)
						.setName(name)
						.setToken(target));
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

	private void givenMissingToken() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder())
				.build();
	}

	private void givenInvalidFreezeKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setFreezeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidAdminKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setAdminKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidWipeKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setWipeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidSupplyKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setSupplyKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidKycKey() {
		tokenUpdateTxn = TransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.setKycKey(Key.getDefaultInstance()))
				.build();
	}

	private void withAlwaysValidValidator() {
		given(validator.tokenNameCheck(any())).willReturn(OK);
		given(validator.tokenSymbolCheck(any())).willReturn(OK);
	}
}
