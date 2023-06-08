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

package com.hedera.node.app.spi.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.swirlds.common.system.DualState;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableFreezeStoreTest {
    @Mock
    private DualState dualState;

    private static final Instant freezeTime = Instant.ofEpochSecond(1_234_567L, 890);

    @Test
    void constructorCreatesFreezeState() {
        final WritableFreezeStore store = new WritableFreezeStore(dualState);
        assertNotNull(store);
    }

    @Test
    void testNullConstructorArgs() {
        assertThrows(NullPointerException.class, () -> new WritableFreezeStore(null));
        assertThrows(NullPointerException.class, () -> new ReadableFreezeStoreImpl(null));
    }

    @Test
    void testFreezeTime() {
        given(dualState.getFreezeTime()).willReturn(freezeTime);
        final WritableFreezeStore store = new WritableFreezeStore(dualState);
        store.freezeTime(freezeTime);
        assertEquals(freezeTime, store.freezeTime());
    }

    @Test
    void testNullFreezeTime() {
        final WritableFreezeStore store = new WritableFreezeStore(dualState);
        store.freezeTime(null);
        assertNull(store.freezeTime());
    }

    @Test
    void testLastFrozenTime() {
        given(dualState.getLastFrozenTime()).willReturn(freezeTime);
        final WritableFreezeStore store = new WritableFreezeStore(dualState);
        assertEquals(freezeTime, store.lastFrozenTime());
    }
}
