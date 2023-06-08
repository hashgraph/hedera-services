/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.state;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.service.contract.impl.state.TokenEvmAccount;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenEvmAccountTest {
    private static final Address TOKEN_ADDRESS = Address.fromHexString("0000000000000000000000000000ffffffffffff");
    private static final Bytes SOME_PRETEND_CODE = Bytes.wrap("<NOT-REALLY-CODE>");
    private static final Hash SOME_PRETEND_CODE_HASH =
            Hash.wrap(Bytes32.wrap("<NOT-REALLY-BYTECODE-HASH-12345>".getBytes()));

    @Mock
    private EvmFrameState state;

    private TokenEvmAccount subject;

    @BeforeEach
    void setUp() {
        subject = new TokenEvmAccount(TOKEN_ADDRESS, state);
    }

    @Test
    void usesGivenAddress() {
        assertSame(TOKEN_ADDRESS, subject.getAddress());
    }

    @Test
    void usesCodeFromState() {
        final var code = pbjToTuweniBytes(SOME_PRETEND_CODE);

        given(state.getTokenRedirectCode(TOKEN_ADDRESS)).willReturn(code);

        assertSame(code, subject.getCode());
    }

    @Test
    void usesHashFromState() {
        given(state.getTokenRedirectCodeHash(TOKEN_ADDRESS)).willReturn(SOME_PRETEND_CODE_HASH);

        assertSame(SOME_PRETEND_CODE_HASH, subject.getCodeHash());
    }

    @Test
    void usesFixedNonce() {
        assertEquals(-1, subject.getNonce());
    }

    @Test
    void alwaysHasZeroWei() {
        assertSame(Wei.ZERO, subject.getBalance());
    }

    @Test
    void storageValuesAreAlwaysZero() {
        assertSame(UInt256.ZERO, subject.getStorageValue(UInt256.ONE));
        assertSame(UInt256.ZERO, subject.getOriginalStorageValue(UInt256.ONE));
    }

    @Test
    void neverEmpty() {
        assertFalse(subject.isEmpty());
    }

    @Test
    void usesSelfAsPutativeMutable() {
        assertSame(subject, subject.getMutable());
    }

    @Test
    void doesNotSupportMutators() {
        assertThrows(UnsupportedOperationException.class, () -> subject.setNonce(1234));
        assertThrows(UnsupportedOperationException.class, () -> subject.setCode(pbjToTuweniBytes(SOME_PRETEND_CODE)));
        assertThrows(UnsupportedOperationException.class, () -> subject.setStorageValue(UInt256.ONE, UInt256.ONE));
        assertThrows(UnsupportedOperationException.class, subject::clearStorage);
        assertThrows(UnsupportedOperationException.class, subject::getUpdatedStorage);
    }
}
