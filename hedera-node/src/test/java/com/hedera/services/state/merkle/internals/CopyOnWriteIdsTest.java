/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.store.models.Id;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CopyOnWriteIdsTest {
    private static final List<long[]> someIds =
            List.of(new long[] {3, 2, 1}, new long[] {4, 2, 1}, new long[] {5, 2, 1});
    private static final List<long[]> someMoreIds =
            List.of(new long[] {3, 2, 1}, new long[] {2, 0, 0}, new long[] {98, 0, 0});

    @Test
    void usesCopyOnWriteSemantics() {
        final var a = new CopyOnWriteIds();
        a.add(someIds);
        final var aCopy = a.copy();
        final var aRepr = "[1.2.4, 1.2.5]";
        final var aCopyRepr = "[0.0.2, 1.2.3, 1.2.3, 1.2.4, 1.2.5, 0.0.98]";

        a.remove(listHas(someMoreIds));
        aCopy.add(someMoreIds);

        assertEquals(aRepr, a.toReadableIdList());
        assertEquals(aCopyRepr, aCopy.toReadableIdList());
    }

    @Test
    void containsWorks() {
        final var present = new Id(1, 2, 4);
        final var absent = new Id(1, 2, 666);

        final var subject = new CopyOnWriteIds();
        subject.add(someIds);

        assertTrue(subject.contains(present));
        assertFalse(subject.contains(absent));
    }

    @Test
    void degenerateEqualsWorks() {
        final var a = new CopyOnWriteIds(new long[] {1, 2, 3, 4, 5, 6});
        final var b = a;

        assertEquals(a, b);
        assertNotEquals(new Object(), a);
        assertNotEquals(null, a);
    }

    @Test
    void toStringWorks() {
        final var desired = "CopyOnWriteIds{ids=[3.2.1, 6.5.4]}";
        final var a = new CopyOnWriteIds(new long[] {1, 2, 3, 4, 5, 6});

        assertEquals(desired, a.toString());
    }

    private Predicate<long[]> listHas(final List<long[]> l) {
        return nativeId -> {
            for (int i = 0, n = l.size(); i < n; i++) {
                if (Arrays.equals(nativeId, l.get(i))) {
                    return true;
                }
            }
            return false;
        };
    }
}
