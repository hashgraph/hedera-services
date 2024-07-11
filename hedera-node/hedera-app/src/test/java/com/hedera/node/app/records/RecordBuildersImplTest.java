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

package com.hedera.node.app.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordBuildersImplTest {

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private SavepointStackImpl stack;

    private RecordBuildersImpl subject;

    @BeforeEach
    void setup() {
        subject = new RecordBuildersImpl(stack);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidParameters() {
        assertThatThrownBy(() -> subject.getOrCreate(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.getOrCreate(List.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject.addChildRecordBuilder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.addChildRecordBuilder(List.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject.addRemovableChildRecordBuilder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.addRemovableChildRecordBuilder(List.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAddChildRecordBuilder() {
        final var childRecordBuilder = mock(SingleTransactionRecordBuilderImpl.class);
        given(stack.createBuilder(any(), any(), any())).willReturn(childRecordBuilder);

        final var actual = subject.addChildRecordBuilder(CryptoCreateRecordBuilder.class);

        assertThat(actual).isEqualTo(childRecordBuilder);
    }

    @Test
    void testAddRemovableChildRecordBuilder() {
        final var childRecordBuilder = mock(SingleTransactionRecordBuilderImpl.class);
        given(stack.createBuilder(any(), any(), any())).willReturn(childRecordBuilder);

        final var actual = subject.addRemovableChildRecordBuilder(CryptoCreateRecordBuilder.class);

        assertThat(actual).isEqualTo(childRecordBuilder);
    }
}
