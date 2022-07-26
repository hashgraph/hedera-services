/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.ethereum;

import static com.hedera.services.ethereum.TestingConstants.CHAINID_TESTNET;
import static com.hedera.services.ethereum.TestingConstants.TINYBARS_2_IN_WEIBARS;
import static com.hedera.services.ethereum.TestingConstants.TINYBARS_57_IN_WEIBARS;
import static com.hedera.services.ethereum.TestingConstants.TRUFFLE0_ADDRESS;
import static com.hedera.services.ethereum.TestingConstants.TRUFFLE0_PRIVATE_ECDSA_KEY;
import static com.hedera.services.ethereum.TestingConstants.TRUFFLE0_PUBLIC_ECDSA_KEY;
import static com.hedera.services.ethereum.TestingConstants.TRUFFLE1_ADDRESS;
import static com.hedera.services.ethereum.TestingConstants.TRUFFLE1_PRIVATE_ECDSA_KEY;
import static com.hedera.services.ethereum.TestingConstants.ZERO_BYTES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EthTxSigsTest {

    @Test
    void signsLegacyUnprotectedNull() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        null,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        assertArrayEquals(null, signedTx.chainId());
        assertArrayEquals(new byte[] {27}, signedTx.v());
        assertNotNull(tx.r());
        assertNotNull(tx.s());
    }

    @Test
    void signsLegacyUnprotectedZeroChainId() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        ZERO_BYTES,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        assertArrayEquals(ZERO_BYTES, signedTx.chainId());
        assertArrayEquals(new byte[] {27}, signedTx.v());
        assertNotNull(tx.r());
        assertNotNull(tx.s());
    }

    @Test
    void doesntSignEIP2930() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.EIP2930,
                        ZERO_BYTES,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        assertThrows(
                IllegalArgumentException.class,
                () -> EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY));
    }

    @Test
    void signsEIP1559() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.EIP1559,
                        ZERO_BYTES,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        assertNotNull(signedTx.r());
        assertNotNull(signedTx.s());
    }

    @Test
    void signsLegacyProtected() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        CHAINID_TESTNET,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        assertArrayEquals(CHAINID_TESTNET, signedTx.chainId());
        assertArrayEquals(new byte[] {2, 116}, signedTx.v());
    }

    @Test
    void extractAddress() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        CHAINID_TESTNET,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        EthTxSigs sigs = EthTxSigs.extractSignatures(signedTx);

        assertArrayEquals(TRUFFLE0_ADDRESS, sigs.address());
        assertArrayEquals(TRUFFLE0_PUBLIC_ECDSA_KEY, sigs.publicKey());
    }

    @Test
    void equalsToStringHashCode() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        CHAINID_TESTNET,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0]);

        EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
        EthTxData signedTxAgain = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
        EthTxData signedTx1 = EthTxSigs.signMessage(tx, TRUFFLE1_PRIVATE_ECDSA_KEY);

        EthTxSigs sigs = EthTxSigs.extractSignatures(signedTx);
        EthTxSigs sigsAgain = EthTxSigs.extractSignatures(signedTxAgain);
        EthTxSigs sigs1 = EthTxSigs.extractSignatures(signedTx1);

        assertEquals(sigs.toString(), sigsAgain.toString());
        assertNotEquals(sigs.toString(), sigs1.toString());

        assertDoesNotThrow(sigs::hashCode);
        assertDoesNotThrow(sigsAgain::hashCode);
        assertDoesNotThrow(sigs1::hashCode);

        assertEquals(sigs, sigsAgain);
        assertNotEquals(sigs, sigs1);
    }

    @Test
    void badSignatureVerification() {
        byte[] allFs = new byte[32];
        Arrays.fill(allFs, (byte) -1);

        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        CHAINID_TESTNET,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        3,
                        new byte[0],
                        allFs,
                        allFs);

        assertThrows(IllegalArgumentException.class, () -> EthTxSigs.extractSignatures(tx));
    }

    @Test
    void badSignatureExtract() {
        var tx =
                new EthTxData(
                        null,
                        EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                        CHAINID_TESTNET,
                        1,
                        TINYBARS_57_IN_WEIBARS,
                        TINYBARS_2_IN_WEIBARS,
                        TINYBARS_57_IN_WEIBARS,
                        1_000_000L,
                        TRUFFLE1_ADDRESS,
                        BigInteger.ZERO,
                        ZERO_BYTES,
                        ZERO_BYTES,
                        1,
                        new byte[0],
                        new byte[32],
                        new byte[32]);

        assertThrows(IllegalArgumentException.class, () -> EthTxSigs.extractSignatures(tx));
    }

    @Test
    void extractsAddress() {
        // good recovery
        assertArrayEquals(
                TRUFFLE0_ADDRESS, EthTxSigs.recoverAddressFromPubKey(TRUFFLE0_PUBLIC_ECDSA_KEY));

        // failed recovery
        assertNull(EthTxSigs.recoverAddressFromPubKey(TRUFFLE0_PRIVATE_ECDSA_KEY));
    }
}
