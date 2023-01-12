/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.app.service.evm.store.contracts.HederaEvmStackedWorldStateUpdater;
import java.util.Collections;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmStackedWorldStateUpdaterTest {
    private final Address address =
            Address.fromHexString("0x000000000000000000000000000000000000077e");
    MockAccountAccessor accountAccessor = new MockAccountAccessor();
    MockTokenAccessor tokenAccessor = new MockTokenAccessor();
    MockEntityAccess entityAccess = new MockEntityAccess();
    HederaEvmStackedWorldStateUpdater hederaEvmStackedWorldStateUpdater =
            new HederaEvmStackedWorldStateUpdater(accountAccessor, entityAccess, tokenAccessor);

    @Test
    void accountTests() {
        assertNull(hederaEvmStackedWorldStateUpdater.createAccount(address, 1, Wei.ONE));
        assertEquals(
                Wei.of(100L), hederaEvmStackedWorldStateUpdater.getAccount(address).getBalance());
        assertEquals(
                Collections.emptyList(), hederaEvmStackedWorldStateUpdater.getTouchedAccounts());
        hederaEvmStackedWorldStateUpdater.commit();
        assertEquals(
                Collections.emptyList(),
                hederaEvmStackedWorldStateUpdater.getDeletedAccountAddresses());
    }

    @Test
    void getAccount() {
        final UpdatedHederaEvmAccount updatedHederaEvmAccount =
                new UpdatedHederaEvmAccount(address);

        assertEquals(
                updatedHederaEvmAccount.getAddress(),
                hederaEvmStackedWorldStateUpdater.get(address).getAddress());
    }

    @Test
    void updaterTest() {
        assertEquals(Optional.empty(), hederaEvmStackedWorldStateUpdater.parentUpdater());
        assertEquals(
                hederaEvmStackedWorldStateUpdater, hederaEvmStackedWorldStateUpdater.updater());
    }

    @Test
    void namedelegatesTokenAccountTest() {
        final var someAddress = Address.BLS12_MAP_FP2_TO_G2;
        assertFalse(hederaEvmStackedWorldStateUpdater.isTokenAddress(someAddress));
    }
}
