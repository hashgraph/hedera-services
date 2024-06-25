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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
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
    private RecordListBuilder recordListBuilder;

    private RecordBuildersImpl subject;

    @BeforeEach
    void setup() {
        final var configuration = HederaTestConfigBuilder.createConfig();

        subject = new RecordBuildersImpl(recordBuilder, recordListBuilder, configuration);
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
    void testGetRecordBuilder() {
        final var actual = subject.getOrCreate(CryptoCreateRecordBuilder.class);
        assertThat(actual).isEqualTo(recordBuilder);
    }

    @Test
    void testAddChildRecordBuilder() {
        final var childRecordBuilder = mock(SingleTransactionRecordBuilderImpl.class);
        when(recordListBuilder.addChild(any(), any())).thenReturn(childRecordBuilder);

        final var actual = subject.addChildRecordBuilder(CryptoCreateRecordBuilder.class);

        assertThat(actual).isEqualTo(childRecordBuilder);
    }

    @Test
    void testAddRemovableChildRecordBuilder() {
        final var childRecordBuilder = mock(SingleTransactionRecordBuilderImpl.class);
        when(recordListBuilder.addRemovableChild(any())).thenReturn(childRecordBuilder);

        final var actual = subject.addRemovableChildRecordBuilder(CryptoCreateRecordBuilder.class);

        assertThat(actual).isEqualTo(childRecordBuilder);
    }

    @Test
    void successCreateRecordListCheckPoint() {
        var precedingRecord = mock(SingleTransactionRecordBuilderImpl.class);
        var childRecord = mock(SingleTransactionRecordBuilderImpl.class);
        given(recordListBuilder.precedingRecordBuilders()).willReturn(List.of(precedingRecord));
        given(recordListBuilder.childRecordBuilders()).willReturn(List.of(childRecord));

        final var actual = subject.createRecordListCheckPoint();

        assertThat(actual).isEqualTo(new RecordListCheckPoint(precedingRecord, childRecord));
    }

    @Test
    void successCreateRecordListCheckPoint_MultipleRecords() {
        var precedingRecord = mock(SingleTransactionRecordBuilderImpl.class);
        var precedingRecord1 = mock(SingleTransactionRecordBuilderImpl.class);
        var childRecord = mock(SingleTransactionRecordBuilderImpl.class);
        var childRecord1 = mock(SingleTransactionRecordBuilderImpl.class);

        given(recordListBuilder.precedingRecordBuilders()).willReturn(List.of(precedingRecord, precedingRecord1));
        given(recordListBuilder.childRecordBuilders()).willReturn(List.of(childRecord, childRecord1));

        final var actual = subject.createRecordListCheckPoint();

        assertThat(actual).isEqualTo(new RecordListCheckPoint(precedingRecord1, childRecord1));
    }

    @Test
    void success_createRecordListCheckPoint_null_values() {
        final var actual = subject.createRecordListCheckPoint();
        assertThat(actual).isEqualTo(new RecordListCheckPoint(null, null));
    }

    @Test
    void revertsAsExpected() {
        final var checkpoint = new RecordListCheckPoint(null, null);
        subject.revertRecordsFrom(checkpoint);
        verify(recordListBuilder).revertChildrenFrom(checkpoint);
    }
}
