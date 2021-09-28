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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
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
	private static final Instant consensusTime = Instant.now();

	@Mock
	private Account sponsor;
	@Mock
	private EntityIdSource ids;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private ContextOptionValidator validator;
	@Mock
	private AccountStore accountStore;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private TransactionRecordService transactionRecordService;
	@Mock
	private HederaLedger ledger;

	private CryptoCreateTransitionLogic subject;
	private TransactionBody cryptoCreateTxn;

	@BeforeEach
	private void setup() {
		subject = new CryptoCreateTransitionLogic(validator, txnCtx, accountStore, dynamicProperties, ids, transactionRecordService, ledger);
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
	void requiresKey() {
		givenMissingKey();
		given(validator.memoCheck(any())).willReturn(OK);

		// expect:
		assertEquals(KEY_REQUIRED, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void returnsKeyRequiredOnEmptyKey() {
		givenValidTxnCtx(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);

		assertEquals(KEY_REQUIRED, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsKeyWithBadEncoding() {
		givenValidTxnCtx();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(false);

		// expect:
		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsInvalidKey() {
		givenValidTxnCtx();
		final var thisKey = mock(JKey.class);
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		MockedStatic<MiscUtils> miscUtilsMockedStatic = mockStatic(MiscUtils.class);
		miscUtilsMockedStatic.when(() -> MiscUtils.asFcKeyUnchecked(any())).thenReturn(thisKey);
		given(thisKey.isValid()).willReturn(false);

		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(cryptoCreateTxn));
		miscUtilsMockedStatic.close();
	}

	@Test
	void rejectsNegativeBalance() {
		givenAbsurdInitialBalance();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);

		// expect:
		assertEquals(INVALID_INITIAL_BALANCE, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsMissingAutoRenewPeriod() {
		givenMissingAutoRenewPeriod();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);

		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsInvalidAutoRenewPeriod() {
		givenValidTxnCtx();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsNegativeSendThreshold() {
		givenAbsurdSendThreshold();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		assertEquals(INVALID_SEND_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsNegativeReceiveThreshold() {
		givenAbsurdReceiveThreshold();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		assertEquals(INVALID_RECEIVE_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsInvalidMaxAutomaticAssociations() {
		givenInvalidMaxAutoAssociations();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		assertEquals(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT,
				subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(dynamicProperties.maxTokensPerAccount()).willReturn(MAX_TOKEN_ASSOCIATIONS + 1);

		assertEquals(OK, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void followsHappyPathWithOverrides() {
		givenValidTxnCtx();

		given(sponsor.isSmartContract()).willReturn(false);
		given(accountStore.loadAccount(any())).willReturn(sponsor);
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		given(ids.newAccountId(any())).willReturn(mock(AccountID.class));
		given(sponsor.getId()).willReturn(mock(Id.class));
		given(sponsor.getBalance()).willReturn(100_000L);
		subject.doStateTransition();

		// then:
		verify(accountStore).persistNew(any());
		verify(accountStore).persistAccount(any());
		verify(transactionRecordService).includeChangesToAccount(any());
		verify(ledger).doZeroSum(any());
	}

	@Test
	void providedNonExistingSponsorId() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setAccountID(Id.DEFAULT.asGrpcAccount())
						.setTransactionValidStart(
								Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
						.build())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(MEMO)
								.setInitialBalance(BALANCE)
								.setProxyAccountID(PROXY)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(CUSTOM_AUTO_RENEW_PERIOD))
								.setKey(KEY)
								.setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
								.build()
				).build();
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);

		given(accountStore.loadAccount(Id.DEFAULT)).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_ACCOUNT_ID);
		verify(accountStore, never()).persistNew(any());
		verify(accountStore, never()).persistAccount(any());
	}

	@Test
	public void sponsorAccountIsSmartContract() {
		givenValidTxnCtx();

		given(accountStore.loadAccount(any())).willReturn(sponsor);
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(sponsor.isSmartContract()).willReturn(true);

		// then:
		assertFailsWith(
				() -> subject.doStateTransition(),
				INVALID_ACCOUNT_ID
		);
	}

	private void givenMissingKey() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setInitialBalance(BALANCE)
				).build();
	}

	private void givenMissingAutoRenewPeriod() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setKey(KEY)
								.setInitialBalance(BALANCE)
				).build();
	}

	private void givenAbsurdSendThreshold() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setSendRecordThreshold(-1L)
				).build();
	}

	private void givenAbsurdReceiveThreshold() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setReceiveRecordThreshold(-1L)
				).build();
	}

	private void givenAbsurdInitialBalance() {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1L))
								.setKey(KEY)
								.setInitialBalance(-1L)
				).build();
	}

	private void givenInvalidMaxAutoAssociations() {
		long customSendThreshold = 49_000L;
		long customReceiveThreshold = 51_001L;
		int maxTokenAssociations = 12345;
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
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(KEY);
	}

	private void givenValidTxnCtx(final Key toUse) {
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
								.setKey(toUse)
								.setMaxAutomaticTokenAssociations(MAX_AUTO_ASSOCIATIONS)
				).build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
