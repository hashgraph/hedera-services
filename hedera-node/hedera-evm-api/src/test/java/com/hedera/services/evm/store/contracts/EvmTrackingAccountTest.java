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

import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmTrackingAccountTest {
    private static final long newBalance = 200_000L;
    private static final int constNonce = 2;
    private EvmTrackingAccount subject;

    @BeforeEach
    void setUp() {
        subject = new EvmTrackingAccount(1, Wei.ONE);
    }

    @Test
    void mirrorsBalanceChangesInNonNullTrackingAccounts() {
        subject.setBalance(Wei.of(newBalance));
        assertEquals(newBalance, subject.getBalance().toLong());
    }

    @Test
    void getNonceReturnsExpectedValue() {
        subject.setNonce(constNonce);
        assertEquals(constNonce, subject.getNonce());
    }
}
