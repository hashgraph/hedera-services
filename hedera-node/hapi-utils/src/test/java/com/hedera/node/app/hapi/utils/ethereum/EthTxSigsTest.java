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

package com.hedera.node.app.hapi.utils.ethereum;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType.LEGACY_ETHEREUM;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.CHAINID_TESTNET;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TINYBARS_2_IN_WEIBARS;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TINYBARS_57_IN_WEIBARS;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TRUFFLE0_ADDRESS;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TRUFFLE0_PRIVATE_ECDSA_KEY;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TRUFFLE0_PUBLIC_ECDSA_KEY;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TRUFFLE1_ADDRESS;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.TRUFFLE1_PRIVATE_ECDSA_KEY;
import static com.hedera.node.app.hapi.utils.ethereum.TestingConstants.ZERO_BYTES;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EthTxSigsTest {
    private final SplittableRandom random = new SplittableRandom();

    @Test
    void leftPadsRIfNecessary() {
        final var r = nextBytes(30);
        final var s = nextBytes(32);
        final var sig = EthTxSigs.concatLeftPadded(r, s);
        assertArrayEquals(r, Arrays.copyOfRange(sig, 2, 32));
        assertArrayEquals(s, Arrays.copyOfRange(sig, 32, 64));
    }

    @Test
    void issue4180CaseStudyPasses() {
        final var expectedFromAddress = CommonUtils.unhex("5052672db37ad6f222b8de61665c6bb76acfefaa");
        final var ethTxData = EthTxData.populateEthTxData(
                CommonUtils.unhex(
                        "f88b718601d1a94a20008316e360940000000000000000000000000000000002e8a7b980a4fdacd5760000000000000000000000000000000000000000000000000000000000000002820273a076398dfd239dcdf69aeef7328a5e8cc69ef1b4ba5cca56eab1af06d7959923599f8194cd217b301cbdbdcd05b3572c411ec9333af39c98af8c5c9de45ddb05c5"));
        final var ethTxSigs = EthTxSigs.extractSignatures(ethTxData);
        assertArrayEquals(expectedFromAddress, ethTxSigs.address());
    }

    @Test
    void leftPadsSIfNecessary() {
        final var r = nextBytes(32);
        final var s = nextBytes(29);
        final var sig = EthTxSigs.concatLeftPadded(r, s);
        assertArrayEquals(r, Arrays.copyOfRange(sig, 0, 32));
        assertArrayEquals(s, Arrays.copyOfRange(sig, 35, 64));
    }

    @Test
    void leftPadsBothIfNecessary() {
        final var r = nextBytes(31);
        final var s = nextBytes(29);
        final var sig = EthTxSigs.concatLeftPadded(r, s);
        assertArrayEquals(r, Arrays.copyOfRange(sig, 1, 32));
        assertArrayEquals(s, Arrays.copyOfRange(sig, 35, 64));
    }

    @Test
    void leftPadsNeitherIfUnnecessary() {
        final var r = nextBytes(32);
        final var s = nextBytes(32);
        final var sig = EthTxSigs.concatLeftPadded(r, s);
        assertArrayEquals(r, Arrays.copyOfRange(sig, 0, 32));
        assertArrayEquals(s, Arrays.copyOfRange(sig, 32, 64));
    }

    public byte[] nextBytes(final int n) {
        final var ans = new byte[n];
        random.nextBytes(ans);
        return ans;
    }

    @Test
    void signsLegacyUnprotectedNull() {
        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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

        final EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        assertArrayEquals(null, signedTx.chainId());
        assertArrayEquals(new byte[] {27}, signedTx.v());
        assertNotNull(tx.r());
        assertNotNull(tx.s());
    }

    @Test
    void signsLegacyUnprotectedZeroChainId() {
        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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

        final EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        Assertions.assertArrayEquals(ZERO_BYTES, signedTx.chainId());
        assertArrayEquals(new byte[] {27}, signedTx.v());
        assertNotNull(tx.r());
        assertNotNull(tx.s());
    }

    @Test
    void doesntSignEIP2930() {
        final var tx = new EthTxData(
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

        assertThrows(IllegalArgumentException.class, () -> EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY));
    }

    @Test
    void signsEIP1559() {
        final var tx = new EthTxData(
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

        final EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        assertNotNull(signedTx.r());
        assertNotNull(signedTx.s());
    }

    @Test
    void signsLegacyProtected() {
        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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

        final EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        Assertions.assertArrayEquals(CHAINID_TESTNET, signedTx.chainId());
        assertArrayEquals(new byte[] {2, 116}, signedTx.v());
    }

    @Test
    void extractAddress() {
        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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

        final EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

        final EthTxSigs sigs = EthTxSigs.extractSignatures(signedTx);

        Assertions.assertArrayEquals(TRUFFLE0_ADDRESS, sigs.address());
        Assertions.assertArrayEquals(TRUFFLE0_PUBLIC_ECDSA_KEY, sigs.publicKey());
    }

    @Test
    void equalsToStringHashCode() {
        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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

        final EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
        final EthTxData signedTxAgain = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
        final EthTxData signedTx1 = EthTxSigs.signMessage(tx, TRUFFLE1_PRIVATE_ECDSA_KEY);

        final EthTxSigs sigs = EthTxSigs.extractSignatures(signedTx);
        final EthTxSigs sigsAgain = EthTxSigs.extractSignatures(signedTxAgain);
        final EthTxSigs sigs1 = EthTxSigs.extractSignatures(signedTx1);

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
        final byte[] allFs = new byte[32];
        Arrays.fill(allFs, (byte) -1);

        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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
        final var tx = new EthTxData(
                null,
                LEGACY_ETHEREUM,
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
        Assertions.assertArrayEquals(TRUFFLE0_ADDRESS, recoverAddressFromPubKey(TRUFFLE0_PUBLIC_ECDSA_KEY));

        // failed recovery
        assertArrayEquals(new byte[0], recoverAddressFromPubKey(TRUFFLE0_PRIVATE_ECDSA_KEY));
    }
}
