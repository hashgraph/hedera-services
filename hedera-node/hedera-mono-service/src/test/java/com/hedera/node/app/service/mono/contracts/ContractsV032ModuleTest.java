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
package com.hedera.node.app.service.mono.contracts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.SHA256PrecompiledContract;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsV032ModuleTest {

    @Mock MessageFrame frame;
    @Mock WorldUpdater worldUpdater;

    @Test
    void provideAddressValidator() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        final var existingAddress = Address.fromHexString("0x66");
        final var nonExistingAddress = Address.fromHexString("0x55");
        given(worldUpdater.get(existingAddress)).willReturn(mock(Account.class));
        given(worldUpdater.get(nonExistingAddress)).willReturn(null);

        final var precompileAddress = "0x5";
        final var addressValidator =
                ContractsV_0_32Module.provideAddressValidator(
                        Map.of(precompileAddress, new SHA256PrecompiledContract(null)));

        assertFalse(addressValidator.test(nonExistingAddress, frame));
        assertTrue(addressValidator.test(Address.fromHexString(precompileAddress), frame));
        assertTrue(addressValidator.test(existingAddress, frame));
    }
}
