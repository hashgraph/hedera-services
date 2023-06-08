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

package com.hedera.node.app.records;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REVERTED_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordListBuilderTest {

    private static final Instant CONSENSUS_NOW = Instant.parse("2000-01-01T00:00:00Z");

    private static final long MAX_PRECEDING = 3;
    private static final long MAX_CHILDREN = 10;

    private static final Configuration CONFIGURATION = new HederaTestConfigBuilder()
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
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);

        // when
        final var recordListBuilder = new RecordListBuilder(base);

        // then
        assertThat(recordListBuilder.builders()).containsExactly(base);
    }

    @Test
    void testAddSinglePreceding() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var preceding = recordListBuilder.addPreceding(CONFIGURATION);

        // then
        assertThat(preceding.consensusNow())
                .isAfterOrEqualTo(base.consensusNow().minusNanos(MAX_PRECEDING))
                .isBefore(base.consensusNow());
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(recordListBuilder.builders()).containsExactly(preceding, base);
    }

    @Test
    void testAddTooManyPrecedingsFails() {
        // given
        final var maxPreceding = 2L;
        final var config = new HederaTestConfigBuilder()
                .withValue("consensus.message.maxPrecedingRecords", maxPreceding)
                .withValue("consensus.message.maxFollowingRecords", MAX_CHILDREN)
                .getOrCreateConfig();
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var preceding1 = recordListBuilder.addPreceding(config);
        final var preceding2 = recordListBuilder.addPreceding(config);
        assertThatThrownBy(() -> recordListBuilder.addPreceding(config)).isInstanceOf(IndexOutOfBoundsException.class);

        // then
        assertThat(preceding1.consensusNow())
                .isAfterOrEqualTo(base.consensusNow().minusNanos(maxPreceding))
                .isBefore(preceding2.consensusNow());
        assertThat(preceding2.consensusNow()).isBefore(base.consensusNow());
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(recordListBuilder.builders()).containsExactly(preceding1, preceding2, base);
    }

    @Test
    void testAddSingleChild() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child = recordListBuilder.addChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child.consensusNow())
                .isAfter(base.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(recordListBuilder.builders()).containsExactly(base, child);
    }

    @Test
    void testAddTooManyChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = new HederaTestConfigBuilder()
                .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.message.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child1 = recordListBuilder.addChild(CONFIGURATION);
        final var child2 = recordListBuilder.addChild(CONFIGURATION);
        assertThatThrownBy(() -> recordListBuilder.addChild(config)).isInstanceOf(IndexOutOfBoundsException.class);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(maxChildren));
        assertThat(recordListBuilder.builders()).containsExactly(base, child1, child2);
    }

    @Test
    void testRevertSingleChild() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addChild(CONFIGURATION);

        // when
        recordListBuilder.revertChildRecordBuilders(base);
        final var child2 = recordListBuilder.addChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.status()).isEqualTo(REVERTED_SUCCESS);
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child2.status()).isEqualTo(OK);
        assertThat(recordListBuilder.builders()).containsExactly(base, child1, child2);
    }

    @Test
    void testRevertMultipleChildren() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addChild(CONFIGURATION);
        final var child2 = recordListBuilder.addChild(CONFIGURATION);
        final var child3 = recordListBuilder.addChild(CONFIGURATION);
        child3.status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildRecordBuilders(child1);
        final var child4 = recordListBuilder.addChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.status()).isEqualTo(OK);
        assertThat(child2.consensusNow()).isAfter(child1.consensusNow());
        assertThat(child2.status()).isEqualTo(REVERTED_SUCCESS);
        assertThat(child3.consensusNow()).isAfter(child2.consensusNow());
        assertThat(child3.status()).isEqualTo(ACCOUNT_ID_DOES_NOT_EXIST);
        assertThat(child4.consensusNow())
                .isAfter(child3.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child4.status()).isEqualTo(OK);
        assertThat(recordListBuilder.builders()).containsExactly(base, child1, child2, child3, child4);
    }

    @Test
    void testAddSingleRemovableChild() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child = recordListBuilder.addRemovableChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child.consensusNow())
                .isAfter(base.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(recordListBuilder.builders()).containsExactly(base, child);
    }

    @Test
    void testAddTooManyRemovableChildrenFails() {
        // given
        final var maxChildren = 2L;
        final var config = new HederaTestConfigBuilder()
                .withValue("consensus.message.maxPrecedingRecords", MAX_PRECEDING)
                .withValue("consensus.message.maxFollowingRecords", maxChildren)
                .getOrCreateConfig();
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION);
        final var child2 = recordListBuilder.addRemovableChild(CONFIGURATION);
        assertThatThrownBy(() -> recordListBuilder.addRemovableChild(config))
                .isInstanceOf(IndexOutOfBoundsException.class);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(maxChildren));
        assertThat(recordListBuilder.builders()).containsExactly(base, child1, child2);
    }

    @Test
    void testRevertSingleRemovableChild() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);
        recordListBuilder.addRemovableChild(CONFIGURATION);

        // when
        recordListBuilder.revertChildRecordBuilders(base);
        final var child2 = recordListBuilder.addRemovableChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child2.consensusNow())
                .isAfter(base.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child2.status()).isEqualTo(OK);
        assertThat(recordListBuilder.builders()).containsExactly(base, child2);
    }

    @Test
    void testRevertMultipleRemovableChildren() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION);
        recordListBuilder.addRemovableChild(CONFIGURATION);
        final var child3 = recordListBuilder.addRemovableChild(CONFIGURATION);
        child3.status(ACCOUNT_ID_DOES_NOT_EXIST);

        // when
        recordListBuilder.revertChildRecordBuilders(child1);
        final var child4 = recordListBuilder.addRemovableChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.status()).isEqualTo(OK);
        assertThat(child4.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child4.status()).isEqualTo(OK);
        assertThat(recordListBuilder.builders()).containsExactly(base, child1, child4);
    }

    @Test
    void testRevertMultipleMixedChildren() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);
        final var child1 = recordListBuilder.addRemovableChild(CONFIGURATION);
        final var child2 = recordListBuilder.addChild(CONFIGURATION);
        final var child3 = recordListBuilder.addChild(CONFIGURATION);
        recordListBuilder.addRemovableChild(CONFIGURATION);
        final var child5 = recordListBuilder.addChild(CONFIGURATION);
        final var child6 = recordListBuilder.addChild(CONFIGURATION);
        recordListBuilder.addRemovableChild(CONFIGURATION);

        // when
        recordListBuilder.revertChildRecordBuilders(child3);
        final var child8 = recordListBuilder.addRemovableChild(CONFIGURATION);
        final var child9 = recordListBuilder.addChild(CONFIGURATION);

        // then
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child1.status()).isEqualTo(OK);
        assertThat(child2.consensusNow()).isAfter(child1.consensusNow());
        assertThat(child2.status()).isEqualTo(OK);
        assertThat(child3.consensusNow()).isAfter(child2.consensusNow());
        assertThat(child3.status()).isEqualTo(OK);
        assertThat(child5.consensusNow()).isAfter(child3.consensusNow());
        assertThat(child5.status()).isEqualTo(REVERTED_SUCCESS);
        assertThat(child6.consensusNow()).isAfter(child5.consensusNow());
        assertThat(child6.status()).isEqualTo(REVERTED_SUCCESS);
        assertThat(child8.consensusNow()).isAfter(child6.consensusNow());
        assertThat(child8.status()).isEqualTo(OK);
        assertThat(child9.consensusNow())
                .isAfter(child8.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(child9.status()).isEqualTo(OK);
        assertThat(recordListBuilder.builders())
                .containsExactly(base, child1, child2, child3, child5, child6, child8, child9);
    }

    @Test
    void testAddMultipleRecordBuilders() {
        // given
        final var base = new SingleTransactionRecordBuilder(CONSENSUS_NOW);
        final var recordListBuilder = new RecordListBuilder(base);

        // when
        final var preceding1 = recordListBuilder.addPreceding(CONFIGURATION);
        final var preceding2 = recordListBuilder.addPreceding(CONFIGURATION);
        final var child1 = recordListBuilder.addChild(CONFIGURATION);
        final var child2 = recordListBuilder.addRemovableChild(CONFIGURATION);

        // then
        assertThat(preceding1.consensusNow())
                .isAfterOrEqualTo(base.consensusNow().minusNanos(MAX_PRECEDING))
                .isBefore(preceding2.consensusNow());
        assertThat(preceding2.consensusNow()).isBefore(base.consensusNow());
        assertThat(base.consensusNow()).isEqualTo(CONSENSUS_NOW);
        assertThat(child1.consensusNow()).isAfter(base.consensusNow());
        assertThat(child2.consensusNow())
                .isAfter(child1.consensusNow())
                .isBeforeOrEqualTo(base.consensusNow().plusNanos(MAX_CHILDREN));
        assertThat(recordListBuilder.builders()).containsExactly(preceding1, preceding2, base, child1, child2);
    }
}
