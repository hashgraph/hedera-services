package com.hedera.services.txns.token;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
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

class TokenCreateTransitionLogicTest {
	long thisSecond = 1_234_567L;
	private Instant now = Instant.ofEpochSecond(thisSecond);
	private int decimals = 2;
	private long initialSupply = 1_000_000L;
	private String memo = "...descending into thin air, where no arms / outstretch to catch her";
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
	void setsFailInvalidIfUnhandledException() {
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
	void abortsIfCreationFails() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

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
	void abortsIfTokenIdIsMissingInTheResult() {
		givenValidTxnCtx();
		CreationResult<TokenID> result = mock(CreationResult.class);

		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(result);
		given(result.getStatus()).willReturn(OK);
		given(result.getCreated()).willReturn(Optional.empty());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	void abortsIfInitialExpiryIsInvalid() {
		givenValidTxnCtx();
		given(validator.isValidExpiry(any())).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
	}

	@Test
	void abortsIfAdjustmentFailsDueToTokenLimitPerAccountExceeded() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.success(created));
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
	void abortsIfAssociationFails() {
		givenValidTxnCtx(false, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.success(created));
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
	void abortsIfUnfreezeFails() {
		givenValidTxnCtx(false, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.success(created));
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
	void followsHappyPathWithAllKeys() {
		givenValidTxnCtx(true, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.success(created));
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
	void doesntUnfreezeIfNoKeyIsPresent() {
		givenValidTxnCtx(true, false);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.success(created));
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
	void doesntGrantKycIfNoKeyIsPresent() {
		givenValidTxnCtx(false, true);
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer, thisSecond))
				.willReturn(CreationResult.success(created));
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
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void acceptsMissingAutoRenewAcount() {
		givenValidMissingRenewAccount();

		// expect
		assertEquals(OK, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsMissingSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(MISSING_TOKEN_SYMBOL);

		// expect:
		assertEquals(MISSING_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsTooLongSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidSymbol() {
		givenValidTxnCtx();
		given(validator.tokenSymbolCheck(any())).willReturn(INVALID_TOKEN_SYMBOL);

		// expect:
		assertEquals(INVALID_TOKEN_SYMBOL, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsMissingName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(MISSING_TOKEN_NAME);

		// expect:
		assertEquals(MISSING_TOKEN_NAME, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsTooLongName() {
		givenValidTxnCtx();
		given(validator.tokenNameCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);

		// expect:
		assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidInitialSupply() {
		givenInvalidInitialSupply();

		// expect:
		assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidDecimals() {
		givenInvalidDecimals();

		// expect:
		assertEquals(INVALID_TOKEN_DECIMALS, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsMissingTreasury() {
		givenMissingTreasury();

		// expect:
		assertEquals(INVALID_TREASURY_ACCOUNT_FOR_TOKEN, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidAdminKey() {
		givenInvalidAdminKey();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidKycKey() {
		givenInvalidKycKey();

		// expect:
		assertEquals(INVALID_KYC_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidWipeKey() {
		givenInvalidWipeKey();

		// expect:
		assertEquals(INVALID_WIPE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidSupplyKey() {
		givenInvalidSupplyKey();

		// expect:
		assertEquals(INVALID_SUPPLY_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectMissingFreezeKeyWithFreezeDefault() {
		givenMissingFreezeKeyWithFreezeDefault();

		// expect:
		assertEquals(TOKEN_HAS_NO_FREEZE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidFreezeKey() {
		givenInvalidFreezeKey();

		// expect:
		assertEquals(INVALID_FREEZE_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidAdminKeyBytes() {
		givenInvalidAdminKeyBytes();

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidMemo() {
		givenValidTxnCtx();
		given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);

		// expect:
		assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(tokenCreateTxn));
	}

	@Test
	void rejectsExpiryInPastInPrecheck() {
		givenInvalidExpirationTime();

		assertEquals(INVALID_EXPIRATION_TIME, subject.semanticCheck().apply(tokenCreateTxn));
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(false, false);
	}

	private void givenValidTxnCtx(boolean withKyc, boolean withFreeze) {
		final var expiry = Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
		var builder = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setMemo(memo)
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setAutoRenewAccount(renewAccount)
						.setExpiry(expiry));
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
		given(validator.isValidExpiry(expiry)).willReturn(true);
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
						.setExpiry(Timestamp.newBuilder().setSeconds(-1)))
				.build();
	}

	private void givenValidMissingRenewAccount() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setInitialSupply(initialSupply)
						.setDecimals(decimals)
						.setTreasury(treasury)
						.setAdminKey(key)
						.setExpiry(Timestamp.newBuilder().setSeconds(thisSecond + Instant.now().getEpochSecond())))
				.build();
	}

	private void withAlwaysValidValidator() {
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.tokenNameCheck(any())).willReturn(OK);
		given(validator.tokenSymbolCheck(any())).willReturn(OK);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
	}
}
