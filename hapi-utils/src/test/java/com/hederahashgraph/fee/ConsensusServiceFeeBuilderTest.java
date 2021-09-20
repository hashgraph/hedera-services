package com.hederahashgraph.fee;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConsensusServiceFeeBuilderTest {
    private static final Key A_KEY = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();
    private static final String MEMO = "This is a memo.";
    private static final Timestamp TIMESTAMP = Timestamp.newBuilder().setSeconds(100L).setNanos(100).build();
    private static final AccountID ACCOUNT_A = AccountID.newBuilder().setAccountNum(3L).setRealmNum(0L)
            .setShardNum(0L).build();
    private static final Duration DURATION = Duration.newBuilder().setSeconds(1000L).build();
    private static final SigValueObj SIG_VALUE_OBJ = new SigValueObj(1, 1, 1);

    @Test
    void builderMethodsThrowException() {
        final var txnBody = TransactionBody.newBuilder().build();

        Assertions.assertThrows(InvalidTxBodyException.class,
                () -> ConsensusServiceFeeBuilder.getConsensusCreateTopicFee(null, null));
        Assertions.assertThrows(InvalidTxBodyException.class,
                () -> ConsensusServiceFeeBuilder.getConsensusCreateTopicFee(txnBody, null));
        Assertions.assertThrows(InvalidTxBodyException.class,
                () -> ConsensusServiceFeeBuilder.getConsensusUpdateTopicFee(null, 100L, null));
        Assertions.assertThrows(InvalidTxBodyException.class,
                () -> ConsensusServiceFeeBuilder.getConsensusUpdateTopicFee(txnBody, 100L, null));
        Assertions.assertThrows(InvalidTxBodyException.class,
                () -> ConsensusServiceFeeBuilder.getConsensusDeleteTopicFee(null, null));
        Assertions.assertThrows(InvalidTxBodyException.class,
                () -> ConsensusServiceFeeBuilder.getConsensusDeleteTopicFee(txnBody, null));
    }

    @Test
    void getConsensusCreateTopicFeeHappyPath() throws InvalidTxBodyException {
        final var txnBody = TransactionBody.newBuilder()
                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder()
                        .setAdminKey(A_KEY)
                        .setSubmitKey(A_KEY)
                        .setMemo(MEMO)
                        .setAutoRenewAccount(ACCOUNT_A)
                        .setAutoRenewPeriod(DURATION)
                        .build())
                .build();

        final var expected = FeeData.newBuilder()
                .setNodedata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setBpt(188L)
                        .setVpt(1L)
                        .setBpr(4L)
                        .build())
                .setNetworkdata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setBpt(188L)
                        .setVpt(1L)
                        .setRbh(3L)
                        .build())
                .setServicedata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setRbh(62L)
                        .build())
                .build();
        final var actual = ConsensusServiceFeeBuilder.getConsensusCreateTopicFee(txnBody, SIG_VALUE_OBJ);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void getConsensusUpdateTopicFeeHappyPath() throws InvalidTxBodyException {
        final var txnBody = TransactionBody.newBuilder()
                .setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.newBuilder()
                        .setMemo(StringValue.of(MEMO))
                        .setAdminKey(A_KEY)
                        .setAutoRenewPeriod(DURATION)
                        .build())
                .build();

        final var expected = FeeData.newBuilder()
                .setNodedata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setBpt(156L)
                        .setVpt(1L)
                        .setBpr(4L)
                        .build())
                .setNetworkdata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setBpt(156L)
                        .setVpt(1L)
                        .setRbh(1L)
                        .build())
                .setServicedata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setRbh(6L)
                        .build())
                .build();
        final var actual = ConsensusServiceFeeBuilder.getConsensusUpdateTopicFee(txnBody,
                100L, SIG_VALUE_OBJ);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void getUpdateTopicRbsIncreaseHappyPath() {
        final var bKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .setKeyList(KeyList.newBuilder().build())
                .build();
        final var emptyAccount = AccountID.newBuilder().setAccountNum(0L).setRealmNum(0L)
                .setShardNum(0L).build();
        final var txnBody = ConsensusUpdateTopicTransactionBody.newBuilder()
                .setMemo(StringValue.of(MEMO))
                .setAdminKey(bKey)
                .setSubmitKey(bKey)
                .setAutoRenewPeriod(DURATION)
                .setAutoRenewAccount(emptyAccount)
                .build();


        final var actual = ConsensusServiceFeeBuilder.getUpdateTopicRbsIncrease(TIMESTAMP, A_KEY, A_KEY,
                MEMO, true, TIMESTAMP, txnBody);

        Assertions.assertEquals(0L, actual);
    }

    @Test
    void getConsensusDeleteTopicFeeHappyPath() throws InvalidTxBodyException {
        final var topicId = TopicID.newBuilder().setTopicNum(5L).setRealmNum(0L).setShardNum(0L).build();
        final var txnBody = TransactionBody.newBuilder()
                .setConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody.newBuilder()
                        .setTopicID(topicId)
                        .build())
                .build();

        final var expected = FeeData.newBuilder()
                .setNodedata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setBpt(101L)
                        .setVpt(1L)
                        .setBpr(4L)
                        .build())
                .setNetworkdata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setBpt(101L)
                        .setVpt(1L)
                        .setRbh(1L)
                        .build())
                .setServicedata(FeeComponents.newBuilder()
                        .setConstant(1L)
                        .setRbh(6L)
                        .build())
                .build();
        final var actual = ConsensusServiceFeeBuilder.getConsensusDeleteTopicFee(txnBody, SIG_VALUE_OBJ);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void helperMethodsWork() {
        final var baseTopicRamByteSize = 100;
        Assertions.assertEquals(baseTopicRamByteSize, ConsensusServiceFeeBuilder.getTopicRamBytes(0));
        Assertions.assertEquals(baseTopicRamByteSize + 20, ConsensusServiceFeeBuilder.getTopicRamBytes(20));

        final var actualWithAutoRenewAccount = ConsensusServiceFeeBuilder.computeVariableSizedFieldsUsage(
                A_KEY, A_KEY, MEMO, true);
        final var actualWithoutAutRenewAccount = ConsensusServiceFeeBuilder.computeVariableSizedFieldsUsage(
                A_KEY, A_KEY, MEMO, false);
        Assertions.assertEquals(103, actualWithAutoRenewAccount);
        Assertions.assertEquals(79, actualWithoutAutRenewAccount);
    }
}
