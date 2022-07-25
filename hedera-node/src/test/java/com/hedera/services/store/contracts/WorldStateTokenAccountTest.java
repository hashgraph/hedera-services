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
package com.hedera.services.store.contracts;

import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_BYTECODE_PATTERN;
import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_CALL_REDIRECT_CONTRACT_BINARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldStateTokenAccountTest {
    private static final Address pretendTokenAddr = Address.BLS12_G1MULTIEXP;

    private WorldStateTokenAccount subject = new WorldStateTokenAccount(pretendTokenAddr);

    @Test
    void getsExpectedCode() {
        final var expected = expectedCodeBytes();
        final var firstActual = subject.getCode();
        final var secondActual = subject.getCode();
        assertEquals(expected, firstActual);
        assertSame(firstActual, secondActual);
    }

    @Test
    void alwaysHasCode() {
        assertTrue(subject.hasCode());
    }

    @Test
    void neverEmpty() {
        assertFalse(subject.isEmpty());
    }

    @Test
    void hasTokenNonce() {
        assertEquals(WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE, subject.getNonce());
    }

    @Test
    void balanceAlwaysZero() {
        final var balance = subject.getBalance();
        assertEquals(Wei.of(0), balance);
    }

    @Test
    void addressHashEmpty() {
        assertEquals(Hash.EMPTY, subject.getAddressHash());
    }

    @Test
    void expectedCodeHash() {
        final var bytecode = expectedCodeBytes();
        final var expected = Hash.hash(bytecode);
        final var actual = subject.getCodeHash();
        assertEquals(expected, actual);
    }

    @Test
    void allStorageIsZero() {
        final var storageValue = subject.getStorageValue(UInt256.ONE);
        final var origStorageValue = subject.getOriginalStorageValue(UInt256.ONE);
        assertEquals(UInt256.ZERO, storageValue);
        assertEquals(UInt256.ZERO, origStorageValue);
    }

    @Test
    void storageEntriesStreamStillNotSupported() {
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> subject.storageEntriesFrom(null, 0));
    }

    private Bytes expectedCodeBytes() {
        return Bytes.fromHexString(
                TOKEN_CALL_REDIRECT_CONTRACT_BINARY.replace(
                        TOKEN_BYTECODE_PATTERN, pretendTokenAddr.toUnprefixedHexString()));
    }
}
