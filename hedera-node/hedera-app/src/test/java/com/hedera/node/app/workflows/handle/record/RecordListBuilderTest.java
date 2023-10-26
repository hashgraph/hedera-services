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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REVERTED_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordListBuilderTest extends AppTestBase {

    private static final Instant CONSENSUS_NOW = Instant.parse("2000-01-01T00:00:00Z");

    private static final long MAX_PRECEDING = 3;
    private static final long MAX_CHILDREN = 10;

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.create()
            .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
            .withValue("consensus.message.maxFollowingRecords", MAX_CHILDREN)
            .getOrCreateConfig();

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidParameters() {
        assertThatThrownBy(() -> new RecordListBuilder(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitialRecordListBuilder() {
        // given
        final var consensusTime = Instant.now();

        // when
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);
        final var result = recordListBuilder.build();

        // then
        assertThat(recordListBuilder.precedingRecordBuilders()).isEmpty();
        assertThat(recordListBuilder.childRecordBuilders()).isEmpty();
        assertThat(recordListBuilder.userTransactionRecordBuilder()).isNotNull();
        assertThat(recordListBuilder.userTransactionRecordBuilder().consensusNow())
                .isEqualTo(consensusTime);
        assertThat(result.userTransactionRecord()).isNotNull();
        assertThat(result.records()).containsExactly(result.userTransactionRecord());
    }

    @Test
    void testAddSinglePreceding() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then there are two records: a preceding record, and the user transaction record. The preceding record
        // will have a consensus timestamp that is ONE NANOSECOND before the user transaction record, and the
        // transaction ID of the preceding record will be the same as the user transaction record, except with a
        // nonce of 1.
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasNoParent();
        assertCreatedRecord(records.get(1)).hasNonce(0).hasNoParent();
    }

    @Test
    void testAddMultiplePrecedingRecords() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(3);
        assertThat(records.get(2)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasNoParent();
        assertCreatedRecord(records.get(2)).hasNonce(0).hasNoParent();
    }

    @Test
    void testAddTooManyPrecedingRecordsFails() {
        // given
        final var maxPreceding = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.message.maxPrecedingRecords", maxPreceding)
                .withValue("consensus.message.maxFollowingRecords", MAX_CHILDREN)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addPreceding(config);
        recordListBuilder.addPreceding(config);

        // then
        assertThatThrownBy(() -> recordListBuilder.addPreceding(config))
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
    }

    @Test
    void testAddSingleChild() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testAddMultipleChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(3);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testAddTooManyChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.message.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addChild(config);
        recordListBuilder.addChild(config);

        // then
        assertThatThrownBy(() -> recordListBuilder.addChild(config))
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
    }

    @Test
    void testAddPrecedingAndChildRecords() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        final var first = simpleCryptoTransfer();
        final var second = simpleCryptoTransfer();
        final var fourth = simpleCryptoTransfer();
        final var fifth = simpleCryptoTransfer();
        // mixing up preceding vs. following, but within which, in order
        recordListBuilder.addChild(CONFIGURATION).transaction(fourth);
        recordListBuilder.addPreceding(CONFIGURATION).transaction(first);
        recordListBuilder.addPreceding(CONFIGURATION).transaction(second);
        recordListBuilder.addChild(CONFIGURATION).transaction(fifth);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(5);
        assertThat(records.get(2)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasNoParent()
                .hasTransaction(first);
        assertCreatedRecord(records.get(1))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasNoParent()
                .hasTransaction(second);
        assertCreatedRecord(records.get(2)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(3))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(3)
                .hasParent(result.userTransactionRecord())
                .hasTransaction(fourth);
        assertCreatedRecord(records.get(4))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(4)
                .hasParent(result.userTransactionRecord())
                .hasTransaction(fifth);
    }

    @Test
    void testRevertSingleChild() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(base);
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then, we will find all three records exist, where "child1" will be reverted, but child 2 will not be.
        assertThat(records).hasSize(3);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasResponseCode(OK).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(OK)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testRevertNotFound() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);
        final var nonExistent = new SingleTransactionRecordBuilderImpl(Instant.EPOCH);

        // when
        assertThatThrownBy(() -> recordListBuilder.revertChildrenOf(nonExistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRevertMultipleChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);
        final var child1 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child3 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        child3.status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildrenOf(child1);
        recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(5);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasResponseCode(OK).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK) // child1 is not reverted, but child 2 and 3 are
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(3))
                .nanosAfter(3, result.userTransactionRecord())
                .hasNonce(3)
                .hasResponseCode(ACCOUNT_ID_DOES_NOT_EXIST) // Keeps it's error response code
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(4))
                .nanosAfter(4, result.userTransactionRecord())
                .hasNonce(4)
                .hasResponseCode(OK)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testAddSingleRemovableChild() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testAddMultipleRemovableChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(3);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testAddTooManyRemovableChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.message.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addRemovableChild(CONFIGURATION);
        recordListBuilder.addRemovableChild(CONFIGURATION);

        // then
        assertThatThrownBy(() -> recordListBuilder.addRemovableChild(config))
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
    }

    @Test
    void testRevertSingleRemovableChild() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        final var revertedTx = simpleCryptoTransfer();
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(revertedTx);

        // when
        recordListBuilder.revertChildrenOf(base);
        final var remainingTx = simpleCryptoTransfer();
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(remainingTx);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(2);
        assertCreatedRecord(records.get(0))
                .hasNonce(0)
                .hasResponseCode(OK)
                .hasTransaction(result.userTransactionRecord().transaction())
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK)
                .hasTransaction(remainingTx)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testRevertMultipleRemovableChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);
        final var child1Tx = simpleCryptoTransfer();
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(child1Tx);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer()); // will be removed
        recordListBuilder
                .addRemovableChild(CONFIGURATION)
                .transaction(simpleCryptoTransfer()) // will be removed
                .status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildrenOf(child1);
        final var remainingTx = simpleCryptoTransfer();
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(remainingTx);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(3);
        assertCreatedRecord(records.get(0))
                .hasNonce(0)
                .hasResponseCode(OK)
                .hasTransaction(result.userTransactionRecord().transaction())
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK)
                .hasTransaction(child1Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(OK)
                .hasTransaction(remainingTx)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testRevertMultipleMixedChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        final var child1Tx = simpleCryptoTransfer();
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(child1Tx);
        final var child2Tx = simpleCryptoTransfer();
        recordListBuilder.addChild(CONFIGURATION).transaction(child2Tx);
        final var child3Tx = simpleCryptoTransfer();
        final var child3 = recordListBuilder.addChild(CONFIGURATION).transaction(child3Tx);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer()); // will be removed
        final var child5Tx = simpleCryptoTransfer();
        recordListBuilder.addChild(CONFIGURATION).transaction(child5Tx); // will revert
        final var child6Tx = simpleCryptoTransfer();
        recordListBuilder.addChild(CONFIGURATION).transaction(child6Tx); // will revert
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer()); // will be removed

        // when
        recordListBuilder.revertChildrenOf(child3);
        final var child8Tx = simpleCryptoTransfer();
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(child8Tx);
        final var child9Tx = simpleCryptoTransfer();
        recordListBuilder.addChild(CONFIGURATION).transaction(child9Tx);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(8);
        assertCreatedRecord(records.get(0))
                .hasNonce(0) // user transaction always has a nonce of 0
                .hasResponseCode(OK) // child3's children were reverted, not the user transaction
                .hasTransaction(result.userTransactionRecord().transaction())
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord()) // first child gets next consensus time
                .hasNonce(1) // first child
                .hasResponseCode(OK) // child3's children were reverted, first child comes before, so it is not affected
                .hasTransaction(child1Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2) // second child
                .hasResponseCode(
                        OK) // child3's children were reverted, second child comes before, so it is not affected
                .hasTransaction(child2Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(3))
                .nanosAfter(3, result.userTransactionRecord())
                .hasNonce(3) // third child. The children of this was were reverted
                .hasResponseCode(OK) // child3's children were reverted, third child is not affected
                .hasTransaction(child3Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(4))
                // child4 was removed, leaving a gap in consensus time.
                .nanosAfter(5, result.userTransactionRecord())
                .hasNonce(4) // child5 gets the 4th nonce since child4 was removed
                .hasResponseCode(REVERTED_SUCCESS)
                .hasTransaction(child5Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(5))
                .nanosAfter(6, result.userTransactionRecord()) // immediately after child5
                .hasNonce(5) // child6 gets the 5th nonce since child4 was removed
                .hasResponseCode(REVERTED_SUCCESS)
                .hasTransaction(child6Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(6))
                .nanosAfter(7, result.userTransactionRecord())
                .hasNonce(6)
                .hasResponseCode(OK)
                .hasTransaction(child8Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(7))
                .nanosAfter(8, result.userTransactionRecord())
                .hasNonce(7)
                .hasResponseCode(OK)
                .hasTransaction(child9Tx)
                .hasParent(result.userTransactionRecord());
    }

    private SingleTransactionRecordBuilderImpl addUserTransaction(RecordListBuilder builder) {
        final var start = Instant.now().minusSeconds(60);
        return builder.userTransactionRecordBuilder()
                .transaction(simpleCryptoTransfer())
                .transactionID(TransactionID.newBuilder()
                        .accountID(ALICE.accountID())
                        .transactionValidStart(asTimestamp(start))
                        .build());
    }

    private TransactionRecordAssertions assertCreatedRecord(SingleTransactionRecord record) {
        return new TransactionRecordAssertions(record);
    }

    private static final class TransactionRecordAssertions {
        private final SingleTransactionRecord record;

        private TransactionRecordAssertions(@NonNull final SingleTransactionRecord record) {
            this.record = record;
        }

        TransactionRecordAssertions nanosBefore(final int nanos, @NonNull final SingleTransactionRecord otherRecord) {
            final var otherTimestamp = otherRecord.transactionRecord().consensusTimestampOrThrow();
            final var expectedTimestamp = otherTimestamp
                    .copyBuilder()
                    .nanos(otherTimestamp.nanos() - nanos)
                    .build();
            assertThat(record.transactionRecord().consensusTimestampOrThrow()).isEqualTo(expectedTimestamp);
            return this;
        }

        TransactionRecordAssertions nanosAfter(final int nanos, @NonNull final SingleTransactionRecord otherRecord) {
            final var otherTimestamp = otherRecord.transactionRecord().consensusTimestampOrThrow();
            final var expectedTimestamp = otherTimestamp
                    .copyBuilder()
                    .nanos(otherTimestamp.nanos() + nanos)
                    .build();
            assertThat(record.transactionRecord().consensusTimestampOrThrow()).isEqualTo(expectedTimestamp);
            return this;
        }

        TransactionRecordAssertions hasNonce(final int nonce) {
            assertThat(record.transactionRecord().transactionIDOrThrow().nonce())
                    .isEqualTo(nonce);
            return this;
        }

        TransactionRecordAssertions hasParent(@NonNull final SingleTransactionRecord parent) {
            assertThat(record.transactionRecord().parentConsensusTimestamp())
                    .isEqualTo(parent.transactionRecord().consensusTimestampOrThrow());
            return this;
        }

        TransactionRecordAssertions hasResponseCode(@NonNull final ResponseCodeEnum responseCode) {
            assertThat(record.transactionRecord().receiptOrThrow().status()).isEqualTo(responseCode);
            return this;
        }

        public TransactionRecordAssertions hasNoParent() {
            assertThat(record.transactionRecord().parentConsensusTimestamp()).isNull();
            return this;
        }

        public TransactionRecordAssertions hasTransaction(@NonNull final Transaction tx) {
            assertThat(record.transaction()).isEqualTo(tx);
            return this;
        }
    }
}
