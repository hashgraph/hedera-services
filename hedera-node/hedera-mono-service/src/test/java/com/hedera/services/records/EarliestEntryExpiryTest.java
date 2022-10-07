/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.records;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class EarliestEntryExpiryTest {
    @Test
    void equalityWorks() {
        final var ere = new EarliestRecordExpiry(5L, asAccount("0.0.5"));

        assertEquals(ere, ere);
        assertNotEquals(ere, new Object());
        assertEquals(ere, new EarliestRecordExpiry(5L, asAccount("0.0.5")));
        assertNotEquals(ere, new EarliestRecordExpiry(6L, asAccount("0.0.5")));
        assertNotEquals(ere, new EarliestRecordExpiry(5L, asAccount("0.0.6")));
        final var equalsForcedCallResult = ere.equals(null);
        assertFalse(equalsForcedCallResult);
    }

    @Test
    void toStringWorks() {
        final var ere = new EarliestRecordExpiry(5L, asAccount("0.0.5"));

        assertEquals("EarliestRecordExpiry{id=0.0.5, earliestExpiry=5}", ere.toString());
    }

    @Test
    void hashCodeWorks() {
        final var ere = new EarliestRecordExpiry(5L, asAccount("0.0.5"));
        final var expectedHashCode = 1072481;

        assertEquals(expectedHashCode, ere.hashCode());
    }

    @Test
    void comparisonWorks() {
        final var ere = new EarliestRecordExpiry(5L, asAccount("0.0.5"));
        final var ere2 = new EarliestRecordExpiry(4L, asAccount("0.0.1"));
        assertEquals(1, ere.compareTo(ere2));
    }
}
