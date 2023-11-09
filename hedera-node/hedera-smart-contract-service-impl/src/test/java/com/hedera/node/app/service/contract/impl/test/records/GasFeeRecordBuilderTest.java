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

package com.hedera.node.app.service.contract.impl.test.records;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.contract.impl.records.GasFeeRecordBuilder;
import org.junit.jupiter.api.Test;

class GasFeeRecordBuilderTest {
    @Test
    void withGasFeeWorksAsExpected() {
        final var subject = mock(GasFeeRecordBuilder.class);
        doCallRealMethod().when(subject).withGasFee(123L);
        given(subject.transactionFee()).willReturn(456L);
        given(subject.transactionFee(123L + 456L)).willReturn(subject);

        assertSame(subject, subject.withGasFee(123L));
    }
}
