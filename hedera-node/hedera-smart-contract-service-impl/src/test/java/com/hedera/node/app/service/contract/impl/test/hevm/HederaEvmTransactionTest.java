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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import org.junit.jupiter.api.Test;

class HederaEvmTransactionTest {
    @Test
    void gasAvailableIsLimitMinusIntrinsic() {
        final var subject = TestHelpers.wellKnownHapiCall();
        assertEquals(GAS_LIMIT - INTRINSIC_GAS, subject.gasAvailable(INTRINSIC_GAS));
    }

    @Test
    void computesUpfrontCostWithoutOverflowConcern() {
        final var subject = TestHelpers.wellKnownHapiCall();
        assertEquals(VALUE + 123L * GAS_LIMIT, subject.upfrontCostGiven(123L));
    }

    @Test
    void computesUpfrontCostWithOverflow() {
        final var subject = TestHelpers.wellKnownHapiCall();
        assertEquals(Long.MAX_VALUE, subject.upfrontCostGiven(Long.MAX_VALUE / (GAS_LIMIT - 1)));
    }

    @Test
    void computesOfferedGasCostWithoutOverflowConcern() {
        final var subject = TestHelpers.wellKnownHapiCall();
        assertEquals(GAS_LIMIT * USER_OFFERED_GAS_PRICE, subject.offeredGasCost());
    }

    @Test
    void computesOfferedGasCostWithOverflow() {
        final var subject = TestHelpers.wellKnownRelayedHapiCallWithGasLimit(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, subject.offeredGasCost());
    }
}
