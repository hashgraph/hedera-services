package com.hedera.services.store.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.api.Test;

import static com.hedera.services.store.schedule.ExceptionalScheduleStore.NOOP_SCHEDULE_STORE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionalScheduleStoreTest {
    @Test
    public void allButSetAreUse() {
        // expect:
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.createProvisionally(null, null,null, null, null, null));
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.addSigners(null, null));
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.exists(null));
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.get(null));
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.delete(null));
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.apply(null, null));
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.markAsExecuted(null));
        assertThrows(UnsupportedOperationException.class, NOOP_SCHEDULE_STORE::commitCreation);
        assertThrows(UnsupportedOperationException.class, NOOP_SCHEDULE_STORE::rollbackCreation);
        assertThrows(UnsupportedOperationException.class, NOOP_SCHEDULE_STORE::isCreationPending);
        assertThrows(UnsupportedOperationException.class, () -> NOOP_SCHEDULE_STORE.lookupScheduleId(null, null));
        // and:
        assertDoesNotThrow(() -> NOOP_SCHEDULE_STORE.setAccountsLedger(null));
        assertDoesNotThrow(() -> NOOP_SCHEDULE_STORE.setHederaLedger(null));
    }
}