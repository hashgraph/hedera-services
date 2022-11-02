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
package com.hedera.services.evm.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.services.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import java.util.Collections;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractLedgerEvmWorldUpdaterTest {
    private final Address address =
            Address.fromHexString("0x000000000000000000000000000000000000077e");
    MockAccountAccessor accountAccessor = new MockAccountAccessor();
    AbstractLedgerEvmWorldUpdater abstractLedgerEvmWorldUpdater =
            new AbstractLedgerEvmWorldUpdater(accountAccessor);

    @Test
    void accountTests() {
        assertNull(abstractLedgerEvmWorldUpdater.createAccount(address, 1, Wei.ONE));
        assertNull(abstractLedgerEvmWorldUpdater.getAccount(address));
        assertEquals(Collections.emptyList(), abstractLedgerEvmWorldUpdater.getTouchedAccounts());
        abstractLedgerEvmWorldUpdater.commit();
        assertEquals(
                Collections.emptyList(),
                abstractLedgerEvmWorldUpdater.getDeletedAccountAddresses());
    }

    @Test
    void getAccount() {
        UpdatedHederaEvmAccount updatedHederaEvmAccount = new UpdatedHederaEvmAccount(address);

        assertEquals(
                updatedHederaEvmAccount.getAddress(),
                abstractLedgerEvmWorldUpdater.get(address).getAddress());
    }

    @Test
    void updaterTest() {
        assertEquals(Optional.empty(), abstractLedgerEvmWorldUpdater.parentUpdater());
        assertNull(abstractLedgerEvmWorldUpdater.updater());
    }
}
