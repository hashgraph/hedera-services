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

package com.hedera.node.app.state.recordcache;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.node.app.blocks.BlockItemsTranslator;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.TranslationContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockRecordSourceTest {
    @Mock
    private BlockItemsTranslator recordTranslator;

    @Mock
    private Consumer<BlockItem> action;

    @Mock
    private TranslationContext translationContext;

    private BlockRecordSource subject;

    @Test
    void actionSeesAllItems() {
        subjectWith(List.of(
                new BlockStreamBuilder.Output(List.of(BlockItem.DEFAULT, BlockItem.DEFAULT), translationContext),
                new BlockStreamBuilder.Output(List.of(BlockItem.DEFAULT), translationContext)));

        subject.forEachItem(action);

        verify(action, times(3)).accept(BlockItem.DEFAULT);
    }

    private void subjectWith(@NonNull final List<BlockStreamBuilder.Output> outputs) {
        subject = new BlockRecordSource(recordTranslator, outputs);
    }
}
