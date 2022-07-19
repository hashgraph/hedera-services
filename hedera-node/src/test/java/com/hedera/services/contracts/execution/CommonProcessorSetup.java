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
package com.hedera.services.contracts.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static org.mockito.BDDMockito.given;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class CommonProcessorSetup {
    static void setup(GasCalculator gasCalculator) {
        given(gasCalculator.getVeryLowTierGasCost()).willReturn(3L);
        given(gasCalculator.getLowTierGasCost()).willReturn(5L);
        given(gasCalculator.getMidTierGasCost()).willReturn(8L);
        given(gasCalculator.getBaseTierGasCost()).willReturn(2L);
        given(gasCalculator.getBlockHashOperationGasCost()).willReturn(20L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(160L);
        given(gasCalculator.getColdSloadCost()).willReturn(2100L);
        given(gasCalculator.getSloadOperationGasCost()).willReturn(0L);
        given(gasCalculator.getHighTierGasCost()).willReturn(10L);
        given(gasCalculator.getJumpDestOperationGasCost()).willReturn(1L);
        given(gasCalculator.getZeroTierGasCost()).willReturn(0L);
    }
}
