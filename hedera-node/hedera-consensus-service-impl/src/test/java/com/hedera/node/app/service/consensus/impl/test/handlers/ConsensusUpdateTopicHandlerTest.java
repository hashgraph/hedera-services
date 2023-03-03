/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertOkResponse;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.google.protobuf.StringValue;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.TemporaryUtils;
import com.hedera.node.app.service.consensus.impl.records.ConsensusUpdateTopicRecordBuilder;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusUpdateTopicHandlerTest extends ConsensusHandlerTestBase {

    private final ConsensusUpdateTopicTransactionBody.Builder OP_BUILDER =
            ConsensusUpdateTopicTransactionBody.newBuilder();

    private final ExpiryMeta currentExpiryMeta =
            new ExpiryMeta(expirationTime, autoRenewSecs, autoRenewId.getAccountNum());

    @Mock
    private HandleContext handleContext;

    @Mock
    private AccountAccess accountAccess;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private AttributeValidator attributeValidator;

    private ConsensusUpdateTopicHandler subject = new ConsensusUpdateTopicHandler();

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusUpdateTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    void rejectsMissingTopic() {
        final var op = OP_BUILDER.build();

        // expect:
        assertFailsWith(INVALID_TOPIC_ID, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void rejectsDeletedTopic() {
        givenValidTopic(0, true);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.setTopicID(wellKnownId()).build();

        // expect:
        assertFailsWith(INVALID_TOPIC_ID, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void rejectsNonExpiryMutationOfImmutableTopic() {
        givenValidTopic(0, false, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setMemo(StringValue.of("Please mind the vase"))
                .build();

        // expect:
        assertFailsWith(UNAUTHORIZED, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void validatesNewAdminKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.setTopicID(wellKnownId()).setAdminKey(key).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleStatusException(BAD_ENCODING))
                .given(attributeValidator)
                .validateKey(key);

        // expect:
        assertFailsWith(BAD_ENCODING, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void appliesNewAdminKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op =
                OP_BUILDER.setTopicID(wellKnownId()).setAdminKey(anotherKey).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        final var expectedKey = TemporaryUtils.fromGrpcKey(anotherKey);
        assertEquals(expectedKey, newTopic.adminKey());
    }

    @Test
    void validatesNewSubmitKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.setTopicID(wellKnownId()).setSubmitKey(key).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleStatusException(BAD_ENCODING))
                .given(attributeValidator)
                .validateKey(key);

        // expect:
        assertFailsWith(BAD_ENCODING, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void appliesNewSubmitKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op =
                OP_BUILDER.setTopicID(wellKnownId()).setSubmitKey(anotherKey).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        final var expectedKey = TemporaryUtils.fromGrpcKey(anotherKey);
        assertEquals(expectedKey, newTopic.submitKey());
    }

    @Test
    void validatesNewMemo() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setMemo(StringValue.of("Please mind the vase"))
                .build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleStatusException(MEMO_TOO_LONG))
                .given(attributeValidator)
                .validateMemo(op.getMemo().getValue());

        // expect:
        assertFailsWith(MEMO_TOO_LONG, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void appliesNewMemo() {
        final var newMemo = "Please mind the vase";
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setMemo(StringValue.of(newMemo))
                .build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(newMemo, newTopic.memo());
    }

    @Test
    void validatesNewExpiryViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setExpirationTime(Timestamp.newBuilder().setSeconds(123L))
                .build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(123L, NA, NA);
        willThrow(new HandleStatusException(INVALID_EXPIRATION_TIME))
                .given(expiryValidator)
                .resolveUpdateAttempt(currentExpiryMeta, impliedMeta);

        // expect:
        assertFailsWith(INVALID_EXPIRATION_TIME, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void appliesNewExpiryViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setExpirationTime(Timestamp.newBuilder().setSeconds(123L))
                .build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(123L, NA, NA);
        given(expiryValidator.resolveUpdateAttempt(currentExpiryMeta, impliedMeta))
                .willReturn(
                        new ExpiryMeta(123L, currentExpiryMeta.autoRenewPeriod(), currentExpiryMeta.autoRenewNum()));

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(123L, newTopic.expiry());
    }

    @Test
    void validatesNewAutoRenewPeriodViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(123))
                .build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(NA, 123L, NA);
        willThrow(new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(expiryValidator)
                .resolveUpdateAttempt(currentExpiryMeta, impliedMeta);

        // expect:
        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void appliesNewAutoRenewPeriodViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(123))
                .build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(NA, 123L, NA);
        given(expiryValidator.resolveUpdateAttempt(currentExpiryMeta, impliedMeta))
                .willReturn(new ExpiryMeta(currentExpiryMeta.expiry(), 123L, currentExpiryMeta.autoRenewNum()));

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(123L, newTopic.autoRenewPeriod());
    }

    @Test
    void validatesNewAutoRenewAccountViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setAutoRenewAccount(autoRenewId)
                .build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(NA, NA, autoRenewId.getAccountNum());
        willThrow(new HandleStatusException(INVALID_AUTORENEW_ACCOUNT))
                .given(expiryValidator)
                .resolveUpdateAttempt(currentExpiryMeta, impliedMeta);

        // expect:
        assertFailsWith(INVALID_AUTORENEW_ACCOUNT, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    void appliesNewAutoRenewNumViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER
                .setTopicID(wellKnownId())
                .setAutoRenewAccount(AccountID.newBuilder().setAccountNum(666).build())
                .build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(NA, NA, 666);
        given(expiryValidator.resolveUpdateAttempt(currentExpiryMeta, impliedMeta))
                .willReturn(new ExpiryMeta(currentExpiryMeta.expiry(), currentExpiryMeta.autoRenewPeriod(), 666));

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(666L, newTopic.autoRenewAccountNumber());
    }

    @Test
    void nothingHappensIfUpdateIsNoop() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        // No-op
        final var op = OP_BUILDER.setTopicID(wellKnownId()).build();

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(topic, newTopic);
    }

    public static void assertFailsWith(final ResponseCodeEnum status, final Runnable something) {
        final var ex = assertThrows(HandleStatusException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    @Test
    void memoMutationsIsNonExpiry() {
        final var op = OP_BUILDER.setMemo(StringValue.of("HI")).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void adminKeyMutationIsNonExpiry() {
        final var op = OP_BUILDER.setAdminKey(key).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void submitKeyMutationIsNonExpiry() {
        final var op = OP_BUILDER.setSubmitKey(key).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void autoRenewPeriodMutationIsNonExpiry() {
        final var op = OP_BUILDER
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(123))
                .build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void autoRenewAccountMutationIsNonExpiry() {
        final var op = OP_BUILDER.setAutoRenewAccount(autoRenewId).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void expiryMutationIsExpiry() {
        final var op = OP_BUILDER
                .setExpirationTime(Timestamp.newBuilder().setSeconds(123L))
                .build();
        assertFalse(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void noneOfFieldsSetHaveNoRequiredKeys() {
        given(accountAccess.getKey(asAccount(payerId))).willReturn(withKey(adminKey));

        final var op =
                OP_BUILDER.setExpirationTime(Timestamp.newBuilder().build()).build();
        final var context = new PreHandleContext(accountAccess, txnWith(op));

        subject.preHandle(context, readableStore);

        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(adminKey);

        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    void missingTopicFails() {
        given(accountAccess.getKey(asAccount(payerId))).willReturn(withKey(adminKey));

        final var op = OP_BUILDER
                .setTopicID(TopicID.newBuilder().setTopicNum(123L).build())
                .build();
        final var context = new PreHandleContext(accountAccess, txnWith(op));

        subject.preHandle(context, readableStore);

        assertThat(context.getPayerKey()).isEqualTo(adminKey);
        assertTrue(context.failed());
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_TOPIC_ID);
        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    void adminKeyAndOpAdminKeyAdded() {
        given(accountAccess.getKey(asAccount(payerId))).willReturn(withKey(adminKey));

        final var op = OP_BUILDER
                .setAdminKey(key)
                .setTopicID(TopicID.newBuilder().setTopicNum(1L).build())
                .build();
        final var context = new PreHandleContext(accountAccess, txnWith(op));

        subject.preHandle(context, readableStore);

        assertThat(context.getPayerKey()).isEqualTo(adminKey);
        assertFalse(context.failed());
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.OK);
        // adminKey and op admin key
        assertEquals(2, context.getRequiredNonPayerKeys().size());
        //        assertSame(context.getRequiredNonPayerKeys().get(0), asHederaKey(key).get());
    }

    @Test
    void autoRenewAccountKeyAdded() {
        given(accountAccess.getKey(asAccount(payerId))).willReturn(withKey(adminKey));
        given(accountAccess.getKey(autoRenewId)).willReturn(withKey(adminKey));

        final var op = OP_BUILDER
                .setAutoRenewAccount(autoRenewId)
                .setTopicID(TopicID.newBuilder().setTopicNum(1L).build())
                .build();
        final var context = new PreHandleContext(accountAccess, txnWith(op));

        subject.preHandle(context, readableStore);

        assertThat(context.getPayerKey()).isEqualTo(adminKey);
        assertFalse(context.failed());
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.OK);
        // adminKey and auto-renew key
        assertEquals(2, context.getRequiredNonPayerKeys().size());
    }

    @Test
    void missingAutoREnewAccountFails() {
        given(accountAccess.getKey(asAccount(payerId))).willReturn(withKey(adminKey));
        given(accountAccess.getKey(autoRenewId)).willReturn(withFailureReason(INVALID_AUTORENEW_ACCOUNT));

        final var op = OP_BUILDER
                .setAutoRenewAccount(autoRenewId)
                .setTopicID(TopicID.newBuilder().setTopicNum(1L).build())
                .build();
        final var context = new PreHandleContext(accountAccess, txnWith(op));

        subject.preHandle(context, readableStore);

        assertThat(context.getPayerKey()).isEqualTo(adminKey);
        assertTrue(context.failed());
        assertThat(context.getStatus()).isEqualTo(INVALID_AUTORENEW_ACCOUNT);
        // adminKey
        assertEquals(1, context.getRequiredNonPayerKeys().size());
    }

    private TransactionBody txnWith(final ConsensusUpdateTopicTransactionBody op) {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(asAccount(payerId)))
                .setConsensusUpdateTopic(op)
                .build();
    }

    private TopicID wellKnownId() {
        return TopicID.newBuilder().setTopicNum(topicEntityNum.longValue()).build();
    }
}
