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

import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CryptoUpdateTransitionLogicTest {
	private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L);
	private static final long CURRENT_EXPIRY = CONSENSUS_TIME.getEpochSecond() + 2L;
	private static final long NEW_EXPIRY = CONSENSUS_TIME.getEpochSecond() + 7776000L;
	private static final int CUR_MAX_AUTOMATIC_ASSOCIATIONS = 10;
	private static final int NEW_MAX_AUTOMATIC_ASSOCIATIONS = 15;
	private static final int MAX_TOKEN_ASSOCIATIONS = 12345;

	private static final Key KEY = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private static final long AUTO_RENEW_PERIOD = 100_001L;
	private static final AccountID PROXY = AccountID.newBuilder().setAccountNum(4_321L).build();
	private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
	private static final AccountID TARGET = AccountID.newBuilder().setAccountNum(9_999L).build();
	private static final String MEMO = "Not since life began";

	@Mock
	private Account target;
	@Mock
	private AccountStore accountStore;
	@Mock
	private ContextOptionValidator validator;
	@Mock
	private TransactionBody cryptoUpdateTxn;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private TransactionRecordService transactionRecordService;

	private CryptoUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoUpdateTransitionLogic(accountStore, validator, txnCtx, transactionRecordService, dynamicProperties);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();
		givenHappyPathValidator();

		assertEquals(OK, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsKeyWithBadEncoding() {
		rejectsKey(unmappableKey());
	}

	@Test
	void rejectsInvalidKey() {
		rejectsKey(emptyKey());
	}

	@Test
	void rejectsInvalidMemo() {
		givenValidTxnCtx();
		given(validator.memoCheck(MEMO)).willReturn(MEMO_TOO_LONG);

		assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		givenHappyPathValidator();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsDetachedAccount() {
		givenValidTxnCtx();
		givenLoadedAccount();
		given(target.isDetached()).willReturn(true);

		thenWillFailWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void rejectsInvalidExpiry() {
		givenValidTxnCtx();
		givenLoadedAccount();
		given(validator.isValidExpiry(any())).willReturn(false);

		thenWillFailWith(INVALID_EXPIRATION_TIME);
	}

	@Test
	void permitsDetachedIfOnlyExtendingExpiry() {
		givenValidTxnCtxWithExpiry(NEW_EXPIRY);
		givenLoadedAccount();
		given(target.isDetached()).willReturn(true);
		given(validator.isValidExpiry(any())).willReturn(true);

		subject.doStateTransition();

		verify(target).updateFromGrpc(any(), any(), any(), any(), any(), any(), any());
		verify(accountStore).persistAccount(any());
		verify(transactionRecordService).includeChangesToAccount(any());
	}

	@Test
	void rejectsInvalidExpiryForDetached() {
		givenValidTxnCtxWithExpiry(CURRENT_EXPIRY);
		givenLoadedAccount();
		given(target.isDetached()).willReturn(true);
		given(target.getExpiry()).willReturn(NEW_EXPIRY);
		given(validator.isValidExpiry(any())).willReturn(true);

		thenWillFailWith(EXPIRATION_REDUCTION_NOT_ALLOWED);
	}

	@Test
	void rejectsSmartContract() {
		givenValidTxnCtx();
		givenSmartContractAccount();

		thenWillFailWith(INVALID_ACCOUNT_ID);
	}

	@Test
	void rejectMaxAutomaticAssociationsLessThanAlreadyExisting() {
		givenValidTxnCtxWithMaxAutomaticAssociations(CUR_MAX_AUTOMATIC_ASSOCIATIONS);
		givenLoadedAccount();
		given(target.getAlreadyUsedAutomaticAssociations()).willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS);

		thenWillFailWith(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
	}

	@Test
	void rejectMaxAutomaticAssociationsExceedsMaxLimit() {
		givenValidTxnCtxWithMaxAutomaticAssociations(MAX_TOKEN_ASSOCIATIONS + 1);
		givenLoadedAccount();
		given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS);

		thenWillFailWith(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
	}

	@Test
	void translatesMissingAccount() {
		givenValidTxnCtx();
		givenMissingAccount();

		thenWillFailWith(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesDeletedAccount() {
		givenValidTxnCtx();
		givenDeletedAccount();

		thenWillFailWith(ACCOUNT_DELETED);
	}

	private Key unmappableKey() {
		return Key.getDefaultInstance();
	}

	private Key emptyKey() {
		return Key.newBuilder().setThresholdKey(
				ThresholdKey.newBuilder()
						.setKeys(KeyList.getDefaultInstance())
						.setThreshold(0)
		).build();
	}

	private void rejectsKey(final Key key) {
		givenValidTxnCtx();
		given(validator.memoCheck(MEMO)).willReturn(OK);
		cryptoUpdateTxn = cryptoUpdateTxn.toBuilder()
				.setCryptoUpdateAccount(cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(key))
				.build();

		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	private void thenWillFailWith(ResponseCodeEnum code) {
		assertFailsWith(() -> subject.doStateTransition(), code);
		verify(target, never()).updateFromGrpc(any(), any(), any(), any(), any(), any(), any());
		verify(accountStore, never()).persistAccount(any());
		verify(transactionRecordService, never()).includeChangesToAccount(any());
	}

	private void givenLoadedAccount() {
		given(accountStore.loadPossiblyDetachedAccount(Id.fromGrpcAccount(TARGET))).willReturn(target);
	}

	private void givenSmartContractAccount() {
		given(accountStore.loadPossiblyDetachedAccount(any())).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));
	}

	private void givenDeletedAccount() {
		given(accountStore.loadPossiblyDetachedAccount(any())).willThrow(new InvalidTransactionException(ACCOUNT_DELETED));
	}

	private void givenMissingAccount() {
		given(accountStore.loadPossiblyDetachedAccount(any())).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));
	}

	private void givenValidTxnCtx() {
		cryptoUpdateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoUpdateAccount(
						CryptoUpdateTransactionBody.newBuilder()
								.setMemo(StringValue.of(MEMO))
								.setProxyAccountID(PROXY)
								.setAccountIDToUpdate(TARGET)
								.setExpirationTime(Timestamp.newBuilder().setSeconds(CURRENT_EXPIRY).build())
								.setReceiverSigRequiredWrapper(BoolValue.of(true))
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
								.setKey(KEY)
								.setMaxAutomaticTokenAssociations(Int32Value.of(MAX_TOKEN_ASSOCIATIONS))
				).build();
		lenient().when(accessor.getTxn()).thenReturn(cryptoUpdateTxn);
		lenient().when(txnCtx.accessor()).thenReturn(accessor);
	}

	private void givenValidTxnCtxWithExpiry(final long expiry) {
		cryptoUpdateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoUpdateAccount(
						CryptoUpdateTransactionBody.newBuilder()
								.setAccountIDToUpdate(TARGET)
								.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry).build())
				).build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private void givenValidTxnCtxWithMaxAutomaticAssociations(final int maxValue) {
		cryptoUpdateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoUpdateAccount(
						CryptoUpdateTransactionBody.newBuilder()
								.setAccountIDToUpdate(TARGET)
								.setMaxAutomaticTokenAssociations(Int32Value.of(maxValue))
				).build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
				.build();
	}

	private void givenHappyPathValidator() {
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);
	}
}
