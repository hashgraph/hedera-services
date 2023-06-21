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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownContextWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownHapiCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CustomGasChargingTest {
    @Mock
    private HederaEvmAccount sender;

    @Mock
    private HederaEvmAccount relayer;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private GasCalculator gasCalculator;

    private CustomGasCharging subject;

    @BeforeEach
    void setUp() {
        subject = new CustomGasCharging(gasCalculator);
    }

    @Test
    void staticCallsDoNotChargeGas() {
        final var allowanceCharged = subject.chargeGasAllowance(
                sender,
                relayer,
                wellKnownContextWith(blocks, true),
                worldUpdater,
                wellKnownHapiCall());
        assertEquals(0, allowanceCharged);
        verifyNoInteractions(gasCalculator);
    }
}
