package com.hedera.services.fees.calculation.consensus.txns;

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

import com.google.protobuf.StringValue;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.test.utils.*;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.state.submerkle.RichInstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

class UpdateMerkleTopicResourceUsageTest extends TopicResourceUsageTestBase {
    UpdateTopicResourceUsage subject;

    @BeforeEach
    void setup() throws Throwable {
        super.setup();
        subject = new UpdateTopicResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // setup:
        TransactionBody updateTopicTx = TransactionBody.newBuilder()
                .setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.newBuilder().build())
                .build();
        TransactionBody nonUpdateTopicTx = TransactionBody.newBuilder().build();

        // expect:
        assertTrue(subject.applicableTo(updateTopicTx));
        assertFalse(subject.applicableTo(nonUpdateTopicTx));
    }

    @Test
    public void getFeeThrowsExceptionForBadTxBody() {
        // setup:
        TransactionBody nonUpdateTopicTx = TransactionBody.newBuilder().build();

        // expect:
        assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(null, sigValueObj, view));
        assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(nonUpdateTopicTx, sigValueObj, view));
    }

    // Test to check fee values correctness for various kinds of update topic transactions.
    // txValidStartTimestamp = 0 for these tests.
    @ParameterizedTest
    @CsvSource({
            // 24(topidId) + 8(autoRenewAccount); updating value -> no extra rbs cost
            ",,,,,,,, 2000,,, 32, 0",
            // Add fields; 24(topicId) + 8(memo), 32(admin key), 32(submit key), 24(auto renew account); no change in expiration timestamp, rbs increase due to all fields except topicId
            ", 12345678,, 0000000000000000000000000000000000000000000000000000000000000000,, 1111111111111111111111111111111111111111111111111111111111111111,, 0.1.2,, 3600_0,, 120, 96",
            // No change to fields; 24(topicId); no change in expiration timestamp, no additional rbs cost
            "12345678,, 0000000000000000000000000000000000000000000000000000000000000000,, 1111111111111111111111111111111111111111111111111111111111111111,, 0.1.2,,, 3600_0,, 24, 0",
            // No change to fields, only increase expiration time; 24(topicId) + 8(expirationTimestamp); rbs increase equal to size of set fields (memo, adminKey, autoRenewAccount)
            "12345678,, 0000000000000000000000000000000000000000000000000000000000000000,,,, 0.1.2,,, 3600_0, 7200_0, 32, 164",
    })
    public void feeDataAsExpected(
            String oldMemo,
            String newMemo,
            @ConvertWith(JEd25519KeyConverter.class) JEd25519Key oldAdminKey,
            @ConvertWith(Ed25519KeyConverter.class) Key newAdminKey,
            @ConvertWith(JEd25519KeyConverter.class) JEd25519Key oldSubmitKey,
            @ConvertWith(Ed25519KeyConverter.class) Key newSubmitKey,
            @ConvertWith(EntityIdConverter.class) EntityId oldAutoRenewAccountId,
            @ConvertWith(AccountIDConverter.class) AccountID newAutoRenewAccountId,
            @ConvertWith(DurationConverter.class) Duration newAutoRenewPeriod,
            @ConvertWith(RichInstantConverter.class) RichInstant oldExpirationTimestamp,
            @ConvertWith(RichInstantConverter.class) RichInstant newExpirationTimestamp,
            int expectedExtraBpt,
            int expectedExtraServicesRbh
    ) throws Exception {
        // setup:
        MerkleTopic merkleTopic = new MerkleTopic(oldMemo, oldAdminKey, oldSubmitKey, 0, oldAutoRenewAccountId,
                oldExpirationTimestamp);
        TransactionBody txBody = makeTransactionBody(topicId, newMemo, newAdminKey, newSubmitKey,
                newAutoRenewAccountId, newAutoRenewPeriod,
                Optional.ofNullable(newExpirationTimestamp).map(RichInstant::toGrpc).orElse(null));

        // when:
        given(topics.get(MerkleEntityId.fromTopicId(topicId))).willReturn(merkleTopic);
        FeeData feeData = subject.usageGiven(txBody, sigValueObj, view);

        // expect:
        checkServicesFee(feeData, expectedExtraServicesRbh);
        checkNetworkFee(feeData, expectedExtraBpt, 0);
        checkNodeFee(feeData, expectedExtraBpt);
    }

    private TransactionBody makeTransactionBody(
            TopicID topicId, String memo, Key adminKey, Key submitKey, AccountID autoRenewAccountId,
            Duration autoRenewPeriod, Timestamp expirationTime) {
        ConsensusUpdateTopicTransactionBody.Builder updateTopicTxBodyBuilder =
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
        TransactionID txId = TransactionID.newBuilder()
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(0).setNanos(0).build())
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(txId)
                .setConsensusUpdateTopic(updateTopicTxBodyBuilder)
                .build();
    }
}
