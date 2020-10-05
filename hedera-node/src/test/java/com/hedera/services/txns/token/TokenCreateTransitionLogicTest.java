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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.tokens.TokenCreationResult;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class TokenCreateTransitionLogicTest {
	long thisSecond = 1_234_567L;
	private Instant now = Instant.ofEpochSecond(thisSecond);
	private int decimals = 2;
	private long initialSupply = 1_000_000L;
	private AccountID payer = IdUtils.asAccount("1.2.3");
	private AccountID treasury = IdUtils.asAccount("1.2.4");
	private AccountID renewAccount = IdUtils.asAccount("1.2.5");
	private TokenID created = IdUtils.asToken("1.2.666");
	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private TransactionBody tokenCreateTxn;

	private OptionValidator validator;
	private TokenStore store;
	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TokenCreateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		validator = mock(OptionValidator.class);
		store = mock(TokenStore.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.consensusTime()).willReturn(Instant.now());
		withAlwaysValidValidator();

		subject = new TokenCreateTransitionLogic(validator, store, ledger, txnCtx);
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(FAIL_INVALID);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfCreationFails() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.failure(INVALID_ADMIN_KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(INVALID_ADMIN_KEY);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfAdjustmentFailsDueToTokenLimitPerAccountExceeded() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.success(created));
		given(store.associate(any(), anyList())).willReturn(OK);
		given(ledger.unfreeze(treasury, created)).willReturn(OK);
		given(ledger.adjustTokenBalance(treasury, created, initialSupply))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfAssociationFails() {
		givenValidTxnCtx(false, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.success(created));
		given(store.associate(any(), anyList())).willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfUnfreezeFails() {
		givenValidTxnCtx(false, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.success(created));
		given(store.associate(any(), anyList())).willReturn(OK);
		given(ledger.unfreeze(treasury, created)).willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void followsHappyPathWithAllKeys() {
		givenValidTxnCtx(true, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.success(created));
		given(ledger.unfreeze(treasury, created)).willReturn(OK);
		given(ledger.grantKyc(treasury, created)).willReturn(OK);
		given(ledger.adjustTokenBalance(treasury, created, initialSupply))
				.willReturn(OK);
		given(store.associate(treasury, List.of(created))).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).associate(treasury, List.of(created));
		verify(ledger).unfreeze(treasury, created);
		verify(ledger).grantKyc(treasury, created);
		// and:
		verify(txnCtx).setCreated(created);
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		verify(store).commitCreation();
	}

	@Test
	public void doesntUnfreezeIfNoKeyIsPresent() {
		givenValidTxnCtx(true, false);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.success(created));
		given(store.associate(any(), anyList())).willReturn(OK);
		given(ledger.grantKyc(treasury, created)).willReturn(OK);
		given(ledger.adjustTokenBalance(treasury, created, initialSupply))
				.willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).unfreeze(treasury, created);
		verify(ledger).grantKyc(treasury, created);
		// and:
		verify(txnCtx).setCreated(created);
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		verify(store).commitCreation();
	}

	@Test
	public void doesntGrantKycIfNoKeyIsPresent() {
		givenValidTxnCtx(false, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(TokenCreationResult.success(created));
		given(store.associate(any(), anyList())).willReturn(OK);
		given(ledger.unfreeze(treasury, created)).willReturn(OK);
		given(ledger.adjustTokenBalance(treasury, created, initialSupply))
				.willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).unfreeze(treasury, created);
		verify(ledger, never()).grantKyc(treasury, created);
		// and:
		verify(txnCtx).setCreated(created);
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		verify(store).commitCreation();
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void acceptsMissingAutoRenewAcount() {
		givenValidMissingRenewAccount();

		// expect
		assertEquals(OK, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsMissingSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(MISSING_TOKEN_SYMBOL);

		// expect:
		assertEquals(MISSING_TOKEN_SYMBOL, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsTooLongSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// expect:
		assertEquals(INVALID_TOKEN_SYMBOL, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsMissingName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(MISSING_TOKEN_NAME);

		// expect:
		assertEquals(MISSING_TOKEN_NAME, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsTooLongName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidInitialSupply() {
		givenInvalidInitialSupply();

		// expect:
		assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidDecimals() {
		givenInvalidDecimals();

		// expect:
		assertEquals(INVALID_TOKEN_DECIMALS, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsMissingTreasury() {
		givenMissingTreasury();

		// expect:
		assertEquals(INVALID_TREASURY_ACCOUNT_FOR_TOKEN, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidAdminKey() {
		givenInvalidAdminKey();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidKycKey() {
		givenInvalidKycKey();

		// expect:
		assertEquals(INVALID_KYC_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidWipeKey() {
		givenInvalidWipeKey();

		// expect:
		assertEquals(INVALID_WIPE_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidSupplyKey() {
		givenInvalidSupplyKey();

		// expect:
		assertEquals(INVALID_SUPPLY_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectMissingFreezeKeyWithFreezeDefault() {
		givenMissingFreezeKeyWithFreezeDefault();

		// expect:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidFreezeKey() {
		givenInvalidFreezeKey();

		// expect:
		assertEquals(INVALID_FREEZE_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidAdminKeyBytes() {
		givenInvalidAdminKeyBytes();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	@Test
	public void rejectsInvalidExpirationTime() {
		givenInvalidExpirationTime();

		assertEquals(INVALID_EXPIRATION_TIME, subject.syntaxCheck().apply(tokenCreateTxn));
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(false, false);
	}

	private void givenValidTxnCtx(boolean withKyc, boolean withFreeze) {
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setAutoRenewAccount(renewAccount)
						.setExpiry(thisSecond + thisSecond));
		if (withFreeze) {
			builder.getTokenCreationBuilder().setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey());
		}
		if (withKyc) {
			builder.getTokenCreationBuilder().setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey());
		}
		tokenCreateTxn = builder.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(store.isCreationPending()).willReturn(true);
	}

	private void givenInvalidInitialSupply() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(-1))
				.build();
	}

	private void givenInvalidDecimals() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(0)
						.setDecimals(-1))
				.build();
	}

	private void givenMissingTreasury() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder())
				.build();
	}

	private void givenMissingFreezeKeyWithFreezeDefault() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setFreezeDefault(true))
				.build();
	}

	private void givenInvalidFreezeKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setFreezeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidAdminKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidAdminKeyBytes() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(Key.newBuilder().setEd25519(ByteString.copyFrom("1".getBytes()))))
				.build();
	}

	private void givenInvalidKycKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setKycKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidWipeKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setWipeKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidSupplyKey() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setSupplyKey(Key.getDefaultInstance()))
				.build();
	}

	private void givenInvalidExpirationTime() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setExpiry(-1))
				.build();
	}

	private void givenValidMissingRenewAccount() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(thisSecond + Instant.now().getEpochSecond()))
				.build();
	}

	private void withAlwaysValidValidator() {
		given(validator.tokenNameCheck(any())).willReturn(OK);
		given(validator.tokenSymbolCheck(any())).willReturn(OK);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
	}
}