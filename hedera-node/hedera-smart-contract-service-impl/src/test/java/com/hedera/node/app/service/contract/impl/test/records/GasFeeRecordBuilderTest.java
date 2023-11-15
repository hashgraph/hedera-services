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

import com.hedera.node.app.service.contract.impl.records.GasFeeRecordBuilder;
import org.junit.jupiter.api.Test;

class GasFeeRecordBuilderTest {
    @Test
    void withGasFeeWorksAsExpected() {
        final var subject = new GasFeeRecordBuilder() {
            private long totalFee = 456L;

            @Override
            public long transactionFee() {
                return totalFee;
            }

            @Override
            public GasFeeRecordBuilder transactionFee(final long transactionFee) {
                totalFee = transactionFee;
                return this;
            }
        };

        assertSame(subject, subject.withTinybarGasFee(123L));
        assertEquals(123L + 456L, subject.transactionFee());
    }
}
