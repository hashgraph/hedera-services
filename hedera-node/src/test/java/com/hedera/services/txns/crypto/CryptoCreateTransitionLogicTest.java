package com.hedera.services.txns.crypto;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.CryptoCreateAccessor;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PROXY_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.longThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class CryptoCreateTransitionLogicTest {
	private static final Key KEY = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private static final long CUSTOM_AUTO_RENEW_PERIOD = 100_001L;
	private static final Long BALANCE = 1_234L;
	private static final String MEMO = "The particular is pounded til it is man";
	private static final int MAX_AUTO_ASSOCIATIONS = 1234;
	private static final int MAX_TOKEN_ASSOCIATIONS = 12345;
	private static final AccountID PROXY = AccountID.newBuilder().setAccountNum(4_321L).build();
	private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
	private static final AccountID CREATED = AccountID.newBuilder().setAccountNum(9_999L).build();
	private static final Instant CONSENSUS_TIME = Instant.now();
	public static final AccountID ALIASED_PROXY_ID = asAccountWithAlias("aaaaaaaaa");
	public static final AccountID ALIASED_PAYER = asAccountWithAlias("aaa");
	private static final EntityNum PAYER_NUM = EntityNum.fromAccountId(PAYER);
	private static final EntityNum PROXY_NUM = EntityNum.fromAccountId(PROXY);

	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoCreateTxn;
	private SigImpactHistorian sigImpactHistorian;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor platformAccessor;
	private CryptoCreateAccessor accessor;
	private CryptoCreateTransitionLogic subject;
	private GlobalDynamicProperties dynamicProperties;
	private AliasManager aliasManager;

	@BeforeEach
	private void setup() {
		txnCtx = mock(TransactionContext.class);
		ledger = mock(HederaLedger.class);
		aliasManager = mock(AliasManager.class);
		validator = mock(OptionValidator.class);
		sigImpactHistorian = mock(SigImpactHistorian.class);
		dynamicProperties = mock(GlobalDynamicProperties.class);
		platformAccessor = mock(PlatformTxnAccessor.class);

		given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
		given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
		withRubberstampingValidator();
		given(aliasManager.unaliased(PAYER)).willReturn(PAYER_NUM);
		given(aliasManager.unaliased(PROXY)).willReturn(PROXY_NUM);

		subject = new CryptoCreateTransitionLogic(ledger, validator, sigImpactHistorian, txnCtx, dynamicProperties);
	}

	@Test
	void hasCorrectApplicability() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void returnsMemoTooLongWhenValidatorSays() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(validator.memoCheck(MEMO)).willReturn(MEMO_TOO_LONG);

		assertEquals(MEMO_TOO_LONG, subject.validateSemantics(accessor));
	}

	@Test
	void returnsKeyRequiredOnEmptyKey() throws InvalidProtocolBufferException {
		givenValidTxnCtx(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build(), PROXY, ourTxnId());

		assertEquals(KEY_REQUIRED, subject.validateSemantics(accessor));
	}

	@Test
	void requiresKey() throws InvalidProtocolBufferException {
		givenMissingKey();

		assertEquals(KEY_REQUIRED, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsMissingAutoRenewPeriod() throws InvalidProtocolBufferException {
		givenMissingAutoRenewPeriod();

		assertEquals(INVALID_RENEWAL_PERIOD, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsNegativeBalance() throws InvalidProtocolBufferException {
		givenAbsurdInitialBalance();

		assertEquals(INVALID_INITIAL_BALANCE, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsNegativeSendThreshold() throws InvalidProtocolBufferException {
		givenAbsurdSendThreshold();

		assertEquals(INVALID_SEND_RECORD_THRESHOLD, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsNegativeReceiveThreshold() throws InvalidProtocolBufferException {
		givenAbsurdReceiveThreshold();

		assertEquals(INVALID_RECEIVE_RECORD_THRESHOLD, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsKeyWithBadEncoding() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(validator.hasGoodEncoding(any())).willReturn(false);

		assertEquals(BAD_ENCODING, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.validateSemantics(accessor));
	}

	@Test
	void acceptsValidTxn() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		assertEquals(OK, subject.validateSemantics(accessor));
	}

	@Test
	void rejectsInvalidMaxAutomaticAssociations() throws InvalidProtocolBufferException {
		givenInvalidMaxAutoAssociations();

		assertEquals(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT,
				subject.validateSemantics(accessor));
	}

	@Test
	void followsHappyPathWithOverrides() throws Throwable {
		final var expiry = CONSENSUS_TIME.getEpochSecond() + CUSTOM_AUTO_RENEW_PERIOD;
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenValidTxnCtx();
		given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);

		subject.doStateTransition();

		verify(ledger).create(argThat(PAYER::equals), longThat(BALANCE::equals), captor.capture());
		verify(txnCtx).setCreated(CREATED);
		verify(txnCtx).setStatus(SUCCESS);
		verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

		final var changes = captor.getValue().getChanges();
		assertEquals(7, changes.size());
		assertEquals(CUSTOM_AUTO_RENEW_PERIOD, (long) changes.get(AUTO_RENEW_PERIOD));
		assertEquals(expiry, (long) changes.get(EXPIRY));
		assertEquals(KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
		assertEquals(true, changes.get(IS_RECEIVER_SIG_REQUIRED));
		assertEquals(EntityId.fromGrpcAccountId(PROXY), changes.get(AccountProperty.PROXY));
		assertEquals(MEMO, changes.get(AccountProperty.MEMO));
		assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
	}

	@Test
	void translatesInsufficientPayerBalance() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(ledger.create(any(), anyLong(), any())).willThrow(InsufficientFundsException.class);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INSUFFICIENT_PAYER_BALANCE);
	}

	@Test
	void translatesUnknownException() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		cryptoCreateTxn = cryptoCreateTxn.toBuilder()
				.setCryptoCreateAccount(cryptoCreateTxn.getCryptoCreateAccount().toBuilder().setKey(unmappableKey()))
				.build();
		setAccessor();
		given(txnCtx.accessor()).willReturn(platformAccessor);
		given(platformAccessor.getDelegate()).willReturn(accessor);

		subject.doStateTransition();

		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	void worksWithValidAliasedProxy() throws InvalidProtocolBufferException {
		givenValidTxnCtxWithAliasedProxy();
		given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
		given(aliasManager.unaliased(ALIASED_PAYER)).willReturn(PAYER_NUM);
		given(aliasManager.unaliased(ALIASED_PROXY_ID)).willReturn(PROXY_NUM);

		subject.doStateTransition();

		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void failsWithInvalidAliasedProxy() throws InvalidProtocolBufferException {
		givenValidTxnCtxWithAliasedProxy();
		given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
		given(aliasManager.unaliased(ALIASED_PAYER)).willReturn(PAYER_NUM);
		given(aliasManager.unaliased(ALIASED_PROXY_ID)).willReturn(MISSING_NUM);

		subject.doStateTransition();
		verify(txnCtx).setStatus(INVALID_PROXY_ACCOUNT_ID);
	}

	private Key unmappableKey() {
		return Key.getDefaultInstance();
	}

	private void givenMissingKey() throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setInitialBalance(BALANCE)
				).build();

		setAccessor();
	}

	private void givenMissingAutoRenewPeriod() throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setKey(KEY)
								.setInitialBalance(BALANCE)
				).build();

		setAccessor();
	}

	private void givenAbsurdSendThreshold() throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setSendRecordThreshold(-1L)
				).build();

		setAccessor();
	}

	private void givenAbsurdReceiveThreshold() throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setReceiveRecordThreshold(-1L)
				).build();

		setAccessor();
	}

	private void givenAbsurdInitialBalance() throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setInitialBalance(-1L)
				).build();

		setAccessor();
	}

	private void givenInvalidMaxAutoAssociations() throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setProxyAccountID(PROXY)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setKey(KEY)
								.setMaxAutomaticTokenAssociations(MAX_TOKEN_ASSOCIATIONS + 1)
				).build();

		setAccessor();
	}

	private void setAccessor() throws InvalidProtocolBufferException {
		final var txn = new SwirldTransaction(
				Transaction.newBuilder().setBodyBytes(cryptoCreateTxn.toByteString()).build().toByteArray());
		accessor = new CryptoCreateAccessor(txn.getContentsDirect(), aliasManager);
		given(platformAccessor.getDelegate()).willReturn(accessor);
	}

	private void givenValidTxnCtx() throws InvalidProtocolBufferException {
		givenValidTxnCtx(KEY, PROXY, ourTxnId());
	}

	private void givenValidTxnCtxWithAliasedProxy() throws InvalidProtocolBufferException {
		givenValidTxnCtx(KEY, ALIASED_PROXY_ID, txnIdWithAlias());
	}

	private void givenValidTxnCtx(final Key toUse, final AccountID proxy, final TransactionID txnId) throws InvalidProtocolBufferException {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setProxyAccountID(proxy)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setKey(toUse)
								.setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
				).build();
		setAccessor();
		given(txnCtx.accessor()).willReturn(platformAccessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
				.build();
	}

	private TransactionID txnIdWithAlias() {
		return TransactionID.newBuilder()
				.setAccountID(ALIASED_PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);
	}
}
