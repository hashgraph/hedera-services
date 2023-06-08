/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.node.app.service.evm.store.contracts.utils.BytesKey;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeV0;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractCodeCacheTest {
    @Mock
    HederaEvmEntityAccess entityAccess;

    MockAbstractCodeCache codeCache;

    @BeforeEach
    void setup() {
        codeCache = new MockAbstractCodeCache(100, entityAccess);
    }

    @Test
    void successfulCreate() {
        assertNotNull(codeCache.getCache());
    }

    @Test
    void getTriggeringLoad() {
        given(entityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.of("abc".getBytes()));
        Code code = codeCache.getIfPresent(Address.fromHexString("0xabc"));

        assertEquals(
                Hash.fromHexString("0x4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"),
                code.getCodeHash());
    }

    @Test
    void getContractNotFound() {
        given(entityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.EMPTY);
        Code code = codeCache.getIfPresent(Address.fromHexString("0xabc"));

        assertTrue(code.getBytes().isEmpty());
    }

    @Test
    void returnsCachedValue() {
        Address demoAddress = Address.fromHexString("0xabc");
        BytesKey key = new BytesKey(demoAddress.toArray());
        Code code = CodeV0.EMPTY_CODE;

        codeCache.cacheValue(key, code);
        Code codeResult = codeCache.getIfPresent(demoAddress);

        assertEquals(code, codeResult);
        verifyNoInteractions(entityAccess);
    }

    @Test
    void cacheSize() {
        Address demoAddress = Address.fromHexString("0xabc");
        BytesKey key = new BytesKey(demoAddress.toArray());
        Code code = CodeV0.EMPTY_CODE;

        codeCache.cacheValue(key, code);
        assertEquals(1, codeCache.size());
    }

    @Test
    void getTokenCodeReturnsRedirectCode() {
        given(entityAccess.isTokenAccount(any())).willReturn(true);

        assertEquals(
                HederaEvmWorldStateTokenAccount.proxyBytecodeFor(Address.fromHexString("0xabc")),
                codeCache.getIfPresent(Address.fromHexString("0xabc")).getBytes());
    }

    @Test
    void invalidateSuccess() {
        given(entityAccess.fetchCodeIfPresent(any())).willReturn(Bytes.of("abc".getBytes()));

        codeCache.getIfPresent(Address.fromHexString("0xabc"));

        assertDoesNotThrow(() -> codeCache.invalidate(Address.fromHexString("0xabc")));
    }

    @Test
    void bytesKeyEquals() {
        BytesKey key1 = new BytesKey("abc".getBytes());
        BytesKey key2 = new BytesKey("abc".getBytes());
        assertEquals(key1, key2);
        assertEquals(key1, key1);

        // Workaround to sonar code-smells. It should NOT be an error to use assertTrue/assertFalse.
        boolean eq1 = key1.equals(null);
        boolean eq2 = key1.equals("abc");

        assertEquals(eq1, eq2);
    }
}
