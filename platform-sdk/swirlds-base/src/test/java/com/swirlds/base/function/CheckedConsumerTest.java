/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckedConsumerTest {

    @Test
    void testOf() {
        // given
        final List<String> list = new ArrayList<>();
        final CheckedConsumer<String, RuntimeException> consumer = CheckedConsumer.of(s -> list.add(s));

        // when
        consumer.accept("test");

        // then
        assertEquals(1, list.size());
        assertTrue(list.contains("test"));
    }

    @Test
    void testOfNull() {
        assertThrows(NullPointerException.class, () -> CheckedConsumer.of(null));
    }
}
