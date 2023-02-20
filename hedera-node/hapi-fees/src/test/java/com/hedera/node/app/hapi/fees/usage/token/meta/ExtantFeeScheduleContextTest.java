/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.token.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExtantFeeScheduleContextTest {
    @Test
    void assertEqualsWork() {
        // when:
        final var subject = new ExtantFeeScheduleContext(1234L, 22);
        final var subject2 = new ExtantFeeScheduleContext(1234L, 22);

        // then:
        assertEquals(subject, subject2);
        assertEquals(subject.hashCode(), subject2.hashCode());
    }

    @Test
    void assertGetters() {
        // when:
        final var subject = new ExtantFeeScheduleContext(1234L, 22);

        // then:
        assertEquals(1234L, subject.expiry());
        assertEquals(22, subject.numBytesInFeeScheduleRepr());
    }
}
