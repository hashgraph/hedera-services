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
package com.hedera.services.sigs.verification;

import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.sigs.SyncVerifiers.ALWAYS_VALID;
import static com.hedera.test.factories.sigs.SyncVerifiers.NEVER_VALID;
import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.PlatformSigOps;
import com.hedera.services.sigs.factories.ReusableBodySigningFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrecheckVerifierTest {
    private static List<JKey> reqKeys;
    private static final TransactionBody txnBody =
            TransactionBody.newBuilder()
                    .setTransactionID(
                            TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")))
                    .build();
    private static final Transaction txn =
            Transaction.newBuilder().setBodyBytes(txnBody.toByteString()).build();
    private static PlatformTxnAccessor realAccessor;

    private static final byte[][] VALID_SIG_BYTES = {
        "firstSig".getBytes(), "secondSig".getBytes(), "thirdSig".getBytes(), "fourthSig".getBytes()
    };
    private static final Supplier<PubKeyToSigBytes> VALID_PROVIDER_FACTORY =
            () ->
                    new PubKeyToSigBytes() {
                        private int i = 0;

                        @Override
                        public byte[] sigBytesFor(byte[] pubKey) {
                            return VALID_SIG_BYTES[i++];
                        }
                    };
    private static List<TransactionSignature> expectedSigs = EMPTY_LIST;

    private PrecheckKeyReqs precheckKeyReqs;
    private PrecheckVerifier subject;
    private SignedTxnAccessor mockAccessor;
    private static AliasManager aliasManager;

    @BeforeAll
    static void setupAll() throws Throwable {
        aliasManager = mock(AliasManager.class);
        realAccessor =
                PlatformTxnAccessor.from(
                        SignedTxnAccessor.from(txn.toByteArray()), PlatformTxnFactory.from(txn));
        reqKeys =
                List.of(
                        KeyTree.withRoot(list(ed25519(), list(ed25519(), ed25519()))).asJKey(),
                        KeyTree.withRoot(ed25519()).asJKey());
        expectedSigs =
                PlatformSigOps.createCryptoSigsFrom(
                                reqKeys,
                                VALID_PROVIDER_FACTORY.get(),
                                new ReusableBodySigningFactory(realAccessor))
                        .getPlatformSigs();
    }

    @BeforeEach
    void setup() {
        precheckKeyReqs = mock(PrecheckKeyReqs.class);
        mockAccessor = mock(SignedTxnAccessor.class);
        given(mockAccessor.getTxn()).willReturn(realAccessor.getTxn());
        given(mockAccessor.getTxnBytes()).willReturn(realAccessor.getTxnBytes());
        given(mockAccessor.getPkToSigsFn()).willReturn(VALID_PROVIDER_FACTORY.get());
    }

    @Test
    void affirmsValidSignatures() throws Exception {
        given(precheckKeyReqs.getRequiredKeys(txnBody)).willReturn(reqKeys);
        AtomicReference<List<TransactionSignature>> actualSigsVerified = new AtomicReference<>();
        givenImpliedSubject(
                sigs -> {
                    actualSigsVerified.set(sigs);
                    ALWAYS_VALID.verifySync(sigs);
                });

        // when:
        boolean hasPrechekSigs = subject.hasNecessarySignatures(mockAccessor);

        // then:
        assertEquals(expectedSigs, actualSigsVerified.get());
        assertTrue(hasPrechekSigs);
    }

    @Test
    void rejectsInvalidSignatures() throws Exception {
        given(precheckKeyReqs.getRequiredKeys(txnBody)).willReturn(reqKeys);
        AtomicReference<List<TransactionSignature>> actualSigsVerified = new AtomicReference<>();
        givenImpliedSubject(
                sigs -> {
                    actualSigsVerified.set(sigs);
                    NEVER_VALID.verifySync(sigs);
                });

        // when:
        boolean hasPrechekSigs = subject.hasNecessarySignatures(mockAccessor);

        // then:
        assertEquals(expectedSigs, actualSigsVerified.get());
        assertFalse(hasPrechekSigs);
    }

    @Test
    void propagatesSigCreationFailure() throws Exception {
        // setup:
        given(mockAccessor.getPkToSigsFn())
                .willReturn(
                        bytes -> {
                            throw new KeyPrefixMismatchException("Oops!");
                        });

        given(precheckKeyReqs.getRequiredKeys(txnBody)).willReturn(reqKeys);
        subject = new PrecheckVerifier(ALWAYS_VALID, precheckKeyReqs);

        // expect:
        assertThrows(
                KeyPrefixMismatchException.class,
                () -> subject.hasNecessarySignatures(mockAccessor));
    }

    @Test
    void rejectsGivenInvalidPayerException() throws Exception {
        given(precheckKeyReqs.getRequiredKeys(txnBody))
                .willThrow(new InvalidPayerAccountException());
        givenImpliedSubject(ALWAYS_VALID);

        // expect:
        assertFalse(subject.hasNecessarySignatures(mockAccessor));
    }

    @Test
    void propagatesOtherKeyLookupExceptions() throws Exception {
        given(precheckKeyReqs.getRequiredKeys(txnBody)).willThrow(new IllegalStateException());
        givenImpliedSubject(ALWAYS_VALID);

        // expect:
        assertThrows(
                IllegalStateException.class, () -> subject.hasNecessarySignatures(mockAccessor));
    }

    @Test
    void affirmsValidSignaturesInSignedTxn() throws Exception {
        // setup:
        final var signedTransaction =
                SignedTransaction.newBuilder().setBodyBytes(txnBody.toByteString()).build();
        AtomicReference<List<TransactionSignature>> actualSigsVerified = new AtomicReference<>();

        given(
                        precheckKeyReqs.getRequiredKeys(
                                TransactionBody.parseFrom(signedTransaction.getBodyBytes())))
                .willReturn(reqKeys);
        // and:
        givenImpliedSubject(
                sigs -> {
                    actualSigsVerified.set(sigs);
                    ALWAYS_VALID.verifySync(sigs);
                });

        // when:
        boolean hasPrechekSigs = subject.hasNecessarySignatures(mockAccessor);

        // then:
        assertEquals(expectedSigs, actualSigsVerified.get());
        assertTrue(hasPrechekSigs);
    }

    private void givenImpliedSubject(SyncVerifier syncVerifier) {
        subject = new PrecheckVerifier(syncVerifier, precheckKeyReqs);
    }
}
