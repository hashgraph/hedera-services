/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.txns.consensus;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicCreateTransitionLogicTest {
    private static final long VALID_AUTORENEW_PERIOD_SECONDS = 30 * 86400L;
    private static final long INVALID_AUTORENEW_PERIOD_SECONDS = -1L;
    private static final String TOO_LONG_MEMO = "too-long";
    private static final String VALID_MEMO = "memo";
    private static final TopicID NEW_TOPIC_ID = asTopic("7.6.54321");

    // key to be used as a valid admin or submit key.
    private static final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private static final AccountID payer = AccountID.newBuilder().setAccountNum(2_345L).build();
    private static final Instant consensusTimestamp = Instant.ofEpochSecond(1546304463);
    private TransactionBody transactionBody;

    private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
    private MerkleMap<EntityNum, MerkleTopic> topics = new MerkleMap<>();

    @Mock private UsageLimits usageLimits;
    @Mock private TransactionContext transactionContext;
    @Mock private SignedTxnAccessor accessor;
    @Mock private OptionValidator validator;
    @Mock private EntityIdSource entityIdSource;
    @Mock private TopicStore topicStore;
    @Mock private AccountStore accountStore;
    @Mock private Account autoRenew;
    @Mock private SigImpactHistorian sigImpactHistorian;

    private TopicCreateTransitionLogic subject;

    @BeforeEach
    void setup() {
        accounts.clear();
        topics.clear();

        subject =
                new TopicCreateTransitionLogic(
                        usageLimits,
                        topicStore,
                        entityIdSource,
                        validator,
                        sigImpactHistorian,
                        transactionContext,
                        accountStore);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTransactionWithAllOptions();

        assertTrue(subject.applicability().test(transactionBody));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void syntaxCheckWithAdminKey() {
        givenValidTransactionWithAllOptions();
        given(validator.hasGoodEncoding(key)).willReturn(true);

        assertEquals(OK, subject.semanticCheck().apply(transactionBody));
    }

    @Test
    void syntaxCheckWithInvalidAdminKey() {
        givenValidTransactionWithAllOptions();
        given(validator.hasGoodEncoding(key)).willReturn(false);

        assertEquals(BAD_ENCODING, subject.semanticCheck().apply(transactionBody));
    }

    @Test
    void followsHappyPath() {
        givenValidTransactionWithAllOptions();
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(accountStore.loadAccountOrFailWith(any(), any())).willReturn(autoRenew);
        given(autoRenew.isSmartContract()).willReturn(false);
        given(
                        validator.isValidAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(VALID_AUTORENEW_PERIOD_SECONDS)
                                        .build()))
                .willReturn(true);
        given(transactionContext.consensusTime()).willReturn(consensusTimestamp);
        given(entityIdSource.newTopicId(any())).willReturn(NEW_TOPIC_ID);

        subject.doStateTransition();

        verify(topicStore).persistNew(any());
        verify(usageLimits).assertCreatableTopics(1);
        verify(sigImpactHistorian).markEntityChanged(NEW_TOPIC_ID.getTopicNum());
        verify(usageLimits).refreshTopics();
    }

    @Test
    void memoTooLong() {
        givenTransactionWithTooLongMemo();
        given(validator.memoCheck(anyString())).willReturn(MEMO_TOO_LONG);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);

        assertFailsWith(() -> subject.doStateTransition(), MEMO_TOO_LONG);

        assertTrue(topics.isEmpty());
    }

    @Test
    void badSubmitKey() {
        givenTransactionWithInvalidSubmitKey();
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(validator.attemptDecodeOrThrow(any()))
                .willThrow(new InvalidTransactionException(BAD_ENCODING));

        assertFailsWith(() -> subject.doStateTransition(), BAD_ENCODING);

        assertTrue(topics.isEmpty());
    }

    @Test
    void missingAutoRenewPeriod() {
        givenTransactionWithMissingAutoRenewPeriod();
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);

        assertFailsWith(() -> subject.doStateTransition(), INVALID_RENEWAL_PERIOD);

        assertTrue(topics.isEmpty());
    }

    @Test
    void badAutoRenewPeriod() {
        givenTransactionWithInvalidAutoRenewPeriod();
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);

        assertFailsWith(() -> subject.doStateTransition(), AUTORENEW_DURATION_NOT_IN_RANGE);

        assertTrue(topics.isEmpty());
    }

    @Test
    void invalidAutoRenewAccountId() {
        givenTransactionWithInvalidAutoRenewAccountId();
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(accountStore.loadAccountOrFailWith(any(), any()))
                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));
        given(
                        validator.isValidAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(VALID_AUTORENEW_PERIOD_SECONDS)
                                        .build()))
                .willReturn(true);

        assertFailsWith(() -> subject.doStateTransition(), INVALID_AUTORENEW_ACCOUNT);

        assertTrue(topics.isEmpty());
    }

    @Test
    void detachedAutoRenewAccountId() {
        givenTransactionWithDetachedAutoRenewAccountId();
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(
                        validator.isValidAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(VALID_AUTORENEW_PERIOD_SECONDS)
                                        .build()))
                .willReturn(true);
        given(accountStore.loadAccountOrFailWith(any(), any()))
                .willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));

        assertFailsWith(() -> subject.doStateTransition(), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        assertTrue(topics.isEmpty());
    }

    @Test
    void autoRenewAccountNotAllowed() {
        givenTransactionWithAutoRenewAccountWithoutAdminKey();
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(transactionContext.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(
                        validator.isValidAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(VALID_AUTORENEW_PERIOD_SECONDS)
                                        .build()))
                .willReturn(true);
        given(accountStore.loadAccountOrFailWith(any(), any())).willReturn(autoRenew);
        given(autoRenew.isSmartContract()).willReturn(false);

        assertFailsWith(() -> subject.doStateTransition(), AUTORENEW_ACCOUNT_NOT_ALLOWED);

        assertTrue(topics.isEmpty());
    }

    private void givenTransaction(final ConsensusCreateTopicTransactionBody.Builder body) {
        final var txnId =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(
                                Timestamp.newBuilder()
                                        .setSeconds(consensusTimestamp.getEpochSecond()));
        transactionBody =
                TransactionBody.newBuilder()
                        .setTransactionID(txnId)
                        .setConsensusCreateTopic(body)
                        .build();
    }

    private ConsensusCreateTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
        return ConsensusCreateTopicTransactionBody.newBuilder()
                .setAutoRenewPeriod(
                        Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build());
    }

    private void givenValidTransactionWithAllOptions() {
        givenTransaction(
                getBasicValidTransactionBodyBuilder()
                        .setMemo(VALID_MEMO)
                        .setAdminKey(key)
                        .setSubmitKey(key)
                        .setAutoRenewAccount(MISC_ACCOUNT));
    }

    private void givenTransactionWithTooLongMemo() {
        givenTransaction(getBasicValidTransactionBodyBuilder().setMemo(TOO_LONG_MEMO));
    }

    private void givenTransactionWithInvalidSubmitKey() {
        givenTransaction(
                getBasicValidTransactionBodyBuilder().setSubmitKey(Key.getDefaultInstance()));
    }

    private void givenTransactionWithInvalidAutoRenewPeriod() {
        givenTransaction(
                ConsensusCreateTopicTransactionBody.newBuilder()
                        .setAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(INVALID_AUTORENEW_PERIOD_SECONDS)
                                        .build()));
    }

    private void givenTransactionWithMissingAutoRenewPeriod() {
        givenTransaction(ConsensusCreateTopicTransactionBody.newBuilder());
    }

    private void givenTransactionWithInvalidAutoRenewAccountId() {
        givenTransaction(getBasicValidTransactionBodyBuilder().setAutoRenewAccount(MISC_ACCOUNT));
    }

    private void givenTransactionWithDetachedAutoRenewAccountId() {
        givenTransaction(getBasicValidTransactionBodyBuilder().setAutoRenewAccount(MISC_ACCOUNT));
    }

    private void givenTransactionWithAutoRenewAccountWithoutAdminKey() {
        givenTransaction(getBasicValidTransactionBodyBuilder().setAutoRenewAccount(MISC_ACCOUNT));
    }
}
