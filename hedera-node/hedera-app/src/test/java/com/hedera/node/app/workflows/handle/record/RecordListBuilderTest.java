/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.spi.workflows.HandleContext.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.fixtures.AppTestBase;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordListBuilderTest extends AppTestBase {
    private static final long MAX_PRECEDING = 3;
    private static final long MAX_CHILDREN = 10;

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.create()
            .withValue("consensus.handle.maxPrecedingRecords", MAX_PRECEDING)
            .withValue("consensus.handle.maxFollowingRecords", MAX_CHILDREN)
            .getOrCreateConfig();
    private static final int EXPECTED_CHILD_NANO_INCREMENT = 0;

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
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());
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
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());
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
                .withValue("consensus.handle.maxPrecedingRecords", maxPreceding)
                .withValue("consensus.handle.maxFollowingRecords", MAX_CHILDREN)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addPreceding(config, LIMITED_CHILD_RECORDS);
        recordListBuilder.addPreceding(config, LIMITED_CHILD_RECORDS);

        // then
        assertThatThrownBy(() -> recordListBuilder.addPreceding(config, LIMITED_CHILD_RECORDS))
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
    }

    @Test
    void testRevertSinglePreceding() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(base);
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then, we will find all three records exist, none will be reverted.
        assertThat(records).hasSize(3);
        assertThat(records.get(2)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(OK)
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK)
                .hasNoParent();
        assertCreatedRecord(records.get(2)).hasNonce(0).hasNoParent();
    }

    @Test
    void testAddSingleReversiblePreceding() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
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
    void testAddMultipleReversiblePrecedingRecords() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
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
    void testAddTooManyReversiblePrecedingRecordsFails() {
        // given
        final var maxPreceding = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.handle.maxPrecedingRecords", maxPreceding)
                .withValue("consensus.handle.maxFollowingRecords", MAX_CHILDREN)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addReversiblePreceding(config);
        recordListBuilder.addReversiblePreceding(config);

        // then
        assertThatThrownBy(() -> recordListBuilder.addPreceding(config, LIMITED_CHILD_RECORDS))
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
    }

    @Test
    void testRevertSingleReversiblePreceding() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(base);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then, we will find all three records exist, none will be reverted.
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(1)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(0))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasNoParent();
    }

    @Test
    void testRevertMultipleReversiblePreceding() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var preceding2 =
                recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        preceding2.status(ACCOUNT_ID_DOES_NOT_EXIST);
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(base);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(4);
        assertThat(records.get(3)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(3)).hasNonce(0).hasResponseCode(OK).hasNoParent();
        assertCreatedRecord(records.get(0))
                .nanosBefore(3, result.userTransactionRecord())
                .hasNonce(3)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(ACCOUNT_ID_DOES_NOT_EXIST) // Keeps it's error response code
                .hasNoParent();
        assertCreatedRecord(records.get(2))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasNoParent();
    }

    @Test
    void testRevertingChildDoesNotRevertPreceding() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child = recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(child);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then, we will find all three records exist, none will be reverted.
        assertThat(records).hasSize(3);
        assertThat(records.get(1)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(1)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(0))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK)
                .hasNoParent();
        assertCreatedRecord(records.get(2))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(OK)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testRevertMultipleMixedPreceding() {
        // given
        final var maxPreceding = 4L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.handle.maxPrecedingRecords", maxPreceding)
                .withValue("consensus.handle.maxFollowingRecords", MAX_CHILDREN)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        recordListBuilder.addPreceding(config, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());
        recordListBuilder.addReversiblePreceding(config).transaction(simpleCryptoTransfer());
        recordListBuilder.addPreceding(config, LIMITED_CHILD_RECORDS).transaction(simpleCryptoTransfer());
        recordListBuilder.addReversiblePreceding(config).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(base);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(5);
        assertThat(records.get(4)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(4)).hasNonce(0).hasResponseCode(OK).hasNoParent();
        assertCreatedRecord(records.get(0))
                .nanosBefore(4, result.userTransactionRecord())
                .hasNonce(4)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosBefore(3, result.userTransactionRecord())
                .hasNonce(3)
                .hasResponseCode(OK)
                .hasNoParent();
        assertCreatedRecord(records.get(2))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasNoParent();
        assertCreatedRecord(records.get(3))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK)
                .hasNoParent();
    }

    @Test
    void testAddSingleChild() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
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
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
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
                .withValue("consensus.handle.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.handle.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.addChild(config, CHILD);
        recordListBuilder.addChild(config, CHILD);

        // then
        assertThatThrownBy(() -> recordListBuilder.addChild(config, CHILD))
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
    }

    @Test
    void testAddPrecedingAndChildRecords() throws IOException {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var builder = addUserTransaction(recordListBuilder);
        final var txnId = builder.transactionID();
        // when
        final var first = simpleCryptoTransferWithNonce(txnId, 2);
        final var second = simpleCryptoTransferWithNonce(txnId, 1);
        final var fourth = simpleCryptoTransferWithNonce(txnId, 3);
        final var fifth = simpleCryptoTransferWithNonce(txnId, 4);
        // mixing up preceding vs. following, but within which, in order
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(fourth);
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(first);
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(second);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(fifth);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // then
        assertThat(records).hasSize(5);
        assertThat(records.get(2)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasNoParent()
                .hasSignedTransaction(first);
        assertCreatedRecord(records.get(1))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasNoParent()
                .hasSignedTransaction(second);
        assertCreatedRecord(records.get(2)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(3))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(3)
                .hasParent(result.userTransactionRecord())
                .hasSignedTransaction(fourth);
        assertCreatedRecord(records.get(4))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(4)
                .hasParent(result.userTransactionRecord())
                .hasSignedTransaction(fifth);
    }

    @Test
    void testRevertSingleChild() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildrenOf(base);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
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
        final var child1 = recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        final var child3 = recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        child3.status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildrenOf(child1);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
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
    void testAddRemovableChildWithSuppressedRecord() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder
                .addRemovableChildWithExternalizationCustomizer(
                        CONFIGURATION, SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER)
                .transaction(simpleCryptoTransfer());
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
    void testAddTooManyRemovableChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.handle.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.handle.maxFollowingRecords", maxChildren)
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
        final var baseTxnId = base.transactionID();
        final var revertedTx = simpleCryptoTransferWithNonce(baseTxnId, 1);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(revertedTx);

        // when
        recordListBuilder.revertChildrenOf(base);
        final var remainingTx = simpleCryptoTransferWithNonce(baseTxnId, 1);
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
                .hasSignedTransaction(remainingTx)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testRevertMultipleRemovableChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        final var baseTxnId = base.transactionID();
        final var child1Tx = simpleCryptoTransferWithNonce(baseTxnId, 1);
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(child1Tx);
        recordListBuilder
                .addRemovableChild(CONFIGURATION)
                .transaction(simpleCryptoTransferWithNonce(baseTxnId, 1)); // will be removed
        recordListBuilder
                .addRemovableChild(CONFIGURATION)
                .transaction(simpleCryptoTransfer()) // will be removed
                .status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildrenOf(child1);
        final var remainingTx = simpleCryptoTransferWithNonce(baseTxnId, 2);
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
                .hasSignedTransaction(child1Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(OK)
                .hasSignedTransaction(remainingTx)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void testRevertMultipleMixedChildren() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var base = addUserTransaction(recordListBuilder);
        final var baseTxnId = base.transactionID();

        final var child1Tx = simpleCryptoTransferWithNonce(baseTxnId, 1);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(child1Tx);
        final var child2Tx = simpleCryptoTransferWithNonce(baseTxnId, 2);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(child2Tx);
        final var child3Tx = simpleCryptoTransferWithNonce(baseTxnId, 3);
        final var child3 = recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(child3Tx);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer()); // will be removed
        final var child5Tx = simpleCryptoTransferWithNonce(baseTxnId, 4);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(child5Tx); // will revert
        final var child6Tx = simpleCryptoTransferWithNonce(baseTxnId, 5);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(child6Tx); // will revert
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer()); // will be removed

        // when
        recordListBuilder.revertChildrenOf(child3);
        final var child8Tx = simpleCryptoTransferWithNonce(baseTxnId, 6);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(child8Tx);
        final var child9Tx = simpleCryptoTransferWithNonce(baseTxnId, 7);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(child9Tx);
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
                .hasSignedTransaction(child1Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2) // second child
                .hasResponseCode(
                        OK) // child3's children were reverted, second child comes before, so it is not affected
                .hasSignedTransaction(child2Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(3))
                .nanosAfter(3, result.userTransactionRecord())
                .hasNonce(3) // third child. The children of this was were reverted
                .hasResponseCode(OK) // child3's children were reverted, third child is not affected
                .hasSignedTransaction(child3Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(4))
                // child4 was removed, but for mono-service fidelity we "smooth" the gap in consensus times
                .nanosAfter(4, result.userTransactionRecord())
                .hasNonce(4) // child5 gets the 4th nonce since child4 was removed
                .hasResponseCode(REVERTED_SUCCESS)
                .hasSignedTransaction(child5Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(5))
                .nanosAfter(5, result.userTransactionRecord()) // immediately after child5
                .hasNonce(5) // child6 gets the 5th nonce since child4 was removed
                .hasResponseCode(REVERTED_SUCCESS)
                .hasSignedTransaction(child6Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(6))
                .nanosAfter(6, result.userTransactionRecord())
                .hasNonce(6)
                .hasResponseCode(OK)
                .hasSignedTransaction(child8Tx)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(7))
                .nanosAfter(7, result.userTransactionRecord())
                .hasNonce(7)
                .hasResponseCode(OK)
                .hasSignedTransaction(child9Tx)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void revertChildrenFrom_NullCheckpoint_ShouldThrowException() {
        final var builder = new RecordListBuilder(Instant.now());
        assertThrows(NullPointerException.class, () -> builder.revertChildrenFrom(null));
    }

    @Test
    void revertChildrenFrom_CheckpointWithNoChildren_ShouldUseUserTransactionRecord() {
        // given
        final var recordListBuilder = new RecordListBuilder(Instant.now());
        final var checkpoint = new RecordListCheckPoint(null, null);
        addUserTransaction(recordListBuilder);

        // when
        recordListBuilder.revertChildrenFrom(checkpoint);

        // then
        final var result = recordListBuilder.build();
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0)).isSameAs(result.userTransactionRecord());
    }

    @Test
    void revertChildrenFrom_NotFound_PrecedingInList() {
        // given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        addUserTransaction(recordListBuilder);

        // When
        final var followingChild =
                recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        final var nonExistentPreceding = new SingleTransactionRecordBuilderImpl(Instant.EPOCH);
        final var recordListCheckPoint = new RecordListCheckPoint(nonExistentPreceding, followingChild);

        // then
        assertThatThrownBy(() -> recordListBuilder.revertChildrenFrom(recordListCheckPoint))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revertChildrenFrom_Correctly_Revert_ChildrenBothWays() {
        // Given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var builder = addUserTransaction(recordListBuilder);
        final var txnId = builder.transactionID();

        final var first = simpleCryptoTransferWithNonce(txnId, 2);
        final var second = simpleCryptoTransferWithNonce(txnId, 1);
        final var third = simpleCryptoTransferWithNonce(txnId, 3);
        final var fourth = simpleCryptoTransferWithNonce(txnId, 4);
        // mixing up preceding vs. following, but within which, in order
        var following = recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(third);
        var preceding = recordListBuilder
                .addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS)
                .transaction(first);
        recordListBuilder.addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS).transaction(second);
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(fourth);

        final var recordListCheckPoint = new RecordListCheckPoint(preceding, following);

        // When
        recordListBuilder.revertChildrenFrom(recordListCheckPoint);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then
        assertThat(records).hasSize(5);
        assertThat(records.get(2)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasNoParent()
                .hasSignedTransaction(first);
        assertCreatedRecord(records.get(1))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasNoParent()
                .hasSignedTransaction(second);
        assertCreatedRecord(records.get(2)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(3))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(3)
                .hasParent(result.userTransactionRecord())
                .hasSignedTransaction(third);
        assertCreatedRecord(records.get(4))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(4)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasParent(result.userTransactionRecord())
                .hasSignedTransaction(fourth);
    }

    @Test
    void revertChildrenFrom_Correctly_Revert_FollowingChild() {
        // Given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);

        addUserTransaction(recordListBuilder);
        final var followingChild =
                recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(simpleCryptoTransfer());
        final var recordListCheckPoint = new RecordListCheckPoint(null, followingChild);

        // When
        recordListBuilder.revertChildrenFrom(recordListCheckPoint);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then the following child should be reverted
        assertThat(records).hasSize(3);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasResponseCode(OK).hasNoParent();
        assertCreatedRecord(records.get(1))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasResponseCode(OK)
                .hasParent(result.userTransactionRecord());
        assertCreatedRecord(records.get(2))
                .nanosAfter(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasParent(result.userTransactionRecord());
    }

    @Test
    void revertChildrenFrom_Correctly_Revert_RemovableFollowingChild() {
        // Given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);

        addUserTransaction(recordListBuilder);
        final var followingChild =
                recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var recordListCheckPoint = new RecordListCheckPoint(null, followingChild);

        // When
        recordListBuilder.revertChildrenFrom(recordListCheckPoint);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then the following child should have been removed from the list
        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0)).hasNonce(0).hasResponseCode(OK).hasNoParent();
        assertCreatedRecord(records.get(1)).hasResponseCode(OK).hasParent(result.userTransactionRecord());
    }

    @Test
    void revertChildrenFrom_Correctly_Revert_RemovablePrecedingChild() {
        // Given
        final var consensusTime = Instant.now();
        final var recordListBuilder = new RecordListBuilder(consensusTime);
        final var builder = addUserTransaction(recordListBuilder);
        final var txnId = builder.transactionID();

        final var first = simpleCryptoTransferWithNonce(txnId, 2);
        final var second = simpleCryptoTransferWithNonce(txnId, 1);
        final var third = simpleCryptoTransferWithNonce(txnId, 3);

        // mixing up preceding vs. following, but within which, in order
        var preceding = recordListBuilder
                .addPreceding(CONFIGURATION, LIMITED_CHILD_RECORDS)
                .transaction(first);
        recordListBuilder.addReversiblePreceding(CONFIGURATION).transaction(second);
        var following = recordListBuilder.addChild(CONFIGURATION, CHILD).transaction(third);

        final var recordListCheckPoint = new RecordListCheckPoint(preceding, following);

        // When
        recordListBuilder.revertChildrenFrom(recordListCheckPoint);
        final var result = recordListBuilder.build();
        final var records = result.records();

        // Then
        assertThat(records).hasSize(4);
        assertThat(records.get(2)).isSameAs(result.userTransactionRecord());
        assertCreatedRecord(records.get(0))
                .nanosBefore(2, result.userTransactionRecord())
                .hasNonce(2)
                .hasResponseCode(REVERTED_SUCCESS)
                .hasNoParent()
                .hasSignedTransaction(first);
        assertCreatedRecord(records.get(1))
                .nanosBefore(1, result.userTransactionRecord())
                .hasNonce(1)
                .hasNoParent()
                .hasSignedTransaction(second);
        assertCreatedRecord(records.get(2)).hasNonce(0).hasNoParent();
        assertCreatedRecord(records.get(3))
                .nanosAfter(1, result.userTransactionRecord())
                .hasNonce(3)
                .hasParent(result.userTransactionRecord())
                .hasSignedTransaction(third);
    }

    private SingleTransactionRecordBuilderImpl addUserTransaction(final RecordListBuilder builder) {
        final var start = Instant.now().minusSeconds(60);
        final var txnId = TransactionID.newBuilder()
                .accountID(ALICE.accountID())
                .transactionValidStart(asTimestamp(start))
                .build();
        return builder.userTransactionRecordBuilder()
                .transaction(simpleCryptoTransfer())
                .transactionID(txnId);
    }

    private TransactionRecordAssertions assertCreatedRecord(final SingleTransactionRecord record) {
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
            final Timestamp otherTimestamp = otherRecord.transactionRecord().consensusTimestampOrThrow();
            final int actualOffset = EXPECTED_CHILD_NANO_INCREMENT + nanos;
            final Timestamp expectedTimestamp = otherTimestamp
                    .copyBuilder()
                    .nanos(otherTimestamp.nanos() + actualOffset)
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

        public TransactionRecordAssertions hasSignedTransaction(@NonNull final Transaction tx) {
            assertThat(record.transaction().signedTransactionBytes()).isEqualTo(tx.signedTransactionBytes());
            return this;
        }
    }
}
