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
package com.hedera.services.fees.calculation.consensus.txns;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.google.protobuf.StringValue;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.AccountIDConverter;
import com.hedera.test.utils.DurationConverter;
import com.hedera.test.utils.Ed25519KeyConverter;
import com.hedera.test.utils.EntityIdConverter;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.JEd25519KeyConverter;
import com.hedera.test.utils.RichInstantConverter;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.swirlds.common.utility.CommonUtils;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.codec.DecoderException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(LogCaptureExtension.class)
class UpdateMerkleTopicResourceUsageTest extends TopicResourceUsageTestBase {
    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private UpdateTopicResourceUsage subject;

    private static final JEd25519Key adminKey =
            new JEd25519Key(
                    CommonUtils.unhex(
                            "0000000000000000000000000000000000000000000000000000000000000000"));
    private static final JEd25519Key submitKey =
            new JEd25519Key(
                    CommonUtils.unhex(
                            "1111111111111111111111111111111111111111111111111111111111111111"));
    private static final String defaultMemo = "12345678";

    @BeforeEach
    void setup() throws Throwable {
        super.setup();
        subject = new UpdateTopicResourceUsage();
    }

    @Test
    void recognizesApplicableQuery() {
        final var updateTopicTx =
                TransactionBody.newBuilder()
                        .setConsensusUpdateTopic(
                                ConsensusUpdateTopicTransactionBody.getDefaultInstance())
                        .build();
        final var nonUpdateTopicTx = TransactionBody.getDefaultInstance();

        assertTrue(subject.applicableTo(updateTopicTx));
        assertFalse(subject.applicableTo(nonUpdateTopicTx));
    }

    @Test
    void getFeeThrowsExceptionForBadTxBody() {
        final var mockTxnBody = mock(TransactionBody.class);
        given(mockTxnBody.hasConsensusUpdateTopic()).willReturn(false);

        Exception exception =
                assertThrows(
                        InvalidTxBodyException.class,
                        () -> subject.usageGiven(null, sigValueObj, view));
        assertEquals(
                "consensusUpdateTopic field not available for Fee Calculation",
                exception.getMessage());

        exception =
                assertThrows(
                        InvalidTxBodyException.class,
                        () -> subject.usageGiven(mockTxnBody, sigValueObj, view));
        assertEquals(
                "consensusUpdateTopic field not available for Fee Calculation",
                exception.getMessage());

        given(mockTxnBody.hasConsensusUpdateTopic()).willReturn(true);
        exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> subject.usageGiven(mockTxnBody, sigValueObj, null));
        assertEquals("No StateView present !!", exception.getMessage());
    }

    @Test
    void getFeeThrowsExceptionForBadKeys() throws DecoderException, IllegalArgumentException {
        final var txnBody =
                makeTransactionBody(
                        topicId,
                        defaultMemo,
                        JKey.mapJKey(adminKey),
                        JKey.mapJKey(submitKey),
                        IdUtils.asAccount("0.1.2"),
                        null,
                        null);
        final var merkleTopic =
                new MerkleTopic(
                        defaultMemo,
                        adminKey,
                        submitKey,
                        0,
                        new EntityId(0, 1, 2),
                        new RichInstant(36_000, 0));
        given(topics.get(EntityNum.fromTopicId(topicId))).willReturn(merkleTopic);
        final var mockedJkey = mockStatic(JKey.class);
        mockedJkey.when(() -> JKey.mapJKey(any())).thenThrow(new DecoderException());

        assertThrows(
                InvalidTxBodyException.class, () -> subject.usageGiven(txnBody, sigValueObj, view));
        assertThat(
                logCaptor.warnLogs(),
                Matchers.contains(Matchers.startsWith("Usage estimation unexpectedly failed for")));
        mockedJkey.close();
    }

    @Test
    void updateToMissingTopic() throws DecoderException, InvalidTxBodyException {
        final var txBody =
                makeTransactionBody(
                        topicId,
                        defaultMemo,
                        JKey.mapJKey(adminKey),
                        JKey.mapJKey(submitKey),
                        IdUtils.asAccount("0.1.2"),
                        null,
                        null);
        given(topics.get(EntityNum.fromTopicId(topicId))).willReturn(null);

        final var feeData = subject.usageGiven(txBody, sigValueObj, view);

        checkServicesFee(feeData, 0);
        checkNetworkFee(feeData, 120, 0);
        checkNodeFee(feeData, 120);
    }

    // Test to check fee values correctness for various kinds of update topic transactions.
    // txValidStartTimestamp = 0 for these tests.
    @ParameterizedTest
    @CsvSource({
        // 24(topidId) + 8(autoRenewAccount); updating value -> no extra rbs cost
        ",,,,,,,, 2000,,, 32, 0",
        // Add fields; 24(topicId) + 8(memo), 32(admin key), 32(submit key), 24(auto renew account);
        // no change in
        // expiration timestamp, 0 increase in rbs as no Old Admin Key making it immutable
        ", 12345678,, 0000000000000000000000000000000000000000000000000000000000000000,,"
            + " 1111111111111111111111111111111111111111111111111111111111111111,, 0.1.2,, 3600_0,,"
            + " 120, 0",
        // No change to fields; 24(topicId); no change in expiration timestamp, no additional rbs
        // cost
        "12345678,, 0000000000000000000000000000000000000000000000000000000000000000,,"
                + " 1111111111111111111111111111111111111111111111111111111111111111,, 0.1.2,,,"
                + " 3600_0,, 24, 0",
        // No change to fields, only increase expiration time; 24(topicId) + 8(expirationTimestamp);
        // rbs increase
        // equal to size of set fields (memo, adminKey, autoRenewAccount)
        "12345678,, 0000000000000000000000000000000000000000000000000000000000000000,,,, 0.1.2,,,"
                + " 3600_0, 7200_0, 32, 164",
    })
    void feeDataAsExpected(
            final String oldMemo,
            final String newMemo,
            @ConvertWith(JEd25519KeyConverter.class) final JEd25519Key oldAdminKey,
            @ConvertWith(Ed25519KeyConverter.class) final Key newAdminKey,
            @ConvertWith(JEd25519KeyConverter.class) final JEd25519Key oldSubmitKey,
            @ConvertWith(Ed25519KeyConverter.class) final Key newSubmitKey,
            @ConvertWith(EntityIdConverter.class) final EntityId oldAutoRenewAccountId,
            @ConvertWith(AccountIDConverter.class) final AccountID newAutoRenewAccountId,
            @ConvertWith(DurationConverter.class) final Duration newAutoRenewPeriod,
            @ConvertWith(RichInstantConverter.class) final RichInstant oldExpirationTimestamp,
            @ConvertWith(RichInstantConverter.class) final RichInstant newExpirationTimestamp,
            final int expectedExtraBpt,
            final int expectedExtraServicesRbh)
            throws InvalidTxBodyException, IllegalStateException {
        final var merkleTopic =
                new MerkleTopic(
                        oldMemo,
                        oldAdminKey,
                        oldSubmitKey,
                        0,
                        oldAutoRenewAccountId,
                        oldExpirationTimestamp);
        final var txBody =
                makeTransactionBody(
                        topicId,
                        newMemo,
                        newAdminKey,
                        newSubmitKey,
                        newAutoRenewAccountId,
                        newAutoRenewPeriod,
                        Optional.ofNullable(newExpirationTimestamp)
                                .map(RichInstant::toGrpc)
                                .orElse(null));

        given(topics.get(EntityNum.fromTopicId(topicId))).willReturn(merkleTopic);
        final var feeData = subject.usageGiven(txBody, sigValueObj, view);

        checkServicesFee(feeData, expectedExtraServicesRbh);
        checkNetworkFee(feeData, expectedExtraBpt, 0);
        checkNodeFee(feeData, expectedExtraBpt);
    }

    private TransactionBody makeTransactionBody(
            final TopicID topicId,
            @Nullable final String memo,
            final Key adminKey,
            final Key submitKey,
            final AccountID autoRenewAccountId,
            @Nullable final Duration autoRenewPeriod,
            @Nullable final Timestamp expirationTime) {
        final var updateTopicTxBodyBuilder =
                ConsensusUpdateTopicTransactionBody.newBuilder()
                        .setTopicID(topicId)
                        .mergeAdminKey(adminKey)
                        .mergeSubmitKey(submitKey)
                        .mergeAutoRenewAccount(autoRenewAccountId);
        if (memo != null) {
            updateTopicTxBodyBuilder.setMemo(StringValue.of(memo));
        }
        if (expirationTime != null) {
            updateTopicTxBodyBuilder.setExpirationTime(expirationTime);
        }
        if (autoRenewPeriod != null) {
            updateTopicTxBodyBuilder.setAutoRenewPeriod(autoRenewPeriod);
        }
        // Set transaction valid start time to 0. Makes expiration time based tests easy.
        final var txId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(0).setNanos(0));
        return TransactionBody.newBuilder()
                .setTransactionID(txId)
                .setConsensusUpdateTopic(updateTopicTxBodyBuilder)
                .build();
    }
}
