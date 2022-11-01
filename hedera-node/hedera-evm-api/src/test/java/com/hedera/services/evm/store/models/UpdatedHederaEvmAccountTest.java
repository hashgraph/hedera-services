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

import java.util.Collections;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatedHederaEvmAccountTest {
    private final Address newAddress =
            Address.fromHexString("0x000000000000000000000000000000000000066e");

    private static final long newBalance = 200_000L;
    private static final int newNonce = 2;
    private final Address address =
            Address.fromHexString("0x000000000000000000000000000000000000077e");
    private UpdatedHederaEvmAccount subject;

    @BeforeEach
    void setUp() {
        subject = new UpdatedHederaEvmAccount(address, 1, Wei.ONE);
    }

    @Test
    void testConstructor() {
        UpdatedHederaEvmAccount newSubject = new UpdatedHederaEvmAccount(newAddress);
        assertEquals(newSubject.getAddress(), newAddress);
    }

    @Test
    void addressChanges() {
        subject.setAddress(newAddress);
        assertEquals(newAddress, subject.getAddress());
    }

    @Test
    void addressHash() {
        assertNull(subject.getAddressHash());
    }

    @Test
    void nonceChanges() {
        subject.setNonce(newNonce);
        assertEquals(newNonce, subject.getNonce());
    }

    @Test
    void balanceChanges() {
        subject.setBalance(Wei.of(newBalance));
        assertEquals(newBalance, subject.getBalance().toLong());
    }

    @Test
    void getCode() {
        assertNull(subject.getCode());
    }

    @Test
    void getCodeHash() {
        assertNull(subject.getCodeHash());
    }

    @Test
    void getStorageValue() {
        assertNull(subject.getStorageValue(UInt256.MIN_VALUE));
    }

    @Test
    void getOriginalStorageValue() {
        assertNull(subject.getOriginalStorageValue(UInt256.MIN_VALUE));
    }

    @Test
    void storageEntriesFrom() {
        assertEquals(Collections.emptyNavigableMap(), subject.storageEntriesFrom(Bytes32.ZERO, 0));
    }
}
