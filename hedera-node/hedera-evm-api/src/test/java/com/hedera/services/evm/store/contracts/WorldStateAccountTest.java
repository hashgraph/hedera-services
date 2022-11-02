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
package com.hedera.services.evm.store.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WorldStateAccountTest {

    @Mock HederaEvmEntityAccess entityAccess;

    MockAbstractCodeCache codeCache = new MockAbstractCodeCache(100, entityAccess);
    ;

    private final Address address =
            Address.fromHexString("0x000000000000000000000000000000000000077e");

    WorldStateAccount subject = new WorldStateAccount(address, Wei.ONE, codeCache, entityAccess);

    @Test
    void getAddress() {
        assertEquals(address, subject.getAddress());
    }

    @Test
    void getAddressHash() {
        assertEquals(Hash.EMPTY, subject.getAddressHash());
    }

    @Test
    void getNonce() {
        assertEquals(0, subject.getNonce());
    }

    @Test
    void getBalance() {
        assertEquals(Wei.ONE, subject.getBalance());
    }
}
