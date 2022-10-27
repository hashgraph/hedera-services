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

import static org.junit.Assert.assertEquals;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatedHederaEvmAccountTest {
    private static final long newBalance = 200_000L;
    private static final int constNonce = 2;
    private final Address address =
            Address.fromHexString("0x000000000000000000000000000000000000077e");
    private UpdatedHederaEvmAccount subject;

    @BeforeEach
    void setUp() {
        subject = new UpdatedHederaEvmAccount(address, 1, Wei.ONE);
    }

    @Test
    void balanceChanges() {
        subject.setBalance(Wei.of(newBalance));
        assertEquals(newBalance, subject.getBalance().toLong());
    }
}
