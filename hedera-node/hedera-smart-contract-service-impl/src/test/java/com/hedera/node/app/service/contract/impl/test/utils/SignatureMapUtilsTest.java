/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.utils;

import static com.hedera.node.app.service.contract.impl.utils.SignatureMapUtils.preprocessEcdsaSignatures;
import static com.hedera.node.app.service.contract.impl.utils.SignatureMapUtils.validChainId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SignatureMapUtils class.
 */
class SignatureMapUtilsTest {

    @Test
    void testPreprocessEcdsaSignatures() {
        final var keyBytes = Bytes.wrap(new byte[65]);
        final var sigMap = new SignatureMap.Builder()
                .sigPair(new SignaturePair.Builder().ecdsaSecp256k1(keyBytes).build())
                .build();
        final var resultSigMap = preprocessEcdsaSignatures(sigMap, 0);
        assertThat(resultSigMap.sigPair().get(0).hasEcdsaSecp256k1()).isTrue();
        // Assert truncated to 64 bytes
        assertThat(resultSigMap.sigPair().get(0).ecdsaSecp256k1().length()).isEqualTo(64);
    }

    @Test
    void testPreprocessEcdsaSignaturesForEdSigs() {
        final var keyBytes = Bytes.wrap(new byte[65]);
        final var sigMap = new SignatureMap.Builder()
                .sigPair(new SignaturePair.Builder().ed25519(keyBytes).build())
                .build();
        final var resultSigMap = preprocessEcdsaSignatures(sigMap, 0);
        assertThat(resultSigMap.sigPair().get(0).hasEd25519()).isTrue();
        // Assert not truncated
        assertThat(resultSigMap.sigPair().get(0).ed25519().length()).isEqualTo(65);
    }

    @Test
    void testPreprocessEcdsaSignaturesWrongChainId() {
        final var chainId = 199;
        final var ecSig = new byte[65];
        ecSig[64] = (byte) (35 + (chainId * 3));
        final var keyBytes = Bytes.wrap(ecSig);
        final var sigMap = new SignatureMap.Builder()
                .sigPair(new SignaturePair.Builder().ecdsaSecp256k1(keyBytes).build())
                .build();
        assertThrows(IllegalArgumentException.class, () -> preprocessEcdsaSignatures(sigMap, 0));
    }

    @Test
    void testWrongChainId() {
        final var chainId = 199;
        final var ecSig = new byte[65];
        ecSig[64] = (byte) (35 + (chainId * 3));
        assertThat(validChainId(ecSig, chainId)).isFalse();
    }

    @Test
    void testCorrectChainId() {
        final var chainId = 199;
        int v = 35 + (chainId * 2);
        // distribute the v value across the last two bytes
        final var ecSig = new byte[66];
        ecSig[65] = (byte) (v & 0xFF);
        v >>= 8;
        ecSig[64] = (byte) (v & 0xFF);
        assertThat(validChainId(ecSig, chainId)).isTrue();
    }

    @Test
    void testChainIdBelow35() {
        final var ecSig = new byte[65];
        ecSig[64] = 34;
        assertThat(validChainId(ecSig, 0)).isTrue();
    }
}
