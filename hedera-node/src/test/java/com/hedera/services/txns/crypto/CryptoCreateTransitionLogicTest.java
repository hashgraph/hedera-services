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
import com.hedera.test.utils.TxnUtils;
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
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

	final private Long balance = 1_234L;
	final private int maxAutoAssociations = 1234;
	final private int maxTokenAssociations = 12345;
	final private Instant consensusTime = Instant.now();
	final private long customAutoRenewPeriod = 100_001L;
	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private String memo = "The particular is pounded til it is man";
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

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

	private CryptoCreateTransitionLogic subject;
	private TransactionBody cryptoCreateTxn;

	@BeforeEach
	private void setup() {
		subject = new CryptoCreateTransitionLogic(validator, txnCtx, accountStore, dynamicProperties, ids, transactionRecordService);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(cryptoCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void returnsMemoTooLongWhenValidatorSays() {
		givenValidTxnCtx();
		given(validator.memoCheck(memo)).willReturn(MEMO_TOO_LONG);

		// expect:
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

		// expect:
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

		// expect:
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

		// expect:
		assertEquals(INVALID_SEND_RECORD_THRESHOLD, subject.semanticCheck().apply(cryptoCreateTxn));
	}

	@Test
	void rejectsNegativeReceiveThreshold() {
		givenAbsurdReceiveThreshold();
		given(validator.memoCheck(any())).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		// expect:
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
		given(dynamicProperties.maxTokensPerAccount()).willReturn(maxTokenAssociations + 1);

		// expect:
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

		// when:
		subject.doStateTransition();

		// then:
		verify(accountStore).persistNew(any());
		verify(accountStore).persistAccount(any());
		verify(transactionRecordService).includeChangesToAccount(any());
		verify(transactionRecordService).includeHbarBalanceChanges(any());
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
								.setMemo(memo)
								.setInitialBalance(balance)
								.setProxyAccountID(proxy)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod))
								.setKey(key)
								.setMaxAutomaticTokenAssociations(maxAutoAssociations)
								.build()
				).build();
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(cryptoCreateTxn);

		given(accountStore.loadAccount(Id.DEFAULT)).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));

		TxnUtils.assertFailsWith(() -> subject.doStateTransition(), INVALID_ACCOUNT_ID);
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

		TxnUtils.assertFailsWith(() -> subject.doStateTransition(), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
		verify(accountStore, never()).persistNew(any());
		verify(accountStore, never()).persistAccount(any());
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

	private void givenInvalidMaxAutoAssociations() {
		long customSendThreshold = 49_000L;
		long customReceiveThreshold = 51_001L;
		int maxTokenAssociations = 12345;
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(memo)
								.setInitialBalance(balance)
								.setProxyAccountID(proxy)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod))
								.setReceiveRecordThreshold(customReceiveThreshold)
								.setSendRecordThreshold(customSendThreshold)
								.setKey(key)
								.setMaxAutomaticTokenAssociations(maxTokenAssociations + 1)
								.build()
				).build();
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(key);
	}

	private void givenValidTxnCtx(Key toUse) {
		cryptoCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoCreateAccount(
						CryptoCreateTransactionBody.newBuilder()
								.setMemo(memo)
								.setInitialBalance(balance)
								.setProxyAccountID(proxy)
								.setReceiverSigRequired(true)
								.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod))
								.setKey(toUse)
								.setMaxAutomaticTokenAssociations(maxAutoAssociations)
								.build()
				).build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
