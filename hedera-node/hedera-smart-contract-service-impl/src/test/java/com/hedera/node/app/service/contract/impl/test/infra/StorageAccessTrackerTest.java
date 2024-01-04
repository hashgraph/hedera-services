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

package com.hedera.node.app.service.contract.impl.test.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.StorageAccess;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import java.util.List;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class StorageAccessTrackerTest {
    private final StorageAccessTracker subject = new StorageAccessTracker();

    @Test
    void onlyFirstReadIsTracked() {
        subject.trackIfFirstRead(123L, UInt256.ONE, UInt256.MIN_VALUE);
        subject.trackIfFirstRead(123L, UInt256.ONE, UInt256.MAX_VALUE);

        final var merged = subject.getReadsMergedWith(List.of());

        assertEquals(1, merged.size());
        final var accesses = merged.get(0);
        assertEquals(123L, accesses.contractNumber());
        final var accesses123 = accesses.accesses();
        assertEquals(1, accesses123.size());
        final var firstAccess = accesses123.get(0);
        assertEquals(UInt256.ONE, firstAccess.key());
        assertEquals(UInt256.MIN_VALUE, firstAccess.value());
        assertNull(firstAccess.writtenValue());
    }

    @Test
    void writesTakePrecedenceOverReads() {
        subject.trackIfFirstRead(123L, UInt256.ONE, UInt256.MIN_VALUE);

        final var writes = new StorageAccesses(
                123L, List.of(StorageAccess.newWrite(UInt256.ONE, UInt256.MIN_VALUE, UInt256.MAX_VALUE)));

        final var merged = subject.getReadsMergedWith(List.of(writes));
        assertEquals(1, merged.size());
        final var accesses = merged.get(0);
        assertEquals(123L, accesses.contractNumber());
        final var accesses123 = accesses.accesses();
        assertEquals(1, accesses123.size());
        final var firstAccess = accesses123.get(0);
        assertEquals(UInt256.ONE, firstAccess.key());
        assertEquals(UInt256.MIN_VALUE, firstAccess.value());
        assertEquals(UInt256.MAX_VALUE, firstAccess.writtenValue());
    }

    @Test
    void getsExpectedReads() {
        subject.trackIfFirstRead(123L, UInt256.ONE, UInt256.MIN_VALUE);
        subject.trackIfFirstRead(456L, UInt256.MAX_VALUE, UInt256.ONE);

        final var reads = subject.getJustReads();

        assertEquals(reads, subject.getReadsMergedWith(List.of()));
    }
}
