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
package com.hedera.node.app.service.evm.store.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.models.MockAccountAccessor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmWorldStateTest {

    @Mock private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock private EvmProperties evmProperties;

    @Mock private AbstractCodeCache abstractCodeCache;

    private final Address address =
            Address.fromHexString("0x000000000000000000000000000000000000077e");
    final long balance = 1_234L;

    MockAccountAccessor accountAccessor = new MockAccountAccessor();

    private HederaEvmWorldState subject;

    private HederaEvmWorldState subject2;

    @BeforeEach
    void setUp() {
        subject = new HederaEvmWorldState(hederaEvmEntityAccess, evmProperties, abstractCodeCache);

        subject2 =
                new HederaEvmWorldState(
                        hederaEvmEntityAccess, evmProperties, abstractCodeCache, accountAccessor);
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
        assertThrows(UnsupportedOperationException.class, () -> subject.streamAccounts(null, 10));
    }

    @Test
    void returnsNullForNull() {
        assertNull(subject.get(null));
    }

    @Test
    void returnsNull() {
        assertNull(subject.get(address));
    }

    @Test
    void returnsWorldStateAccountt() {
        final var address = Address.RIPEMD160;
        given(hederaEvmEntityAccess.getBalance(address)).willReturn(balance);
        given(hederaEvmEntityAccess.isUsable(any())).willReturn(true);

        final var account = subject.get(address);

        assertTrue(account.getCode().isEmpty());
        assertFalse(account.hasCode());
    }

    @Test
    void returnsHederaEvmWorldStateTokenAccount() {
        final var address = Address.RIPEMD160;
        given(hederaEvmEntityAccess.isTokenAccount(address)).willReturn(true);
        given(evmProperties.isRedirectTokenCallsEnabled()).willReturn(true);

        final var account = subject.get(address);

        assertFalse(account.getCode().isEmpty());
        assertTrue(account.hasCode());
    }

    @Test
    void returnsNull2() {
        final var address = Address.RIPEMD160;
        given(hederaEvmEntityAccess.isTokenAccount(address)).willReturn(true);
        given(evmProperties.isRedirectTokenCallsEnabled()).willReturn(false);

        assertNull(subject.get(address));
    }

    @Test
    void updater() {
        var actualSubject = subject2.updater();
        assertEquals(0, actualSubject.getSbhRefund());
    }
}
