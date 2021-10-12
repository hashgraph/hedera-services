/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.txns.consensus;

import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISSING_TOPIC;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TopicUpdateTransitionLogicTest {

	private static final long VALID_AUTORENEW_PERIOD_SECONDS = 30 * 86400L;
	private static final long INVALID_AUTORENEW_PERIOD_SECONDS = -1L;
	private static final long NOW_SECONDS = 1546304462;
	private static final RichInstant EXISTING_EXPIRATION_TIME = new RichInstant(NOW_SECONDS + 1000L, 0);
	private final Instant consensusTime = Instant.ofEpochSecond(NOW_SECONDS);
	private final Instant updatedExpirationTime = Instant.ofEpochSecond(EXISTING_EXPIRATION_TIME.getSeconds()).plusSeconds(1000);
	private static final TopicID TOPIC_ID = asTopic("9.8.7");
	private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	private final Key updatedAdminKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private final Key updatedSubmitKey = MISC_ACCOUNT_KT.asKey();
	private static final String VALID_MEMO = "updated memo";
	private static final String TOO_LONG_MEMO = "too-long";

	@Mock private PlatformTxnAccessor accessor;
	@Mock private TransactionContext txnCtx;
	@Mock private TransactionBody transactionBody;
	@Mock private OptionValidator validator;
	@Mock private AccountStore accountStore;
	@Mock private TopicStore topicStore;
	@Mock private Topic topic;
	@Mock private Account newAutoRenew;

	private TopicUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new TopicUpdateTransitionLogic(validator, txnCtx, accountStore, topicStore);
	}

	@Test
	void hasCorrectApplicability() {
		// given:
		givenValidTransactionWithAllOptions();
		// then:
		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void syntaxCheckWithAdminKey() {
		// given:
		givenValidTransactionWithAllOptions();
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(true);
		// then:
		assertEquals(OK, subject.semanticCheck().apply(transactionBody));
	}

	@Test
	void syntaxCheckWithInvalidAdminKey() {
		// given:
		givenValidTransactionWithAllOptions();
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(false);
		// then:
		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(transactionBody));
	}

	@Test
	void clearsAutoRenewAccount() {
		givenTransactionClearingAutoRenewAccount();
		givenTopicWithAdminKey(true);

		thenWillSuccessfullyUpdateTopic();
	}

	@Test
	void followHappyPath() {
		givenValidTransactionWithAllOptions();
		givenTopicWithAdminKey(true);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(true);
		given(validator.memoCheck(VALID_MEMO)).willReturn(OK);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(topic.hasAutoRenewAccountId()).willReturn(false);
		given(accountStore.loadAccount(Id.fromGrpcAccount(MISC_ACCOUNT))).willReturn(newAutoRenew);

		thenWillSuccessfullyUpdateTopic();
	}

	@Test
	void clearsKeysIfRequested() {
		givenTransactionClearingKeys();
		givenTopicWithAdminKey(true);
		given(validator.hasGoodEncoding(any())).willReturn(true);

		thenWillSuccessfullyUpdateTopic();
	}

	@Test
	void failsOnInvalidTopic() {
		givenValidTransactionInvalidTopic();
		given(topicStore.loadTopic(any())).willThrow(new InvalidTransactionException(INVALID_TOPIC_ID));

		thenWillFailWith(INVALID_TOPIC_ID);
	}

	@Test
	void failsUnauthorizedOnMemoChange() {
		givenTransactionWithMemo();
		givenTopicWithAdminKey(false);

		thenWillFailWith(UNAUTHORIZED);
	}

	@Test
	void failsOnInvalidSubmitKey() {
		givenTransactionWithInvalidSubmitKey();
		givenTopicWithAdminKey(true);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(false);

		thenWillFailWith(BAD_ENCODING);
	}

	@Test
	void failsOnInvalidMemo() {
		givenTransactionWithInvalidMemo();
		givenTopicWithAdminKey(true);
		given(validator.memoCheck(TOO_LONG_MEMO)).willReturn(MEMO_TOO_LONG);

		thenWillFailWith(MEMO_TOO_LONG);
	}

	@Test
	void failsOnInvalidExpirationTime() {
		givenTransactionWithInvalidExpirationTime();
		givenTopicWithAdminKey(true);
		given(validator.isValidExpiry(any())).willReturn(false);

		thenWillFailWith(INVALID_EXPIRATION_TIME);
	}

	@Test
	void failsOnInvalidAutoRenewPeriod() {
		givenTransactionWithInvalidAutoRenewPeriod();
		givenTopicWithAdminKey(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		thenWillFailWith(AUTORENEW_DURATION_NOT_IN_RANGE);
	}

	@Test
	void failsOnDetachedExistingAutoRenewAccount() {
		givenValidTransactionWithAllOptions();
		givenTopicWithAdminKey(true);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(true);
		given(validator.memoCheck(VALID_MEMO)).willReturn(OK);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(topic.hasAutoRenewAccountId()).willReturn(true);
		given(accountStore.loadAccount(any()))
				.willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));

		thenWillFailWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	/* ----- Test scenarios ----- */
	private void givenValidTransactionWithAllOptions() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(VALID_MEMO))
						.setAdminKey(updatedAdminKey)
						.setSubmitKey(updatedSubmitKey)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build())
						.setAutoRenewAccount(MISC_ACCOUNT)
						.setExpirationTime(Timestamp.newBuilder().setSeconds(updatedExpirationTime.getEpochSecond()))
		);
	}

	private void givenTransactionClearingKeys() {
		var clearKey = Key.newBuilder().setKeyList(KeyList.getDefaultInstance());
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(clearKey)
						.setSubmitKey(clearKey)
		);
	}

	private void givenTransactionWithInvalidMemo() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(TOO_LONG_MEMO))
		);
	}

	private void givenTransactionWithInvalidSubmitKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setSubmitKey(updatedSubmitKey)
		);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(false);
	}

	private void givenTransactionWithInvalidAutoRenewPeriod() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(INVALID_AUTORENEW_PERIOD_SECONDS))
		);
	}

	private void givenTransactionWithInvalidExpirationTime() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setExpirationTime(Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond() - 1L))
		);
		given(validator.isValidExpiry(any())).willReturn(false);
	}

	private void givenTransactionWithMemo() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(VALID_MEMO))
		);
	}

	private void givenValidTransactionInvalidTopic() {
		givenTransaction(
				ConsensusUpdateTopicTransactionBody.newBuilder()
						.setTopicID(MISSING_TOPIC)
						.setMemo(StringValue.of(VALID_MEMO))
		);
	}

	private void givenTransactionClearingAutoRenewAccount() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(
								AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(0).build())
		);
	}

	private void givenTransaction(ConsensusUpdateTopicTransactionBody.Builder body) {
		transactionBody = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setConsensusUpdateTopic(body.build())
				.build();

		lenient().when(accessor.getTxn()).thenReturn(transactionBody);
		lenient().when(txnCtx.accessor()).thenReturn(accessor);
	}

	private ConsensusUpdateTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
		return ConsensusUpdateTopicTransactionBody.newBuilder()
				.setTopicID(TOPIC_ID);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void givenTopicWithAdminKey(final boolean flag) {
		given(topicStore.loadTopic(Id.fromGrpcTopic(TOPIC_ID))).willReturn(topic);
		given(topic.hasAdminKey()).willReturn(flag);
	}

	private void thenWillSuccessfullyUpdateTopic() {
		subject.doStateTransition();
		verify(topic).update(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
		verify(topicStore).persistTopic(any());
	}

	private void thenWillFailWith(final ResponseCodeEnum failCode) {
		TxnUtils.assertFailsWith(() -> subject.doStateTransition(), failCode);
		verify(topic, never()).update(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
		verify(topicStore, never()).persistTopic(any());
	}

}