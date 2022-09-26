/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.sourcing;

import static com.hedera.test.factories.keys.NodeFactory.ecdsa384Secp256k1;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.sigs.SigFactory.signUnchecked;
import static com.hedera.test.factories.sigs.SigMapGenerator.withAlternatingUniqueAndFullPrefixes;
import static com.hedera.test.factories.txns.SystemDeleteFactory.newSignedSystemDelete;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.keys.KeyTreeLeaf;
import com.hedera.test.factories.sigs.SigFactory;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.SignatureType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PojoSigMapPubKeyToSigBytesTest {
    private final byte[] EMPTY_SIG = {};
    private final KeyTree payerKt =
            KeyTree.withRoot(
                    list(
                            ed25519(true),
                            ecdsa384Secp256k1(true),
                            ed25519(true),
                            ed25519(true),
                            ed25519(true)));
    private final KeyTree otherKt =
            KeyTree.withRoot(list(ed25519(true), ecdsa384Secp256k1(true), ecdsa384Secp256k1(true)));
    private final KeyFactory defaultFactory = KeyFactory.getDefaultInstance();

    @Test
    void defaultMethodsWorkAsExpected() {
        final var subject = mock(PubKeyToSigBytes.class);
        doCallRealMethod().when(subject).forEachUnusedSigWithFullPrefix(any());
        doCallRealMethod().when(subject).resetAllSigsToUnused();
        doCallRealMethod().when(subject).hasAtLeastOneUnusedSigWithFullPrefix();
        assertDoesNotThrow(() -> subject.forEachUnusedSigWithFullPrefix(null));
        assertDoesNotThrow(subject::resetAllSigsToUnused);
        assertFalse(subject::hasAtLeastOneUnusedSigWithFullPrefix);
    }

    @Test
    void getsUnusedFullKeysAndSigs() throws Throwable {
        final var signedTxn =
                newSignedSystemDelete()
                        .payerKt(payerKt)
                        .nonPayerKts(otherKt)
                        .sigMapGen(withAlternatingUniqueAndFullPrefixes())
                        .get();
        final var subject =
                new PojoSigMapPubKeyToSigBytes(
                        SignedTxnAccessor.uncheckedFrom(signedTxn).getSigMap());
        lookupsMatch(
                payerKt,
                defaultFactory,
                CommonUtils.extractTransactionBodyBytes(signedTxn),
                subject);

        final var numUnusedFullPrefixSigs = new AtomicInteger(0);
        assertTrue(subject.hasAtLeastOneUnusedSigWithFullPrefix());
        subject.forEachUnusedSigWithFullPrefix(
                (type, pubKey, sig) -> {
                    numUnusedFullPrefixSigs.getAndIncrement();
                });
        assertEquals(2, numUnusedFullPrefixSigs.get());
    }

    @Test
    void getsNoUnusedFullKeysAndSigs() throws Throwable {
        final var signedTxn =
                newSignedSystemDelete()
                        .payerKt(payerKt)
                        .nonPayerKts(otherKt)
                        .sigMapGen(withAlternatingUniqueAndFullPrefixes())
                        .get();
        final var subject =
                new PojoSigMapPubKeyToSigBytes(
                        SignedTxnAccessor.uncheckedFrom(signedTxn).getSigMap());
        lookupsMatch(
                payerKt,
                defaultFactory,
                CommonUtils.extractTransactionBodyBytes(signedTxn),
                subject);
        lookupsMatch(
                otherKt,
                defaultFactory,
                CommonUtils.extractTransactionBodyBytes(signedTxn),
                subject);

        assertFalse(subject.hasAtLeastOneUnusedSigWithFullPrefix());

        subject.resetAllSigsToUnused();

        assertTrue(subject.hasAtLeastOneUnusedSigWithFullPrefix());
        final var numUnusedFullPrefixSigs = new AtomicInteger(0);
        subject.forEachUnusedSigWithFullPrefix(
                (type, pubKey, sig) -> {
                    numUnusedFullPrefixSigs.getAndIncrement();
                });
        assertEquals(4, numUnusedFullPrefixSigs.get());
    }

    @Test
    void getsExpectedSigBytesForOtherParties() throws Throwable {
        // given:
        Transaction signedTxn = newSignedSystemDelete().payerKt(payerKt).nonPayerKts(otherKt).get();
        PubKeyToSigBytes subject =
                new PojoSigMapPubKeyToSigBytes(
                        SignedTxnAccessor.uncheckedFrom(signedTxn).getSigMap());

        // expect:
        lookupsMatch(
                payerKt,
                defaultFactory,
                CommonUtils.extractTransactionBodyBytes(signedTxn),
                subject);
        lookupsMatch(
                otherKt,
                defaultFactory,
                CommonUtils.extractTransactionBodyBytes(signedTxn),
                subject);
    }

    @Test
    void rejectsNonUniqueSigBytes() {
        // given:
        String str = "TEST_STRING";
        byte[] pubKey = str.getBytes(StandardCharsets.UTF_8);
        SignaturePair sigPair =
                SignaturePair.newBuilder().setPubKeyPrefix(ByteString.copyFromUtf8(str)).build();
        SignatureMap sigMap =
                SignatureMap.newBuilder().addSigPair(sigPair).addSigPair(sigPair).build();
        PojoSigMapPubKeyToSigBytes sigMapPubKeyToSigBytes = new PojoSigMapPubKeyToSigBytes(sigMap);

        // expect:
        KeyPrefixMismatchException exception =
                assertThrows(
                        KeyPrefixMismatchException.class,
                        () -> {
                            sigMapPubKeyToSigBytes.sigBytesFor(pubKey);
                        });

        assertEquals(
                "Source signature map with prefix 544553545f535452494e47 is ambiguous for given"
                        + " public key! (544553545f535452494e47)",
                exception.getMessage());
    }

    private void lookupsMatch(KeyTree kt, KeyFactory factory, byte[] data, PubKeyToSigBytes subject)
            throws Exception {
        AtomicReference<Exception> thrown = new AtomicReference<>();
        kt.traverseLeaves(
                leaf -> {
                    byte[] pubKey = pubKeyFor(leaf, factory);
                    byte[] sigBytes = EMPTY_SIG;
                    try {
                        sigBytes = subject.sigBytesFor(pubKey);
                    } catch (Exception e) {
                        thrown.set(e);
                    }
                    if (pubKey.length == 32) {
                        byte[] expectedSigBytes = expectedSigFor(leaf, factory, data);
                        if (thrown.get() == null) {
                            assertArrayEquals(expectedSigBytes, sigBytes);
                        }
                    } else {
                        assertTrue(sigBytes.length >= 64);
                    }
                });
        if (thrown.get() != null) {
            throw thrown.get();
        }
    }

    private byte[] pubKeyFor(KeyTreeLeaf leaf, KeyFactory factory) {
        Key key = leaf.asKey(factory);
        if (key.getEd25519() != ByteString.EMPTY) {
            return key.getEd25519().toByteArray();
        } else if (key.getECDSASecp256K1() != ByteString.EMPTY) {
            return key.getECDSASecp256K1().toByteArray();
        } else if (key.getECDSA384() != ByteString.EMPTY) {
            return key.getECDSA384().toByteArray();
        } else if (key.getRSA3072() != ByteString.EMPTY) {
            return key.getRSA3072().toByteArray();
        }
        throw new AssertionError("Impossible leaf type!");
    }

    private byte[] expectedSigFor(KeyTreeLeaf leaf, KeyFactory factory, byte[] data) {
        if (!leaf.isUsedToSign()) {
            return EMPTY_SIG;
        } else {
            if (leaf.getSigType() == SignatureType.ED25519) {
                return signUnchecked(data, factory.lookupPrivateKey(leaf.asKey(factory)));
            } else if (leaf.getSigType() == SignatureType.RSA) {
                return SigFactory.NONSENSE_RSA_SIG;
            }
            throw new AssertionError("Impossible leaf type!");
        }
    }
}
