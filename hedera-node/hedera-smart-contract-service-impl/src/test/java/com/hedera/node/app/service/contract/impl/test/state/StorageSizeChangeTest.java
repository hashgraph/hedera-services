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

package com.hedera.node.app.service.contract.impl.test.state;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import org.junit.jupiter.api.Test;

class StorageSizeChangeTest {
    @Test
    void neverAddsNegativeNumberOfSlots() {
        final var changes =
                new StorageSizeChange(ContractID.newBuilder().contractNum(1L).build(), 5, 2);
        assertEquals(0, changes.numAdded());
        assertEquals(-3, changes.netChange());
    }
}
