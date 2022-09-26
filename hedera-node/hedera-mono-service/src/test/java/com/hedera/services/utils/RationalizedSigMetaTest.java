/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

import static com.hedera.services.keys.HederaKeyTraversal.visitSimpleKeys;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.EXPECTED_SIG;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.pk;
import static com.hedera.services.sigs.utils.MiscCryptoUtils.decompressSecp256k1;
import static com.hedera.test.factories.keys.KeyTree.withRoot;
import static com.hedera.test.factories.keys.NodeFactory.ecdsa384Secp256k1;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RationalizedSigMetaTest {
    @Mock private EthTxSigs ethTxSigs;
    @Mock private TxnAccessor accessor;

    private final Map<String, Object> spanMap = new HashMap<>();

    private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
    private final List<JKey> othersKeys =
            List.of(TxnHandlingScenario.MISC_ADMIN_KT.asJKeyUnchecked());
    private final List<TransactionSignature> rationalizedSigs = List.of(EXPECTED_SIG);

    private RationalizedSigMeta subject;

    @Test
    void canRevokeCryptoSigs() {
        final KeyTree mixedKt = withRoot(list(ed25519(), ecdsa384Secp256k1()));
        final JKey relayerKey = mixedKt.asJKeyUnchecked();
        final List<byte[]> publicKeys = new ArrayList<>();
        final List<TransactionSignature> sigs = new ArrayList<>();
        extractRelayerKeys(relayerKey, sigs, publicKeys);

        givenEthTx();
        given(accessor.getSpanMap()).willReturn(spanMap);
        givenEthTxSigs();

        subject =
                RationalizedSigMeta.forPayerAndOthers(
                        relayerKey, othersKeys, asValid(sigs), accessor);
        final var verifiedSigsFn = subject.pkToVerifiedSigFn();

        // First confirm that all relayer keys have valid crypto signatures
        for (final var publicKey : publicKeys) {
            assertSame(VALID, verifiedSigsFn.apply(publicKey).getSignatureStatus());
        }
        // As well as the Ethereum msg sender
        assertSame(VALID, verifiedSigsFn.apply(ethTxSigs.publicKey()).getSignatureStatus());

        // And now revoke the relayer's signatures
        subject.revokeCryptoSigsFrom(relayerKey);
        final var newVerifiedSigsFn = subject.pkToVerifiedSigFn();

        // Now confirm that no relayer keys have valid crypto signatures
        for (final var publicKey : publicKeys) {
            assertSame(INVALID, newVerifiedSigsFn.apply(publicKey).getSignatureStatus());
        }
        // And cover the remaining branch
        assertSame(
                INVALID,
                newVerifiedSigsFn.apply(payerKey.primitiveKeyIfPresent()).getSignatureStatus());
    }

    @Test
    void noneAvailableHasNoInfo() {
        subject = RationalizedSigMeta.noneAvailable();

        // then:
        assertFalse(subject.couldRationalizePayer());
        assertFalse(subject.couldRationalizeOthers());
        // and:
        assertThrows(IllegalStateException.class, subject::verifiedSigs);
        assertThrows(IllegalStateException.class, subject::payerKey);
        assertThrows(IllegalStateException.class, subject::othersReqSigs);
        assertThrows(IllegalStateException.class, subject::pkToVerifiedSigFn);
    }

    @Test
    void payerOnlyHasExpectedInfo() {
        givenNonEthTx();

        subject = RationalizedSigMeta.forPayerOnly(payerKey, rationalizedSigs, accessor);

        // then:
        assertTrue(subject.couldRationalizePayer());
        assertFalse(subject.couldRationalizeOthers());
        // and:
        assertSame(payerKey, subject.payerKey());
        assertSame(rationalizedSigs, subject.verifiedSigs());
        assertSame(EXPECTED_SIG, subject.pkToVerifiedSigFn().apply(pk));
    }

    @Test
    void forBothHaveExpectedInfo() {
        givenNonEthTx();

        subject =
                RationalizedSigMeta.forPayerAndOthers(
                        payerKey, othersKeys, rationalizedSigs, accessor);

        assertTrue(subject.couldRationalizePayer());
        assertTrue(subject.couldRationalizeOthers());
        // and:
        assertSame(payerKey, subject.payerKey());
        assertSame(othersKeys, subject.othersReqSigs());
        assertSame(rationalizedSigs, subject.verifiedSigs());
        assertSame(EXPECTED_SIG, subject.pkToVerifiedSigFn().apply(pk));
    }

    @Test
    void ethTxCanMatchExtractedPublicKey() {
        givenEthTx();
        given(accessor.getSpanMap()).willReturn(spanMap);
        givenEthTxSigs();

        subject =
                RationalizedSigMeta.forPayerAndOthers(
                        payerKey, othersKeys, rationalizedSigs, accessor);

        assertTrue(subject.couldRationalizePayer());
        assertTrue(subject.couldRationalizeOthers());
        // and:
        assertSame(payerKey, subject.payerKey());
        assertSame(othersKeys, subject.othersReqSigs());
        assertSame(rationalizedSigs, subject.verifiedSigs());
        // and:
        final var verifiedSigsFn = subject.pkToVerifiedSigFn();
        assertSame(EXPECTED_SIG, verifiedSigsFn.apply(pk));
        // and:
        final var ethSigStatus = verifiedSigsFn.apply(ethTxSigs.publicKey()).getSignatureStatus();
        assertEquals(VALID, ethSigStatus);
    }

    private void givenEthTxSigs() {
        final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
        final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
        final var compressed = q.getEncoded(true);
        final var spanMapAccessor = new ExpandHandleSpanMapAccessor();
        spanMapAccessor.setEthTxSigsMeta(accessor, ethTxSigs);
        given(ethTxSigs.publicKey()).willReturn(compressed);
    }

    @Test
    void worksAroundNullEthTxSigs() {
        givenEthTx();
        given(accessor.getSpanMap()).willReturn(spanMap);

        subject =
                RationalizedSigMeta.forPayerAndOthers(
                        payerKey, othersKeys, rationalizedSigs, accessor);

        assertTrue(subject.couldRationalizePayer());
        assertTrue(subject.couldRationalizeOthers());
        // and:
        assertSame(payerKey, subject.payerKey());
        assertSame(othersKeys, subject.othersReqSigs());
        assertSame(rationalizedSigs, subject.verifiedSigs());
        // and:
        final var verifiedSigsFn = subject.pkToVerifiedSigFn();
        assertSame(EXPECTED_SIG, verifiedSigsFn.apply(pk));
    }

    private TransactionSignature mockValidSigWithKey(final JKey key) {
        final byte[] sig = "mockSignatures".getBytes();
        final byte[] data = "mockData".getBytes();
        final byte[] publicKey =
                key.hasEd25519Key()
                        ? key.getEd25519()
                        : decompressSecp256k1(key.getECDSASecp256k1Key());
        final byte[] contents = new byte[sig.length + data.length];
        System.arraycopy(sig, 0, contents, 0, sig.length);
        System.arraycopy(data, 0, contents, sig.length, data.length);
        return new TransactionSignature(
                contents, 0, sig.length, publicKey, 0, publicKey.length, sig.length, data.length);
    }

    private void extractRelayerKeys(
            final JKey relayerKey,
            final List<TransactionSignature> sigs,
            final List<byte[]> relayerPublicKeys) {
        visitSimpleKeys(
                relayerKey,
                key -> {
                    if (key.hasEd25519Key()) {
                        relayerPublicKeys.add(key.getEd25519());
                    } else {
                        relayerPublicKeys.add(key.getECDSASecp256k1Key());
                    }
                    sigs.add(mockValidSigWithKey(key));
                });
    }

    private void givenNonEthTx() {
        given(accessor.getFunction()).willReturn(HederaFunctionality.ContractCall);
    }

    private void givenEthTx() {
        given(accessor.getFunction()).willReturn(HederaFunctionality.EthereumTransaction);
    }
}
