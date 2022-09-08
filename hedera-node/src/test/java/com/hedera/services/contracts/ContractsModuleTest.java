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
package com.hedera.services.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsModuleTest {
    @Mock private GasCalculator gasCalculator;

    @Test
    void logOperationsAreProvided() {
        final var log0 = ContractsV_0_30Operations.provideLog0Operation(gasCalculator);
        final var log1 = ContractsV_0_30Operations.provideLog1Operation(gasCalculator);
        final var log2 = ContractsV_0_30Operations.provideLog2Operation(gasCalculator);
        final var log3 = ContractsV_0_30Operations.provideLog3Operation(gasCalculator);
        final var log4 = ContractsV_0_30Operations.provideLog4Operation(gasCalculator);

        assertEquals("LOG0", log0.getName());
        assertEquals("LOG1", log1.getName());
        assertEquals("LOG2", log2.getName());
        assertEquals("LOG3", log3.getName());
        assertEquals("LOG4", log4.getName());
    }
}
