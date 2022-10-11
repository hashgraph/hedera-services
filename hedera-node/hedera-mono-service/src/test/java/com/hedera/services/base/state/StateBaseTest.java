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
package com.hedera.services.base.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateBaseTest {
    private final String stateKey = "ACCOUNTS_KEY";
    private final Instant lastModifiedTime = Instant.ofEpochSecond(1_234_567L);

    private StateBase subject = new InMemoryStateImpl(stateKey, lastModifiedTime);

    @Test
    void gettersWorkAsExpected() {
        assertEquals(new HashMap<>(), subject.getReadKeys());
        assertEquals(stateKey, subject.getStateKey());
    }

    @Test
    void cachesReadKeys() {
        final var num = EntityNum.fromLong(2L);
        assertEquals(Optional.empty(), subject.get(num));
    }

    @Test
    void throwsIfKeyIsNull() {
        assertThrows(NullPointerException.class, () -> subject.get(null));
    }
}
