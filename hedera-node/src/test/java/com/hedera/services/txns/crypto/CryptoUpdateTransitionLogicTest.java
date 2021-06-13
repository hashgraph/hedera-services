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
import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.AccountCustomizer;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.EXPIRY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.KEY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MEMO;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.PROXY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

class CryptoUpdateTransitionLogicTest {
	final private Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
	final private long curExpiry = consensusTime.getEpochSecond() + 2L;
	final private long newExpiry = consensusTime.getEpochSecond() + 7776000L;

	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private long autoRenewPeriod = 100_001L;
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID target = AccountID.newBuilder().setAccountNum(9_999L).build();

	private String memo = "Not since life began";
	private boolean useLegacyFields;
	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoUpdateTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private CryptoUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		useLegacyFields = false;

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		withRubberstampingValidator();

		subject = new CryptoUpdateTransitionLogic(ledger, validator, txnCtx);
	}

	@Test
	void updatesProxyIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(PROXY));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(EntityId.fromGrpcAccountId(proxy), changes.get(AccountProperty.PROXY));
	}


	@Test
	void updatesReceiverSigReqIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(true, changes.get(AccountProperty.IS_RECEIVER_SIG_REQUIRED));
	}

	@Test
	void updatesReceiverSigReqIfTrueInLegacy() {
		// setup:
		useLegacyFields = true;
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(true, changes.get(AccountProperty.IS_RECEIVER_SIG_REQUIRED));
	}

	@Test
	void updatesExpiryIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(EXPIRY));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(newExpiry, (long)changes.get(AccountProperty.EXPIRY));
	}

	@Test
	void updatesMemoIfPresent() throws Throwable {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(MEMO));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(memo, changes.get(AccountProperty.MEMO));
	}

	@Test
	void updatesAutoRenewIfPresent() throws Throwable {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(AUTO_RENEW_PERIOD));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(autoRenewPeriod, changes.get(AccountProperty.AUTO_RENEW_PERIOD));
	}

	@Test
	void updatesKeyIfPresent() throws Throwable {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenTxnCtx(EnumSet.of(KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(key, JKey.mapJKey((JKey)changes.get(AccountProperty.KEY)));
	}

	@Test
	void hasCorrectApplicability() {
		givenTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(cryptoUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
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
		givenTxnCtx(EnumSet.of(MEMO));
		given(validator.memoCheck(memo)).willReturn(MEMO_TOO_LONG);

		// expect:
		assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() {
		givenTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void acceptsValidTxn() {
		givenTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	@Test
	void rejectsDetachedAccount() {
		givenTxnCtx();
		given(ledger.isDetached(target)).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void rejectsInvalidExpiry() {
		givenTxnCtx();
		given(validator.isValidExpiry(any())).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
	}

	@Test
	void permitsDetachedIfOnlyExtendingExpiry() {
		givenTxnCtx(EnumSet.of(EXPIRY));
		given(ledger.isDetached(target)).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void rejectsInvalidExpiryForDetached() {
		givenTxnCtx(EnumSet.of(EXPIRY), EnumSet.of(EXPIRY));
		given(ledger.isDetached(target)).willReturn(true);
		given(ledger.expiry(target)).willReturn(curExpiry);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
	}

	@Test
	void rejectsSmartContract() {
		givenTxnCtx();
		given(ledger.isSmartContract(target)).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void preemptsMissingAccountException() {
		givenTxnCtx();
		given(ledger.exists(target)).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesMissingAccountException() {
		givenTxnCtx();
		willThrow(MissingAccountException.class).given(ledger).customize(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesAccountIsDeletedException() {
		givenTxnCtx();
		willThrow(DeletedAccountException.class).given(ledger).customize(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(ACCOUNT_DELETED);
	}

	@Test
	void translatesUnknownException() {
		givenTxnCtx();
		cryptoUpdateTxn = cryptoUpdateTxn.toBuilder()
				.setCryptoUpdateAccount(cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(unmappableKey()))
				.build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);


		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
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

	private void rejectsKey(Key key) {
		givenTxnCtx();
		cryptoUpdateTxn = cryptoUpdateTxn.toBuilder()
				.setCryptoUpdateAccount(cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(key))
				.build();

		// expect:
		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoUpdateTxn));
	}

	private void givenTxnCtx() {
		givenTxnCtx(EnumSet.of(
				KEY,
				MEMO,
				PROXY,
				EXPIRY,
				IS_RECEIVER_SIG_REQUIRED,
				AUTO_RENEW_PERIOD
		), EnumSet.noneOf(AccountCustomizer.Option.class));
	}

	private void givenTxnCtx(
			EnumSet<AccountCustomizer.Option> updating
	) {
		givenTxnCtx(updating, EnumSet.noneOf(AccountCustomizer.Option.class));
	}

	private void givenTxnCtx(
			EnumSet<AccountCustomizer.Option> updating,
			EnumSet<AccountCustomizer.Option> misconfiguring
	) {
		CryptoUpdateTransactionBody.Builder op = CryptoUpdateTransactionBody.newBuilder();
		if (updating.contains(MEMO)) {
			op.setMemo(StringValue.newBuilder().setValue(memo).build());
		}
		if (updating.contains(KEY)) {
			op.setKey(key);
		}
		if (updating.contains(PROXY)) {
			op.setProxyAccountID(proxy);
		}
		if (updating.contains(EXPIRY)) {
			if (misconfiguring.contains(EXPIRY)) {
				op.setExpirationTime(Timestamp.newBuilder().setSeconds(curExpiry - 1));
			} else {
				op.setExpirationTime(Timestamp.newBuilder().setSeconds(newExpiry));
			}
		}
		if (updating.contains(IS_RECEIVER_SIG_REQUIRED)) {
			if (!useLegacyFields) {
				op.setReceiverSigRequiredWrapper(BoolValue.newBuilder().setValue(true));
			} else {
				op.setReceiverSigRequired(true);
			}
		}
		if (updating.contains(AUTO_RENEW_PERIOD)) {
			op.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod));
		}
		op.setAccountIDToUpdate(target);
		cryptoUpdateTxn = TransactionBody.newBuilder().setTransactionID(ourTxnId()).setCryptoUpdateAccount(op).build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(ledger.exists(target)).willReturn(true);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);
	}
}
