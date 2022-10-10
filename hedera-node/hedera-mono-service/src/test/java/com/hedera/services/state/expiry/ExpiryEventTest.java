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
package com.hedera.services.state.expiry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExpiryEventTest {
    ExpiryEvent expiryEvent = new ExpiryEvent("something", 1_234_567L);

    @Test
    void expiryEventToStringWorks() {
        var desired = "ExpiryEvent[id=something, expiry=1234567]";

        // when:
        var actual = expiryEvent.toString();

        // then:
        assertEquals(desired, actual);
    }

    @Test
    void expiryEventIsExpiredAt() {
        // when:
        boolean expected = false;
        boolean actual = expiryEvent.isExpiredAt(1L);

        // then:
        assertEquals(expected, actual);
    }
}
