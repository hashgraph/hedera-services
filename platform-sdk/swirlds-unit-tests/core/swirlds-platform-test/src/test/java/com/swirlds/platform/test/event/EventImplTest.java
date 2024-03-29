/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event;

import static com.swirlds.platform.test.fixtures.event.EventImplTestUtils.createEventImpl;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.test.utils.EqualsVerifier;
import java.util.List;
import org.junit.jupiter.api.Test;

public class EventImplTest {

    @Test
    void validateEqualsHashCodeCompareTo() {
        final List<EventImpl> list = EqualsVerifier.generateObjects(
                random -> createEventImpl(
                        TestingEventBuilder.builder(random).setGeneration(random.nextLong(0, Long.MAX_VALUE)),
                        null,
                        null),
                new long[] {1, 1, 2});
        assertTrue(EqualsVerifier.verifyEqualsHashCode(list.get(0), list.get(1), list.get(2)));
        assertTrue(EqualsVerifier.verifyCompareTo(list.get(0), list.get(1), list.get(2)));
    }
}
