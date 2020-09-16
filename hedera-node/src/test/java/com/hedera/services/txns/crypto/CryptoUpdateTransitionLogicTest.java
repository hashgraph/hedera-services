package com.hedera.services.txns.crypto;

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

import com.google.protobuf.BoolValue;
import com.google.protobuf.UInt64Value;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.accounts.AccountCustomizer;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.properties.AccountProperty;
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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.*;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
public class CryptoUpdateTransitionLogicTest {
	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private long autoRenewPeriod = 100_001L;
	final private long sendThresh = 49_000L;
	final private long receiveThresh = 51_001L;
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID target = AccountID.newBuilder().setAccountNum(9_999L).build();

	private long expiry;
	private boolean useLegacyFields;
	private Instant consensusTime;
	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoUpdateTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private CryptoUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();
		expiry = consensusTime.getEpochSecond() + 100_000L;
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
	public void updatesProxyIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(PROXY));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(EntityId.ofNullableAccountId(proxy), changes.get(AccountProperty.PROXY));
	}

	@Test
	public void updatesReceiveThreshIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(FUNDS_RECEIVED_RECORD_THRESHOLD));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(receiveThresh, (long) changes.get(AccountProperty.FUNDS_RECEIVED_RECORD_THRESHOLD));
	}

	@Test
	public void updatesReceiveThreshIfNonnegativeLegacyPresent() {
		// setup:
		useLegacyFields = true;
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(FUNDS_RECEIVED_RECORD_THRESHOLD));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(receiveThresh, (long) changes.get(AccountProperty.FUNDS_RECEIVED_RECORD_THRESHOLD));
	}

	@Test
	public void updatesReceiverSigReqIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

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
	public void updatesReceiverSigReqIfTrueInLegacy() {
		// setup:
		useLegacyFields = true;
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(IS_RECEIVER_SIG_REQUIRED));

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
	public void updatesSendThreshIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(FUNDS_SENT_RECORD_THRESHOLD));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(sendThresh, (long) changes.get(AccountProperty.FUNDS_SENT_RECORD_THRESHOLD));
	}

	@Test
	public void updatesExpiryIfPresent() {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(EXPIRY));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(expiry, (long) changes.get(AccountProperty.EXPIRY));
	}

	@Test
	public void updatesSendThreshIfNonnegativeLegacyPresent() {
		// setup:
		useLegacyFields = true;
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(FUNDS_SENT_RECORD_THRESHOLD));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(sendThresh, (long) changes.get(AccountProperty.FUNDS_SENT_RECORD_THRESHOLD));
	}

	@Test
	public void updatesAutoRenewIfPresent() throws Throwable {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(AUTO_RENEW_PERIOD));

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
	public void updatesKeyIfPresent() throws Throwable {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);

		givenValidTxnCtx(EnumSet.of(KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(argThat(target::equals), captor.capture());
		// and:
		EnumMap<AccountProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(1, changes.size());
		assertEquals(key, JKey.mapJKey((JKey) changes.get(AccountProperty.KEY)));
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(cryptoUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void rejectsKeyWithBadEncoding() {
		rejectsKey(unmappableKey());
	}

	@Test
	public void rejectsInvalidKey() {
		rejectsKey(emptyKey());
	}

	@Test
	public void rejectsInvalidExpiry() {
		givenValidTxnCtx();
		given(validator.isValidExpiry(any())).willReturn(false);

		// expect:
		assertEquals(INVALID_EXPIRATION_TIME, subject.syntaxCheck().apply(cryptoUpdateTxn));
	}

	@Test
	public void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.syntaxCheck().apply(cryptoUpdateTxn));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(cryptoUpdateTxn));
	}

	@Test
	public void translatesMissingAccountException() {
		givenValidTxnCtx();
		willThrow(MissingAccountException.class).given(ledger).customize(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	public void translatesAccountIsDeletedException() {
		givenValidTxnCtx();
		willThrow(DeletedAccountException.class).given(ledger).customize(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(ACCOUNT_DELETED);
	}

	@Test
	public void translatesUnknownException() {
		givenValidTxnCtx();
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
		givenValidTxnCtx();
		cryptoUpdateTxn = cryptoUpdateTxn.toBuilder()
				.setCryptoUpdateAccount(cryptoUpdateTxn.getCryptoUpdateAccount().toBuilder().setKey(key))
				.build();

		// expect:
		assertEquals(BAD_ENCODING, subject.syntaxCheck().apply(cryptoUpdateTxn));
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(EnumSet.of(
				KEY,
				PROXY,
				EXPIRY,
				IS_RECEIVER_SIG_REQUIRED,
				AUTO_RENEW_PERIOD,
				FUNDS_SENT_RECORD_THRESHOLD,
				FUNDS_RECEIVED_RECORD_THRESHOLD
		));
	}

	private void givenValidTxnCtx(EnumSet<AccountCustomizer.Option> updating) {
		CryptoUpdateTransactionBody.Builder op = CryptoUpdateTransactionBody.newBuilder();
		if (updating.contains(KEY)) {
			op.setKey(key);
		}
		if (updating.contains(PROXY)) {
			op.setProxyAccountID(proxy);
		}
		if (updating.contains(EXPIRY)) {
			op.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry));
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
		if (updating.contains(FUNDS_SENT_RECORD_THRESHOLD)) {
			if (!useLegacyFields) {
				op.setSendRecordThresholdWrapper(UInt64Value.newBuilder().setValue(sendThresh));
			} else {
				op.setSendRecordThreshold(sendThresh);
			}
		}
		if (updating.contains(FUNDS_RECEIVED_RECORD_THRESHOLD)) {
			if (!useLegacyFields) {
				op.setReceiveRecordThresholdWrapper(UInt64Value.newBuilder().setValue(receiveThresh));
			} else {
				op.setReceiveRecordThreshold(receiveThresh);
			}
		}
		op.setAccountIDToUpdate(target);
		cryptoUpdateTxn = TransactionBody.newBuilder().setTransactionID(ourTxnId()).setCryptoUpdateAccount(op).build();
		given(accessor.getTxn()).willReturn(cryptoUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
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
	}
}
