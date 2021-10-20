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

import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import static org.mockito.BDDMockito.given;

public class CommonProcessorSetup {
	static void setup(GasCalculator gasCalculator) {
		given(gasCalculator.getVeryLowTierGasCost()).willReturn(Gas.of(3));
		given(gasCalculator.getLowTierGasCost()).willReturn(Gas.of(5));
		given(gasCalculator.getMidTierGasCost()).willReturn(Gas.of(8));
		given(gasCalculator.getBaseTierGasCost()).willReturn(Gas.of(2));
		given(gasCalculator.getBlockHashOperationGasCost()).willReturn(Gas.of(20));
		given(gasCalculator.getWarmStorageReadCost()).willReturn(Gas.of(160));
		given(gasCalculator.getColdSloadCost()).willReturn(Gas.of(2100));
		given(gasCalculator.getSloadOperationGasCost()).willReturn(Gas.ZERO);
		given(gasCalculator.getHighTierGasCost()).willReturn(Gas.of(10));
		given(gasCalculator.getJumpDestOperationGasCost()).willReturn(Gas.of(1));
		given(gasCalculator.getZeroTierGasCost()).willReturn(Gas.ZERO);
	}
}
