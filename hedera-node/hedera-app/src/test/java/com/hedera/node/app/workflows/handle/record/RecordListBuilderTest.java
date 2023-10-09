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

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordListBuilderTest implements TransactionFactory {

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
        Assertions.assertThatThrownBy(() -> new RecordListBuilder(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitialRecordListBuilder() {
        // given
        final var base = createBaseRecordBuilder();

        // when
        final var recordListBuilder = new RecordListBuilder(base);

        // then
        assertThat(recordListBuilder.builders()).containsExactly(base);
    }

    @Test
    void testAddSinglePreceding() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var preceding = recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(2);
        assertThat(preceding.consensusNow())
                .isAfterOrEqualTo(base.consensusNow().minusNanos(MAX_PRECEDING))
                .isBefore(base.consensusNow());
        assertThat(preceding.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), preceding, 1);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertThat(base.transactionID().nonce()).isZero();
        assertCreatedRecord(result.get(1), base, 0);
    }

    @Test
    void testAddMultiplePrecedings() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var preceding1 = recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var preceding2 = recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(3);
        assertThat(preceding1.consensusNow())
                .isAfterOrEqualTo(base.consensusNow().minusNanos(MAX_PRECEDING))
                .isBefore(preceding2.consensusNow());
        assertThat(preceding1.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), preceding1, 1);
        assertThat(preceding2.consensusNow()).isBefore(base.consensusNow());
        assertThat(preceding2.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(1), preceding2, 2);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(2), base, 0);
    }

    @Test
    void testAddTooManyPrecedingsFails() {
        // given
        final var maxPreceding = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.message.maxPrecedingRecords", maxPreceding)
                .withValue("consensus.message.maxFollowingRecords", MAX_CHILDREN)
                .getOrCreateConfig();
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        recordListBuilder.addPreceding(config);
        recordListBuilder.addPreceding(config);

        // then
        assertThatThrownBy(() -> recordListBuilder.addPreceding(config)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testAddSingleChild() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(2);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child.consensusNow())
                .isAfter(base.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(1), child, 1);
    }

    @Test
    void testAddMultipleChildren() {
        // given
        final var maxChildren = 2L;
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child1 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child2 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(3);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(1), child1, 1);
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(maxChildren));
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(2), child2, 2);
    }

    @Test
    void testAddTooManyChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.message.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        recordListBuilder.addChild(config);
        recordListBuilder.addChild(config);

        // then
        assertThatThrownBy(() -> recordListBuilder.addChild(config)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testRevertSingleChild() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildRecordBuilders(base);
        final var child2 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(3);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.status()).isEqualTo(REVERTED_SUCCESS);
        assertCreatedRecord(result.get(1), child1, 1);
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child2.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(2), child2, 2);
    }

    @Test
    void testRevertNotFound() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);
        final var nonExistent = new SingleTransactionRecordBuilderImpl(Instant.EPOCH);

        // when
        assertThatThrownBy(() -> recordListBuilder.revertChildRecordBuilders(nonExistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRevertMultipleChildren() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child2 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child3 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        child3.status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildRecordBuilders(child1);
        final var child4 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(5);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(1), child1, 1);
        assertThat(child2.consensusNow()).isAfter(child1.consensusNow());
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child2.status()).isEqualTo(REVERTED_SUCCESS);
        assertCreatedRecord(result.get(2), child2, 2);
        assertThat(child3.consensusNow()).isAfter(child2.consensusNow());
        assertThat(child3.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child3.status()).isEqualTo(ACCOUNT_ID_DOES_NOT_EXIST);
        assertCreatedRecord(result.get(3), child3, 3);
        assertThat(child4.consensusNow())
                .isAfter(child3.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child4.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child4.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(4), child4, 4);
    }

    @Test
    void testAddSingleRemovableChild() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(2);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child.consensusNow())
                .isAfter(base.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(1), child, 1);
    }

    @Test
    void testAddMultipleRemovableChildren() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child2 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(3);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(1), child1, 1);
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(2), child2, 2);
    }

    @Test
    void testAddTooManyRemovableChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.message.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        recordListBuilder.addRemovableChild(CONFIGURATION);
        recordListBuilder.addRemovableChild(CONFIGURATION);

        // then
        assertThatThrownBy(() -> recordListBuilder.addRemovableChild(config))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testRevertSingleRemovableChild() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildRecordBuilders(base);
        final var child2 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(2);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child2.consensusNow())
                .isAfter(base.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child2.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(1), child2, 1);
    }

    @Test
    void testRevertMultipleRemovableChildren() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child3 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        child3.status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildRecordBuilders(child1);
        final var child4 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(3);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(1), child1, 1);
        assertThat(child4.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child4.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child4.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(2), child4, 2);
    }

    @Test
    void testRevertMultipleMixedChildren() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child2 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child3 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child5 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child6 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());

        // when
        recordListBuilder.revertChildRecordBuilders(child3);
        final var child8 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child9 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(8);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(1), child1, 1);
        assertThat(child2.consensusNow()).isAfter(child1.consensusNow());
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child2.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(2), child2, 2);
        assertThat(child3.consensusNow()).isAfter(child2.consensusNow());
        assertThat(child3.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child3.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(3), child3, 3);
        assertThat(child5.consensusNow()).isAfter(child3.consensusNow());
        assertThat(child5.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child5.status()).isEqualTo(REVERTED_SUCCESS);
        assertCreatedRecord(result.get(4), child5, 4);
        assertThat(child6.consensusNow()).isAfter(child5.consensusNow());
        assertThat(child6.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child6.status()).isEqualTo(REVERTED_SUCCESS);
        assertCreatedRecord(result.get(5), child6, 5);
        assertThat(child8.consensusNow()).isAfter(child6.consensusNow());
        assertThat(child8.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child8.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(6), child8, 6);
        assertThat(child9.consensusNow())
                .isAfter(child8.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child9.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertThat(child9.status()).isEqualTo(OK);
        assertCreatedRecord(result.get(7), child9, 7);
    }

    @Test
    void testAddMultipleRecordBuilders() {
        // given
        final var base = createBaseRecordBuilder();
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var preceding1 = recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var preceding2 = recordListBuilder.addPreceding(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child1 = recordListBuilder.addChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var child2 = recordListBuilder.addRemovableChild(CONFIGURATION).transaction(simpleCryptoTransfer());
        final var result = recordListBuilder.build().recordStream().toList();

        // then
        assertThat(result).hasSize(5);
        assertThat(preceding1.consensusNow())
                .isAfterOrEqualTo(base.consensusNow().minusNanos(MAX_PRECEDING))
                .isBefore(preceding2.consensusNow());
        assertThat(preceding1.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(0), preceding1, 1);
        assertThat(preceding2.consensusNow()).isBefore(base.consensusNow());
        assertThat(preceding2.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(1), preceding2, 2);
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(base.parentConsensusTimestamp()).isNull();
        assertCreatedRecord(result.get(2), base, 0);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(3), child1, 3);
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child2.parentConsensusTimestamp()).isEqualTo(CONSENSUS_NOW);
        assertCreatedRecord(result.get(4), child2, 4);
    }

    private SingleTransactionRecordBuilderImpl createBaseRecordBuilder() {
        return new SingleTransactionRecordBuilderImpl(CONSENSUS_NOW)
                .transaction(simpleCryptoTransfer())
                .transactionID(TransactionID.newBuilder().build());
    }

    private void assertCreatedRecord(
            SingleTransactionRecord actual, SingleTransactionRecordBuilderImpl builder, int nonce) {
        assertThat(actual.transactionRecord().consensusTimestamp())
                .isEqualTo(HapiUtils.asTimestamp(builder.consensusNow()));
        if (builder.parentConsensusTimestamp() == null) {
            assertThat(actual.transactionRecord().parentConsensusTimestamp()).isNull();
        } else {
            assertThat(actual.transactionRecord().parentConsensusTimestamp())
                    .isEqualTo(HapiUtils.asTimestamp(builder.parentConsensusTimestamp()));
        }
        assertThat(actual.transactionRecord().transactionID().nonce()).isEqualTo(nonce);
    }
}
