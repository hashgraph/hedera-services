/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.crypto.RunningHash;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;

public class RecordsRunningHashLeafSerdeTest
        extends SelfSerializableDataTest<RecordsRunningHashLeaf> {
    @Override
    protected Class<RecordsRunningHashLeaf> getType() {
        return RecordsRunningHashLeaf.class;
    }

    @Override
    protected RecordsRunningHashLeaf getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextRecordsRunningHashLeaf();
    }

    @Override
    protected Optional<BiConsumer<RecordsRunningHashLeaf, RecordsRunningHashLeaf>>
            customAssertEquals() {
        return Optional.of(RecordsRunningHashLeafSerdeTest::assertEqualLeaves);
    }

    public static void assertEqualLeaves(
            final RecordsRunningHashLeaf a, final RecordsRunningHashLeaf b) {
        try {
            assertEqualRunningHashes(a.getRunningHash(), b.getRunningHash());
            assertEqualRunningHashes(a.getNMinus1RunningHash(), b.getNMinus1RunningHash());
            assertEqualRunningHashes(a.getNMinus2RunningHash(), b.getNMinus2RunningHash());
            assertEqualRunningHashes(a.getNMinus3RunningHash(), b.getNMinus3RunningHash());
        } catch (InterruptedException ex) {
            Assertions.fail("RunningHashes are unequal");
        }
    }

    private static void assertEqualRunningHashes(
            @Nullable final RunningHash a, @Nullable final RunningHash b)
            throws InterruptedException {
        if (a == b) {
            return;
        } else if (a == null || b == null) {
            Assertions.fail("A null hash cannot be equal to a non-null hash");
        } else {
            assertEquals(a.getHash(), b.getHash());
        }
    }
}
