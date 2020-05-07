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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.properties.MapValueProperty;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumMap;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.ledger.properties.MapValueProperty.*;

@RunWith(JUnitPlatform.class)
public class CryptoCreateTransitionLogicTest {
	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private long customAutoRenewPeriod = 100_001L;
	final private long customSendThreshold = 49_000L;
	final private long customReceiveThreshold = 51_001L;
	final private Long balance = 1_234L;
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID created = AccountID.newBuilder().setAccountNum(9_999L).build();

	private long expiry;
	private Instant consensusTime;
	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoCreateTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private CryptoCreateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		withRubberstampingValidator();

		subject = new CryptoCreateTransitionLogic(ledger, validator, txnCtx);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(cryptoCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void requiresKey() {
		givenMissingKey();

		// expect:
		assertEquals(KEY_REQUIRED, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void rejectsMissingAutoRenewPeriod() {
		givenMissingAutoRenewPeriod();

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void rejectsNegativeBalance() {
		givenAbsurdInitialBalance();

		// expect:
		assertEquals(INVALID_INITIAL_BALANCE, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void rejectsNegativeSendThreshold() {
		givenAbsurdSendThreshold();

		// expect:
		assertEquals(INVALID_SEND_RECORD_THRESHOLD, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void rejectsNegativeReceiveThreshold() {
		givenAbsurdReceiveThreshold();

		// expect:
		assertEquals(INVALID_RECEIVE_RECORD_THRESHOLD, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void rejectsKeyWithBadEncoding() {
		givenValidTxnCtx();
		given(validator.hasGoodEncoding(any())).willReturn(false);

		// expect:
		assertEquals(BAD_ENCODING, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(cryptoCreateTxn));
	}

	@Test
	public void followsHappyPathWithOverrides() throws Throwable {
		// setup:
		ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		expiry = consensusTime.getEpochSecond() + customAutoRenewPeriod;

		givenValidTxnCtx();
		// and:
		given(ledger.create(any(), anyLong(), any())).willReturn(created);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).create(argThat(payer::equals), longThat(balance::equals), captor.capture());
		verify(txnCtx).setCreated(created);
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		EnumMap<MapValueProperty, Object> changes = captor.getValue().getChanges();
		assertEquals(7, changes.size());
		assertEquals(customAutoRenewPeriod, (long)changes.get(AUTO_RENEW_PERIOD));
		assertEquals(customSendThreshold, (long)changes.get(FUNDS_SENT_RECORD_THRESHOLD));
		assertEquals(customReceiveThreshold, (long)changes.get(FUNDS_RECEIVED_RECORD_THRESHOLD));
		assertEquals(expiry, (long)changes.get(EXPIRY));
		assertEquals(key, JKey.mapJKey((JKey)changes.get(KEY)));
		assertEquals(true, changes.get(IS_RECEIVER_SIG_REQUIRED));
		assertEquals(JAccountID.convert(proxy), changes.get(PROXY));
	}

	@Test
	public void translatesInsufficientPayerBalance() {
		givenValidTxnCtx();
		given(ledger.create(any(), anyLong(), any())).willThrow(InsufficientFundsException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INSUFFICIENT_PAYER_BALANCE);
	}

	@Test
	public void translatesUnknownException() {
		givenValidTxnCtx();
		cryptoCreateTxn = cryptoCreateTxn.toBuilder()
				.setCryptoCreateAccount(cryptoCreateTxn.getCryptoCreateAccount().toBuilder().setKey(unmappableKey()))
				.build();
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private Key unmappableKey() {
		return Key.getDefaultInstance();
	}

	private void givenMissingKey() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setInitialBalance(balance)
								.build()
				).build();
	}

	private void givenMissingAutoRenewPeriod() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setKey(key)
								.setInitialBalance(balance)
								.build()
				).build();
	}

	private void givenAbsurdSendThreshold() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(key)
								.setSendRecordThreshold(-1L)
								.build()
				).build();
	}

	private void givenAbsurdReceiveThreshold() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(key)
								.setReceiveRecordThreshold(-1L)
								.build()
				).build();
	}

	private void givenAbsurdInitialBalance() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(key)
								.setInitialBalance(-1L)
								.build()
				).build();
	}

	private void givenValidTxnCtx() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setInitialBalance(balance)
								.setProxyAccountID(proxy)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod))
								.setReceiveRecordThreshold(customReceiveThreshold)
								.setSendRecordThreshold(customSendThreshold)
								.setKey(key)
								.build()
				).build();
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);
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
		given(validator.hasGoodEncoding(any())).willReturn(true);
	}
}
