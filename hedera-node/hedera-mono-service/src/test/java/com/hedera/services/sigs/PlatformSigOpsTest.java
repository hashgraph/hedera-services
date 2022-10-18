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
package com.hedera.services.sigs;

import static com.hedera.services.sigs.PlatformSigOps.createCryptoSigsFrom;
import static com.hedera.test.factories.keys.NodeFactory.ecdsa384Secp256k1;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.swirlds.common.crypto.SignatureType.ED25519;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.test.factories.keys.KeyTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformSigOpsTest {
    private static final byte[] EMPTY_SIG = new byte[0];
    private static final byte[] MOCK_SIG = "FIRST".getBytes();
    private static final byte[][] MORE_MOCK_SIGS =
            new byte[][] {
                "SECOND".getBytes(),
                "THIRD".getBytes(),
                "FOURTH".getBytes(),
                "FIFTH".getBytes(),
                "SIXTH".getBytes()
            };
    private static final byte[][] MORE_EMPTY_SIGS =
            new byte[][] {EMPTY_SIG, EMPTY_SIG, EMPTY_SIG, EMPTY_SIG};
    private final List<JKey> pubKeys = new ArrayList<>();
    private static final List<KeyTree> kts =
            List.of(
                    KeyTree.withRoot(ed25519()),
                    KeyTree.withRoot(list(ed25519(), ed25519())),
                    KeyTree.withRoot(
                            threshold(1, list(ed25519()), ed25519(), ecdsa384Secp256k1())));
    private PubKeyToSigBytes sigBytes;
    private TxnScopedPlatformSigFactory sigFactory;

    @BeforeEach
    void setup() throws Throwable {
        pubKeys.clear();
        sigBytes = mock(PubKeyToSigBytes.class);
        sigFactory = mock(TxnScopedPlatformSigFactory.class);
        for (final var kt : kts) {
            pubKeys.add(kt.asJKey());
        }
        pubKeys.add(new JContractIDKey(0, 0, 1234));
        pubKeys.add(new JDelegatableContractIDKey(0, 0, 12345));
    }

    @Test
    void createsOnlyNonDegenerateSigs() throws Throwable {
        given(sigBytes.sigBytesFor(any())).willReturn(MOCK_SIG, MORE_EMPTY_SIGS);

        final var result = createCryptoSigsFrom(pubKeys, sigBytes, sigFactory);

        final var nextSigIndex = new AtomicInteger(0);
        verify(sigBytes, never()).sigBytesFor(null);
        for (final var kt : kts) {
            kt.traverseLeaves(
                    leaf -> {
                        final var pk = leaf.asKey().getEd25519().toByteArray();
                        if (nextSigIndex.get() == 0) {
                            verify(sigFactory).signBodyWithEd25519(pk, MOCK_SIG);
                        } else {
                            verify(sigFactory, never()).signBodyWithEd25519(pk, EMPTY_SIG);
                        }
                        nextSigIndex.addAndGet(1);
                    });
        }
        assertEquals(1, result.getPlatformSigs().size());
    }

    @Test
    void createsSigsInTraversalOrder() throws Throwable {
        given(sigBytes.sigBytesFor(any())).willReturn(MOCK_SIG, MORE_MOCK_SIGS);

        final var result = createCryptoSigsFrom(pubKeys, sigBytes, sigFactory);
        final var nextSigIndex = new AtomicInteger(0);
        for (final var kt : kts) {
            kt.traverseLeaves(
                    leaf -> {
                        final var isEd25519 = leaf.getSigType() == ED25519;
                        final var pk =
                                isEd25519
                                        ? leaf.asKey().getEd25519().toByteArray()
                                        : leaf.asKey().getECDSASecp256K1().toByteArray();
                        final var sigBytes =
                                (nextSigIndex.get() == 0)
                                        ? MOCK_SIG
                                        : MORE_MOCK_SIGS[nextSigIndex.get() - 1];
                        if (isEd25519) {
                            verify(sigFactory).signBodyWithEd25519(pk, sigBytes);
                        } else {
                            verify(sigFactory).signKeccak256DigestWithSecp256k1(pk, sigBytes);
                        }
                        nextSigIndex.addAndGet(1);
                    });
        }
        assertEquals(1 + MORE_MOCK_SIGS.length, result.getPlatformSigs().size());
    }

    @Test
    void ignoresAmbiguousScheduledSig() throws Throwable {
        final JKey scheduledKey = new JEd25519Key("01234578901234578901234578901".getBytes());
        scheduledKey.setForScheduledTxn(true);
        given(sigBytes.sigBytesFor(any())).willThrow(KeyPrefixMismatchException.class);

        final var result = createCryptoSigsFrom(List.of(scheduledKey), sigBytes, sigFactory);

        assertFalse(result.hasFailed());
        assertTrue(result.getPlatformSigs().isEmpty());
    }

    @Test
    void doesntIgnoreUnrecognizedProblemForScheduledSig() throws Throwable {
        final JKey scheduledKey = new JEd25519Key("01234578901234578901234578901".getBytes());
        scheduledKey.setForScheduledTxn(true);
        given(sigBytes.sigBytesFor(any())).willThrow(IllegalStateException.class);

        final var result = createCryptoSigsFrom(List.of(scheduledKey), sigBytes, sigFactory);

        assertTrue(result.hasFailed());
    }

    @Test
    void failsOnInsufficientSigs() throws Throwable {
        given(sigBytes.sigBytesFor(any()))
                .willReturn(MOCK_SIG)
                .willThrow(KeyPrefixMismatchException.class);

        final var result = createCryptoSigsFrom(pubKeys, sigBytes, sigFactory);

        assertEquals(1, result.getPlatformSigs().size());
        assertTrue(result.hasFailed());
    }

    @Test
    void returnsSuccessSigStatusByDefault() {
        final var subject = new PlatformSigsCreationResult();

        final var status = subject.asCode();

        assertEquals(OK, status);
    }

    @Test
    void reportsInvalidSigMap() {
        final var subject = new PlatformSigsCreationResult();
        subject.setTerminatingEx(new KeyPrefixMismatchException("No!"));

        final var status = subject.asCode();

        assertEquals(KEY_PREFIX_MISMATCH, status);
    }

    @Test
    void reportsNonspecificInvalidSig() {
        final var subject = new PlatformSigsCreationResult();
        subject.setTerminatingEx(new Exception());

        final var status = subject.asCode();

        assertEquals(INVALID_SIGNATURE, status);
    }
}
