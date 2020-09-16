package com.hedera.services.txns.consensus;

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

import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.*;
import static com.hedera.test.utils.IdUtils.asTopic;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class MerkleTopicUpdateTransitionLogicTest {
	final private Key updatedAdminKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private Key updatedSubmitKey = MISC_ACCOUNT_KT.asKey();
	final private Key existingKey = MISC_ACCOUNT_KT.asKey();

	private static final long VALID_AUTORENEW_PERIOD_SECONDS = 30 * 86400L;
	private static final long INVALID_AUTORENEW_PERIOD_SECONDS = -1L;
	private static final long EXISTING_AUTORENEW_PERIOD_SECONDS = 29 * 86400L;
	private static final long NOW_SECONDS = 1546304462;
	private static final RichInstant EXISTING_EXPIRATION_TIME = new RichInstant(NOW_SECONDS + 1000L, 0);
	private static final String TOO_LONG_MEMO = "too-long";
	private static final String VALID_MEMO = "updated memo";
	private static final String EXISTING_MEMO = "unmodified memo";
	private static final TopicID TOPIC_ID = asTopic("9.8.7");

	private Instant consensusTime;
	private Instant updatedExpirationTime;
	private TransactionBody transactionBody;
	private TransactionContext transactionContext;
	private PlatformTxnAccessor accessor;
	private OptionValidator validator;
	private FCMap<MerkleEntityId, MerkleAccount> accounts =
			new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
	private FCMap<MerkleEntityId, MerkleTopic> topics = new FCMap<>(new MerkleEntityId.Provider(), new MerkleTopic.Provider());
	private TopicUpdateTransitionLogic subject;
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

	@BeforeEach
	private void setup() {
		consensusTime = Instant.ofEpochSecond(NOW_SECONDS);
		updatedExpirationTime = Instant.ofEpochSecond(EXISTING_EXPIRATION_TIME.getSeconds()).plusSeconds(1000);

		transactionContext = mock(TransactionContext.class);
		given(transactionContext.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		given(validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(true);
		given(validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(INVALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(false);
		given(validator.isValidEntityMemo("")).willReturn(true);
		given(validator.isValidEntityMemo(VALID_MEMO)).willReturn(true);
		given(validator.isValidEntityMemo(TOO_LONG_MEMO)).willReturn(false);

		subject = new TopicUpdateTransitionLogic(() -> accounts, () -> topics, validator, transactionContext);
	}

	@Test
	public void hasCorrectApplicability() {
		// given:
		givenValidTransactionWithAllOptions();

		// expect:
		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void syntaxCheckWithAdminKey() {
		// given:
		givenValidTransactionWithAllOptions();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(transactionBody));
	}

	@Test
	public void syntaxCheckWithInvalidAdminKey() {
		// given:
		givenValidTransactionWithAllOptions();
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(false);

		// expect:
		assertEquals(BAD_ENCODING, subject.syntaxCheck().apply(transactionBody));
	}

	@Test
	public void followsHappyPath() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenValidTransactionWithAllOptions();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		assertNotNull(topic);
		verify(transactionContext).setStatus(SUCCESS);
		assertEquals(VALID_MEMO, topic.getMemo());
		assertArrayEquals(JKey.mapKey(updatedAdminKey).serialize(), topic.getAdminKey().serialize());
		assertArrayEquals(JKey.mapKey(updatedSubmitKey).serialize(), topic.getSubmitKey().serialize());
		assertEquals(VALID_AUTORENEW_PERIOD_SECONDS, topic.getAutoRenewDurationSeconds());
		assertEquals(EntityId.ofNullableAccountId(MISC_ACCOUNT), topic.getAutoRenewAccountId());
		assertEquals(updatedExpirationTime.getEpochSecond(), topic.getExpirationTimestamp().getSeconds());
	}

	@Test
	public void clearsKeysIfRequested() throws Throwable {
		// given:
		givenExistingTopicWithBothKeys();
		givenTransactionClearingKeys();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		assertNotNull(topic);
		verify(transactionContext).setStatus(SUCCESS);
		assertEquals(EXISTING_MEMO, topic.getMemo());
		assertFalse(topic.hasAdminKey());
		assertFalse(topic.hasSubmitKey());
		assertEquals(EXISTING_AUTORENEW_PERIOD_SECONDS, topic.getAutoRenewDurationSeconds());
		assertFalse(topic.hasAutoRenewAccountId());
		assertEquals(EXISTING_EXPIRATION_TIME, topic.getExpirationTimestamp());
	}

	@Test
	public void failsOnInvalidMemo() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidMemo();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(MEMO_TOO_LONG);
	}

	@Test
	public void failsOnInvalidAdminKey() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidAdminKey();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(BAD_ENCODING);
	}

	@Test
	public void failsOnInvalidSubmitKey() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidSubmitKey();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(BAD_ENCODING);
	}

	@Test
	public void failsOnInvalidAutoRenewPeriod() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidAutoRenewPeriod();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(AUTORENEW_DURATION_NOT_IN_RANGE);
	}

	@Test
	public void failsOnInvalidExpirationTime() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidExpirationTime();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(INVALID_EXPIRATION_TIME);
	}

	@Test
	public void failsOnExpirationTimeReduction() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithReducedExpirationTime();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
	}

	@Test
	public void failsUnauthorizedOnMemoChange() throws Throwable {
		// given:
		givenExistingTopicWithoutAdminKey();
		givenTransactionWithMemo();

		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(UNAUTHORIZED);
	}

	@Test
	public void failsOnInvalidTopic() throws Throwable {
		// given:
		givenValidTransactionInvalidTopic();

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(INVALID_TOPIC_ID);
	}

	@Test
	public void failsOnInvalidAutoRenewAccount() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidAutoRenewAccount();

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(INVALID_AUTORENEW_ACCOUNT);
	}

	@Test
	public void failsOnAutoRenewAccountNotAllowed() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithAutoRenewAccountClearingAdminKey();

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED);
	}

	@Test
	public void clearsAutoRenewAccount() throws Throwable {
		// given:
		givenExistingTopicWithAutoRenewAccount();
		givenTransactionClearingAutoRenewAccount();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		verify(transactionContext).setStatus(SUCCESS);
		assertFalse(topic.hasAutoRenewAccountId());
	}

	private void assertTopicNotUpdated(MerkleTopic originalMerkleTopic, MerkleTopic originalMerkleTopicClone) {
		var updatedTopic = topics.get(MerkleEntityId.fromTopicId(TOPIC_ID));
		assertSame(originalMerkleTopic, updatedTopic); // No change
		assertEquals(originalMerkleTopicClone, updatedTopic); // No change in values
	}

	private void givenExistingTopicWithAdminKey() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, JKey.mapKey(existingKey), null, EXISTING_AUTORENEW_PERIOD_SECONDS, null,
				EXISTING_EXPIRATION_TIME);
		topics.put(MerkleEntityId.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenExistingTopicWithBothKeys() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, JKey.mapKey(existingKey), JKey.mapKey(existingKey),
				EXISTING_AUTORENEW_PERIOD_SECONDS, null, EXISTING_EXPIRATION_TIME);
		topics.put(MerkleEntityId.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenExistingTopicWithoutAdminKey() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, null, null, EXISTING_AUTORENEW_PERIOD_SECONDS, null,
				EXISTING_EXPIRATION_TIME);
		topics.put(MerkleEntityId.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenExistingTopicWithAutoRenewAccount() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, JKey.mapKey(existingKey), null, EXISTING_AUTORENEW_PERIOD_SECONDS,
				EntityId.ofNullableAccountId(MISC_ACCOUNT), EXISTING_EXPIRATION_TIME);
		topics.put(MerkleEntityId.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenTransaction(ConsensusUpdateTopicTransactionBody.Builder body) {
		transactionBody = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setConsensusUpdateTopic(body.build())
				.build();
		given(accessor.getTxn()).willReturn(transactionBody);
		given(transactionContext.accessor()).willReturn(accessor);
	}

	private ConsensusUpdateTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
		return ConsensusUpdateTopicTransactionBody.newBuilder()
				.setTopicID(TOPIC_ID);
	}

	private void givenValidTransactionInvalidTopic() {
		givenTransaction(
				ConsensusUpdateTopicTransactionBody.newBuilder()
						.setTopicID(MISSING_TOPIC)
				.setMemo(StringValue.of(VALID_MEMO))
		);
		given(validator.queryableTopicStatus(MISSING_TOPIC, topics)).willReturn(INVALID_TOPIC_ID);
	}

	private void givenTransactionWithInvalidAutoRenewAccount() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(MISSING_ACCOUNT)
		);
		given(validator.queryableAccountStatus(MISSING_ACCOUNT, accounts)).willReturn(INVALID_ACCOUNT_ID);
	}

	private void givenTransactionWithAutoRenewAccountClearingAdminKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()))
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
	}

	private void givenTransactionClearingAutoRenewAccount() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(
								AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(0).build())
		);
	}

	private void givenTransactionClearingKeys() {
		var clearKey = Key.newBuilder().setKeyList(KeyList.getDefaultInstance());
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(clearKey)
						.setSubmitKey(clearKey)
		);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
	}

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
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(true);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(true);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
	}

	private void givenTransactionWithInvalidMemo() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(TOO_LONG_MEMO))
		);
	}

	private void givenTransactionWithMemo() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
				.setMemo(StringValue.of(VALID_MEMO))
		);
	}

	private void givenTransactionWithInvalidAdminKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(updatedAdminKey)
		);
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(false);
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

	private void givenTransactionWithReducedExpirationTime() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setExpirationTime(Timestamp.newBuilder().setSeconds(EXISTING_EXPIRATION_TIME.getSeconds() - 1L))
		);
		given(validator.isValidExpiry(any())).willReturn(true);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
