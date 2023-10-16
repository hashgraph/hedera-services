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

import static org.apache.tuweni.units.bigints.UInt256.MIN_VALUE;
import static org.apache.tuweni.units.bigints.UInt256.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatedHederaEvmAccountTest {
    private final Address newAddress = Address.fromHexString("0x000000000000000000000000000000000000066e");

    private static final long newBalance = 200_000L;
    private static final int newNonce = 2;
    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private UpdateTrackingAccount<?> subject;

    @Mock
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    @BeforeEach
    void setUp() {
        subject = new UpdateTrackingAccount<>(address, null);
        subject.setBalance(Wei.ONE);
        subject.setNonce(1L);
        subject.setEvmEntityAccess(hederaEvmEntityAccess);
    }

    @Test
    void testConstructor() {
        UpdateTrackingAccount<?> newSubject = new UpdateTrackingAccount<>(newAddress, null);
        assertEquals(newSubject.getAddress(), newAddress);
    }

    @Test
    void addressChanges() {
        assertEquals(address, subject.getAddress());
    }

    @Test
    void addressHash() {
        assertEquals(Hash.hash(address), subject.getAddressHash());
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
        assertEquals(Bytes.EMPTY, subject.getCode());
    }

    @Test
    void getCodeHash() {
        assertEquals(Hash.hash(Bytes.EMPTY), subject.getCodeHash());
    }

    @Test
    void getStorageValue() {
        subject.setStorageValue(MIN_VALUE, MIN_VALUE);
        assertEquals(ZERO, subject.getStorageValue(MIN_VALUE));
    }

    @Test
    void getStorageValueFromDb() {
        given(hederaEvmEntityAccess.getStorage(address, MIN_VALUE)).willReturn(MIN_VALUE);
        assertEquals(ZERO, subject.getStorageValue(MIN_VALUE));
    }

    @Test
    void getOriginalStorageValue() {
        subject = new UpdateTrackingAccount<>(address, null);
        assertEquals(ZERO, subject.getOriginalStorageValue(MIN_VALUE));
    }

    @Test
    void storageEntriesFrom() {
        assertThrows(UnsupportedOperationException.class, () -> subject.storageEntriesFrom(Bytes32.ZERO, 0));
    }

    @Test
    void getUpdatedStorage() {
        subject.setStorageValue(MIN_VALUE, MIN_VALUE);
        assertEquals(MIN_VALUE, subject.getUpdatedStorage().get(MIN_VALUE));
    }

    @Test
    void clearStorage() {
        subject.setStorageValue(MIN_VALUE, MIN_VALUE);
        subject.clearStorage();
        assertEquals(0, subject.getUpdatedStorage().size());
    }

    @Test
    void setCode() {
        subject.setCode(Bytes.EMPTY);
        assertEquals(Bytes.EMPTY, subject.getCode());
    }
}
