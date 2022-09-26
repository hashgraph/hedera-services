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
package com.hedera.services.legacy.core.jproto;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_RUNNING_HASH;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_RUNNING_HASH_VERSION;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_SCHEDULED_TXN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.protobuf.ByteString;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TxnReceiptTest {
    final TransactionID scheduledTxnId =
            TransactionID.newBuilder()
                    .setScheduled(true)
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .build();

    private TopicID getTopicId(long shard, long realm, long num) {
        return TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
    }

    private EntityId getTopicJAccountId(long shard, long realm, long num) {
        return new EntityId(shard, realm, num);
    }

    private byte[] getSha384Hash() {
        final var hash = new byte[48];
        for (var i = 0; i < hash.length; ++i) {
            hash[i] = (byte) i;
        }
        return hash;
    }

    @Test
    void canGetStatusAsEnum() {
        final var subject = TxnReceipt.newBuilder().setStatus("INVALID_ACCOUNT_ID").build();
        assertEquals(INVALID_ACCOUNT_ID, subject.getEnumStatus());
    }

    @Test
    void constructorPostConsensusCreateTopic() {
        final var topicId = getTopicJAccountId(1L, 22L, 333L);
        final var sequenceNumber = 0L;
        final var cut =
                TxnReceipt.newBuilder()
                        .setTopicId(topicId)
                        .setTopicSequenceNumber(sequenceNumber)
                        .build();

        assertAll(
                () -> assertEquals(topicId, cut.getTopicId()),
                () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
                () -> assertNull(cut.getTopicRunningHash()));
    }

    @Test
    void constructorPostConsensusSubmitMessage() {
        final var sequenceNumber = 55555L;
        final var cut =
                TxnReceipt.newBuilder()
                        .setTopicRunningHash(getSha384Hash())
                        .setTopicSequenceNumber(sequenceNumber)
                        .build();

        assertAll(
                () -> assertNull(cut.getTopicId()),
                () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
                () -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash()));
    }

    @Test
    void setRunning() {
        final var cut = new TxnReceipt();
        cut.topicRunningHash = getSha384Hash();
        assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash());
    }

    @Test
    void convertToJTransactionReceiptPostConsensusCreateTopic() {
        final var topicId = getTopicId(1L, 22L, 333L);
        final var receipt =
                TransactionReceipt.newBuilder()
                        .setExchangeRate(new ExchangeRates().toGrpc())
                        .setTopicID(topicId)
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);

        assertAll(
                () -> assertEquals(EntityId.fromGrpcTopicId(topicId), cut.getTopicId()),
                () -> assertNull(cut.getAccountId()),
                () -> assertNull(cut.getFileId()),
                () -> assertNull(cut.getContractId()),
                () -> assertEquals(new ExchangeRates(), cut.getExchangeRates()),
                () -> assertEquals(0L, cut.getTopicSequenceNumber()),
                () -> assertNull(cut.getTopicRunningHash()));
    }

    @Test
    void scheduleCreateInterconversionWorks() {
        final var scheduleId = IdUtils.asSchedule("0.0.123");

        final var receipt =
                TransactionReceipt.newBuilder()
                        .setExchangeRate(new ExchangeRates().toGrpc())
                        .setScheduleID(scheduleId)
                        .setScheduledTransactionID(scheduledTxnId)
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);
        final var back = TxnReceipt.convert(cut);

        assertEquals(receipt, back);
    }

    @Test
    void postConsensusSubmitMessageInterconversionWorks() {
        final var topicSequenceNumber = 4444L;
        final var topicRunningHash = getSha384Hash();

        final var receipt =
                TransactionReceipt.newBuilder()
                        .setExchangeRate(new ExchangeRates().toGrpc())
                        .setTopicSequenceNumber(topicSequenceNumber)
                        .setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
                        .setTopicRunningHashVersion(2L)
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);
        final var back = TxnReceipt.convert(cut);

        assertEquals(receipt, back);
    }

    @Test
    void postConsensusTokenMintBurnWipeInterconversionWorks() {
        final var totalSupply = 12345L;

        final var receipt =
                TransactionReceipt.newBuilder()
                        .setExchangeRate(new ExchangeRates().toGrpc())
                        .setNewTotalSupply(totalSupply)
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);
        final var back = TxnReceipt.convert(cut);

        assertEquals(receipt, back);
    }

    @Test
    void postConsensusTokenNftInterconversionWorks() {
        final var receipt =
                TransactionReceipt.newBuilder()
                        .setExchangeRate(new ExchangeRates().toGrpc())
                        .addAllSerialNumbers(List.of(1L, 2L, 3L, 4L, 5L))
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);
        final var back = TxnReceipt.convert(cut);

        assertEquals(receipt, back);
    }

    @Test
    void postConsensusTokenCreationInterconversionWorks() {
        final TokenID.Builder tokenIdBuilder =
                TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0);

        final var receipt =
                TransactionReceipt.newBuilder()
                        .setExchangeRate(new ExchangeRates().toGrpc())
                        .setTokenID(tokenIdBuilder)
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);
        final var back = TxnReceipt.convert(cut);

        assertEquals(receipt, back);
    }

    @Test
    void convertToJTransactionReceiptPostConsensusSubmitMessage() {
        final var topicSequenceNumber = 4444L;
        final var topicRunningHash = getSha384Hash();

        final var receipt =
                TransactionReceipt.newBuilder()
                        .setTopicSequenceNumber(topicSequenceNumber)
                        .setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
                        .setTopicRunningHashVersion(2L)
                        .build();
        final var cut = ExpirableTxnRecordTestHelper.fromGrpc(receipt);

        assertAll(
                () -> assertEquals(2L, cut.getRunningHashVersion()),
                () -> assertNull(cut.getTopicId()),
                () -> assertEquals(topicSequenceNumber, cut.getTopicSequenceNumber()),
                () -> assertArrayEquals(topicRunningHash, cut.getTopicRunningHash()));
    }

    @Test
    void convertToTransactionReceiptPostConsensusCreateTopic() {
        final var topicId = getTopicJAccountId(1L, 22L, 333L);
        final var receipt = new TxnReceipt();
        receipt.status = "OK";
        receipt.topicId = topicId;
        final var cut = TxnReceipt.convert(receipt);

        assertAll(
                () -> assertEquals(topicId.shard(), cut.getTopicID().getShardNum()),
                () -> assertEquals(topicId.realm(), cut.getTopicID().getRealmNum()),
                () -> assertEquals(topicId.num(), cut.getTopicID().getTopicNum()),
                () -> assertEquals(0L, cut.getTopicSequenceNumber()),
                () -> assertEquals(0, cut.getTopicRunningHash().size()));
    }

    @Test
    void convertToTransactionReceiptPostConsensusSubmitMessage() {
        final var sequenceNumber = 666666L;
        final var receipt = new TxnReceipt();
        receipt.status = "OK";
        receipt.topicSequenceNumber = sequenceNumber;
        receipt.topicRunningHash = getSha384Hash();
        final var cut = TxnReceipt.convert(receipt);

        assertAll(
                () -> assertFalse(cut.hasTopicID()),
                () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
                () -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash().toByteArray()));
    }

    @Test
    void equalsDefaults() {
        assertEquals(new TxnReceipt(), new TxnReceipt());
    }

    @Test
    void hashCodeWithNulls() {
        final var cut = new TxnReceipt();
        assertNull(cut.getTopicId());
        assertNull(cut.getTopicRunningHash());

        Assertions.assertDoesNotThrow(() -> cut.hashCode());
    }

    @Test
    void toStringWithNulls() {
        final var cut = new TxnReceipt();
        assertNull(cut.getTopicId());
        assertNull(cut.getTopicRunningHash());

        assertAll(
                () -> Assertions.assertDoesNotThrow(() -> cut.toString()),
                () -> assertNotNull(cut.toString()));
    }

    @Test
    void scheduleConstructor() {
        final var scheduleId = EntityId.fromGrpcScheduleId(IdUtils.asSchedule("0.0.123"));
        final var cut =
                TxnReceipt.newBuilder()
                        .setStatus("SUCCESS")
                        .setScheduleId(scheduleId)
                        .setTopicRunningHash(MISSING_RUNNING_HASH)
                        .setTopicSequenceNumber(0L)
                        .setRunningHashVersion(0)
                        .setNewTotalSupply(0L)
                        .setScheduledTxnId(TxnId.fromGrpc(scheduledTxnId))
                        .build();

        assertAll(
                () -> Assertions.assertDoesNotThrow(() -> cut.toString()),
                () -> assertNotNull(cut.toString()));
    }

    @Test
    void hcsConstructor() {
        final var topicId = EntityId.fromGrpcTopicId(TopicID.newBuilder().setTopicNum(1L).build());
        final var sequenceNumber = 2L;
        final var runningHash = new byte[3];
        final var cut =
                TxnReceipt.newBuilder()
                        .setStatus("SUCCESS")
                        .setTopicId(topicId)
                        .setTopicRunningHash(runningHash)
                        .setTopicSequenceNumber(sequenceNumber)
                        .build();

        assertEquals(topicId, cut.getTopicId());
        assertEquals(sequenceNumber, cut.getTopicSequenceNumber());
        assertEquals(runningHash, cut.getTopicRunningHash());

        assertAll(
                () -> Assertions.assertDoesNotThrow(() -> cut.toString()),
                () -> assertNotNull(cut.toString()));
    }

    @Test
    void tokenConstructorWithTokenId() {
        final var tokenId =
                EntityId.fromGrpcTokenId(
                        TokenID.newBuilder()
                                .setTokenNum(1001L)
                                .setRealmNum(0)
                                .setShardNum(0)
                                .build());
        final var cut =
                TxnReceipt.newBuilder()
                        .setStatus("SUCCESS")
                        .setTokenId(tokenId)
                        .setNewTotalSupply(0L)
                        .build();

        assertEquals(tokenId, cut.getTokenId());

        assertAll(
                () -> Assertions.assertDoesNotThrow(() -> cut.toString()),
                () -> assertNotNull(cut.toString()));
    }

    @Test
    void tokenConstructorWithTotalSupply() {
        final var tokenId =
                EntityId.fromGrpcTokenId(
                        TokenID.newBuilder()
                                .setTokenNum(1001L)
                                .setRealmNum(0)
                                .setShardNum(0)
                                .build());
        final var cut =
                TxnReceipt.newBuilder()
                        .setStatus("SUCCESS")
                        .setTokenId(tokenId)
                        .setRunningHashVersion(MISSING_RUNNING_HASH_VERSION)
                        .setTopicSequenceNumber(0L)
                        .setNewTotalSupply(1000L)
                        .setScheduledTxnId(MISSING_SCHEDULED_TXN_ID)
                        .build();

        assertEquals(1000L, cut.getNewTotalSupply());

        assertAll(
                () -> Assertions.assertDoesNotThrow(cut::toString),
                () -> assertNotNull(cut.toString()));
    }
}
