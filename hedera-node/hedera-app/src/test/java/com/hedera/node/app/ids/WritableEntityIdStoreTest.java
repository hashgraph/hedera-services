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

package com.hedera.node.app.ids;

import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WritableEntityIdStoreTest {
    private final AtomicReference<EntityNumber> nextEntityNumber = new AtomicReference<>();
    private final WritableSingletonState<EntityNumber> entityIdState =
            new WritableSingletonStateBase<>(ENTITY_ID_STATE_KEY, nextEntityNumber::get, nextEntityNumber::set);
    private final WritableStates writableStates = new MapWritableStates(Map.of(ENTITY_ID_STATE_KEY, entityIdState));

    private final WritableEntityIdStore subject = new WritableEntityIdStore(writableStates);

    @Test
    void peeksAndIncrementsAsExpected() {
        assertEquals(1, subject.peekAtNextNumber());
        assertEquals(1, subject.incrementAndGet());
        assertEquals(2, subject.peekAtNextNumber());
        assertEquals(2, subject.incrementAndGet());
    }
}
