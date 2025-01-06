/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.stack;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.swirlds.state.State;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavepointStackImplBlocksStreamModeTest {
    @Mock
    private State state;

    @Mock
    private StreamBuilder streamBuilder;

    @Mock
    private BoundaryStateChangeListener boundaryStateChangeListener;

    @Mock
    private KVStateChangeListener kvStateChangeListener;

    private SavepointStackImpl subject;

    @BeforeEach
    void setUp() {
        subject = SavepointStackImpl.newRootStack(
                state, 3, 50, boundaryStateChangeListener, kvStateChangeListener, BLOCKS);
    }

    @Test
    void commitsFullStackAsExpected() {
        final var mockChanges = List.of(StateChange.DEFAULT);
        given(kvStateChangeListener.getStateChanges()).willReturn(mockChanges);

        subject.commitTransaction(streamBuilder);

        verify(kvStateChangeListener).reset();
        verify(streamBuilder).stateChanges(mockChanges);
    }

    @Test
    void usesBlockStreamBuilderForChild() {
        final var childBuilder = subject.addChildRecordBuilder(StreamBuilder.class, CONTRACT_CALL);
        assertThat(childBuilder).isInstanceOf(BlockStreamBuilder.class);
    }

    @Test
    void usesBlockStreamBuilderForRemovableChild() {
        final var childBuilder = subject.addRemovableChildRecordBuilder(StreamBuilder.class, CONTRACT_CALL);
        assertThat(childBuilder).isInstanceOf(BlockStreamBuilder.class);
    }

    @Test
    void allCreatedBuildersAreBlockStreamBuilders() {
        assertThat(subject.createIrreversiblePrecedingBuilder()).isInstanceOf(BlockStreamBuilder.class);
        assertThat(subject.createRemovableChildBuilder()).isInstanceOf(BlockStreamBuilder.class);
        assertThat(subject.createReversibleChildBuilder()).isInstanceOf(BlockStreamBuilder.class);
    }
}
