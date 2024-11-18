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

package com.hedera.node.app.workflows.handle.stack.savepoints;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REVERSIBLE;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.TransactionCustomizer.NOOP_TRANSACTION_CUSTOMIZER;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.state.WrappedState;
import com.hedera.node.app.workflows.handle.stack.BuilderSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirstRootSavepointTest {
    @Mock
    private WrappedState state;

    @Mock
    private BuilderSink parentSink;

    private FirstRootSavepoint subject;

    @Test
    void usesBlockStreamBuilderForBlocksStreamMode() {
        givenSubjectWithCapacities(1, 2);

        final var builder = subject.createBuilder(REVERSIBLE, CHILD, NOOP_TRANSACTION_CUSTOMIZER, BLOCKS, false);

        assertThat(builder).isInstanceOf(BlockStreamBuilder.class);
    }

    private void givenSubjectWithCapacities(final int maxPreceding, final int maxFollowing) {
        given(parentSink.precedingCapacity()).willReturn(maxPreceding);
        given(parentSink.followingCapacity()).willReturn(maxFollowing);
        subject = new FirstRootSavepoint(state, parentSink);
    }
}
