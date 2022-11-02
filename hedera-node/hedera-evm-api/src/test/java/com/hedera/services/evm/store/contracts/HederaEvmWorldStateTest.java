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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.services.evm.contracts.execution.EvmProperties;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HederaEvmWorldStateTest {

    @Mock private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock private EvmProperties evmProperties;

    @Mock private AbstractCodeCache abstractCodeCache;

    private HederaEvmWorldState subject;

    @BeforeEach
    void setUp() {
        subject =
                new HederaEvmWorldState(hederaEvmEntityAccess, evmProperties, abstractCodeCache) {
                    @Override
                    public HederaEvmWorldUpdater updater() {
                        return null;
                    }
                };
    }

    @Test
    void rootHash() {
        assertEquals(Hash.EMPTY, subject.rootHash());
    }

    @Test
    void frontierRootHash() {
        assertEquals(Hash.EMPTY, subject.frontierRootHash());
    }

    @Test
    void streamAccounts() {
        assertNull(subject.streamAccounts(Bytes32.ZERO, 1));
    }
}
