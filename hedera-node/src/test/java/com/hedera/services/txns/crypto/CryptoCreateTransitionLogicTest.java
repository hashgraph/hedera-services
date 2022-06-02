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

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.legacy.core.jproto.JEd25519Key.ED25519_BYTE_LENGTH;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
	private static final long CUSTOM_SEND_THRESHOLD = 49_000L;
	private static final long CUSTOM_RECEIVE_THRESHOLD = 51_001L;
	private static final Long BALANCE = 1_234L;
	private static final String MEMO = "The particular is pounded til it is man";
	private static final int MAX_AUTO_ASSOCIATIONS = 1234;
	private static final int MAX_TOKEN_ASSOCIATIONS = 12345;
	private static final AccountID PROXY = AccountID.newBuilder().setAccountNum(4_321L).build();
	private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
	private static final AccountID CREATED = AccountID.newBuilder().setAccountNum(9_999L).build();
	private static final AccountID STAKED_ACCOUNT_ID = AccountID.newBuilder().setAccountNum(1000L).build();
	private static final Instant consensusTime = Instant.now();

	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoCreateTxn;
	private SigImpactHistorian sigImpactHistorian;
	private TransactionContext txnCtx;
	private SignedTxnAccessor accessor;
	private GlobalDynamicProperties dynamicProperties;
	private CryptoCreateTransitionLogic subject;
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	private NodeInfo nodeInfo;

	@BeforeEach
	private void setup() {
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		ledger = mock(HederaLedger.class);
		accessor = mock(SignedTxnAccessor.class);
		validator = mock(OptionValidator.class);
		sigImpactHistorian = mock(SigImpactHistorian.class);
		dynamicProperties = mock(GlobalDynamicProperties.class);
		accounts = mock(MerkleMap.class);
		nodeInfo = mock(NodeInfo.class);
		given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
		withRubberstampingValidator();

		subject = new CryptoCreateTransitionLogic(ledger, validator, sigImpactHistorian, txnCtx, dynamicProperties,
				() -> accounts, nodeInfo);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void returnsMemoTooLongWhenValidatorSays() {
		givenValidTxnCtx();
		given(validator.memoCheck(MEMO)).willReturn(MEMO_TOO_LONG);

		assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void returnsKeyRequiredOnEmptyKey() {
		givenValidTxnCtx(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());

		assertEquals(KEY_REQUIRED, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void requiresKey() {
		givenMissingKey();

		assertEquals(KEY_REQUIRED, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsInvalidKey() {
		givenEmptyKey();

		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsMissingAutoRenewPeriod() {
		givenMissingAutoRenewPeriod();

		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsNegativeBalance() {
		givenAbsurdInitialBalance();

		assertEquals(INVALID_INITIAL_BALANCE, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsNegativeSendThreshold() {
		givenAbsurdSendThreshold();

		assertEquals(INVALID_SEND_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsNegativeReceiveThreshold() {
		givenAbsurdReceiveThreshold();

		assertEquals(INVALID_RECEIVE_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsKeyWithBadEncoding() {
		givenValidTxnCtx();
		given(validator.hasGoodEncoding(any())).willReturn(false);

		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();
		given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
		given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
		given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);

		assertEquals(OK, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsInvalidMaxAutomaticAssociations() {
		givenInvalidMaxAutoAssociations();
		given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);
		given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);

		assertEquals(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT,
				subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void followsHappyPathWithOverrides() throws Throwable {
		final var expiry = consensusTime.getEpochSecond() + CUSTOM_AUTO_RENEW_PERIOD;
		final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		givenValidTxnCtx();
		given(ledger.create(any(), anyLong(), any())).willReturn(CREATED);
		given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);

		subject.doStateTransition();

		verify(ledger).create(argThat(PAYER::equals), longThat(BALANCE::equals), captor.capture());
		verify(txnCtx).setCreated(CREATED);
		verify(txnCtx).setStatus(SUCCESS);
		verify(sigImpactHistorian).markEntityChanged(CREATED.getAccountNum());

		final var changes = captor.getValue().getChanges();
		assertEquals(8, changes.size());
		assertEquals(CUSTOM_AUTO_RENEW_PERIOD, (long) changes.get(AUTO_RENEW_PERIOD));
		assertEquals(expiry, (long) changes.get(EXPIRY));
		assertEquals(KEY, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
		assertEquals(true, changes.get(IS_RECEIVER_SIG_REQUIRED));
		assertEquals(null, changes.get(AccountProperty.PROXY));
		assertEquals(EntityId.fromGrpcAccountId(STAKED_ACCOUNT_ID).num(), changes.get(STAKED_ID));
		assertEquals(false, changes.get(DECLINE_REWARD));
		assertEquals(MEMO, changes.get(AccountProperty.MEMO));
		assertEquals(MAX_AUTO_ASSOCIATIONS, changes.get(MAX_AUTOMATIC_ASSOCIATIONS));
	}

	@Test
	void translatesInsufficientPayerBalance() {
		givenValidTxnCtx();
		given(ledger.create(any(), anyLong(), any())).willThrow(InsufficientFundsException.class);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INSUFFICIENT_PAYER_BALANCE);
	}

	@Test
	void translatesUnknownException() {
		givenValidTxnCtx();
		cryptoCreateTxn = cryptoCreateTxn.toBuilder()
				.setCryptoCreateAccount(cryptoCreateTxn.getCryptoCreateAccount().toBuilder().setKey(unmappableKey()))
				.build();
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);
		given(txnCtx.activePayer()).willReturn(ourAccount());
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	void validatesStakedId() {
		givenValidTxnCtx();

		given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);

		assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void usingProxyAccountFails() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setProxyAccountID(PROXY)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
								.setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
								.setKey(KEY)
								.setMaxAutomaticTokenAssociations(MAX_TOKEN_ASSOCIATIONS + 1)
				).build();

		assertEquals(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED, subject.semanticCheck().apply(cryptoCreateTxn));

		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setProxyAccountID(AccountID.getDefaultInstance())
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
								.setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
								.setKey(KEY)
								.setMaxAutomaticTokenAssociations(MAX_TOKEN_ASSOCIATIONS + 1)
				).build();
		given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);
		assertNotEquals(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	private Key unmappableKey() {
		return Key.getDefaultInstance();
	}

	private void givenMissingKey() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setInitialBalance(BALANCE)
				).build();
	}

	private void givenEmptyKey() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder()
						.setKey(asKeyUnchecked(new JEd25519Key(new byte[ED25519_BYTE_LENGTH - 1])))
						.setInitialBalance(BALANCE)
				).build();
	}

	private void givenMissingAutoRenewPeriod() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setKey(KEY)
								.setInitialBalance(BALANCE)
				).build();
	}

	private void givenAbsurdSendThreshold() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setSendRecordThreshold(-1L)
				).build();
	}

	private void givenAbsurdReceiveThreshold() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setReceiveRecordThreshold(-1L)
				).build();
	}

	private void givenAbsurdInitialBalance() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setInitialBalance(-1L)
				).build();
	}

	private void givenInvalidMaxAutoAssociations() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setStakedAccountId(STAKED_ACCOUNT_ID)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
								.setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
								.setKey(KEY)
								.setMaxAutomaticTokenAssociations(MAX_TOKEN_ASSOCIATIONS + 1)
				).build();
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(KEY);
	}

	private void givenValidTxnCtx(final Key toUse) {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setStakedAccountId(STAKED_ACCOUNT_ID)
								.setDeclineReward(false)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setReceiveRecordThreshold(CUSTOM_RECEIVE_THRESHOLD)
								.setSendRecordThreshold(CUSTOM_SEND_THRESHOLD)
								.setKey(toUse)
								.setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
				).build();
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);
		given(txnCtx.activePayer()).willReturn(ourAccount());
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private AccountID ourAccount() {
		return PAYER;
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);
	}
}
