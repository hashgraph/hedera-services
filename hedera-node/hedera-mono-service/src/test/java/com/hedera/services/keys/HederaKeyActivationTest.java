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
package com.hedera.services.keys;

import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.keys.HederaKeyActivation.isActive;
import static com.hedera.services.keys.HederaKeyActivation.pkToSigMapFrom;
import static com.hedera.services.legacy.proto.utils.SignatureGenerator.signBytes;
import static com.hedera.services.sigs.PlatformSigOps.createCryptoSigsFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.ed25519Sig;
import static com.hedera.services.sigs.utils.MiscCryptoUtils.keccak256DigestOf;
import static com.hedera.test.factories.keys.KeyTree.withRoot;
import static com.hedera.test.factories.keys.NodeFactory.ecdsa384Secp256k1;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sigs.factories.ReusableBodySigningFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.sigs.SigWrappers;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.engine.CryptoEngine;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaKeyActivationTest {
    private static JKey complexKey;
    private static JKey keyList;
    private static final byte[] pk = "PK".getBytes();
    private static final byte[] sig = "SIG".getBytes();
    private static final byte[] data = "DATA".getBytes();
    private Function<byte[], TransactionSignature> sigsFn;
    private static final TransactionSignature VALID_SIG =
            SigWrappers.asValid(List.of(ed25519Sig(pk, sig, data))).get(0);
    private static final TransactionSignature INVALID_SIG =
            SigWrappers.asInvalid(List.of(ed25519Sig(pk, sig, data))).get(0);

    private static final Function<Integer, TransactionSignature> mockSigFn =
            i ->
                    ed25519Sig(
                            String.format("01234567890123456789012345678PK%d", i).getBytes(),
                            String.format("SIG%d", i).getBytes(),
                            String.format("DATA%d", i).getBytes());

    @BeforeAll
    static void setupAll() throws Throwable {
        keyList = withRoot(list(ecdsa384Secp256k1(), ed25519())).asJKey();

        complexKey =
                withRoot(
                                list(
                                        ed25519(),
                                        threshold(
                                                1,
                                                list(list(ed25519(), ed25519()), ed25519()),
                                                ed25519()),
                                        ed25519(),
                                        list(threshold(2, ed25519(), ed25519(), ed25519()))))
                        .asJKey();
    }

    @BeforeEach
    void setup() {
        sigsFn = (Function<byte[], TransactionSignature>) mock(Function.class);
    }

    @Test
    void canTestEcdsaSecp256kKey() {
        final var secp256k1Key =
                new JECDSASecp256k1Key("012345789012345789012345789012".getBytes());
        final var mockCryptoSig = mock(TransactionSignature.class);

        given(mockCryptoSig.getSignatureStatus()).willReturn(VerificationStatus.VALID);
        given(sigsFn.apply(secp256k1Key.getECDSASecp256k1Key())).willReturn(mockCryptoSig);

        assertTrue(HederaKeyActivation.isActive(secp256k1Key, sigsFn, ONLY_IF_SIG_IS_VALID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void contractAndDelegateContractKeysTestedWithInvalidSig() {
        final var contractKey = new JContractIDKey(0, 0, 1234);
        final var delegateContractKey = new JDelegatableContractIDKey(0, 0, 12345);
        final BiPredicate<JKey, TransactionSignature> mockTest = mock(BiPredicate.class);

        given(mockTest.test(contractKey, HederaKeyActivation.INVALID_MISSING_SIG))
                .willReturn(false);
        given(mockTest.test(delegateContractKey, HederaKeyActivation.INVALID_MISSING_SIG))
                .willReturn(true);

        assertFalse(HederaKeyActivation.isActive(contractKey, sigsFn, mockTest));
        assertTrue(HederaKeyActivation.isActive(delegateContractKey, sigsFn, mockTest));
    }

    @Test
    void canMatchCompressedEcdsaSecp256k1Key() throws Exception {
        final var mockTxnBytes =
                "012345789012345789012345789012345789012345789012345789012345789012345789012345789012345789"
                        .getBytes();

        final var explicitList = keyList.getKeyList().getKeysList();
        final var secp256k1Key = explicitList.get(0);
        final var ed25519Key = explicitList.get(1);

        final var keyFactory = KeyFactory.getDefaultInstance();
        final var mockSigs = mock(PubKeyToSigBytes.class);
        given(mockSigs.sigBytesFor(explicitList.get(0).getECDSASecp256k1Key()))
                .willReturn(
                        signBytes(
                                keccak256DigestOf(mockTxnBytes),
                                keyFactory.lookupPrivateKey(
                                        hex(secp256k1Key.getECDSASecp256k1Key()))));
        given(mockSigs.sigBytesFor(explicitList.get(1).getEd25519()))
                .willReturn(
                        signBytes(
                                mockTxnBytes,
                                keyFactory.lookupPrivateKey(hex(ed25519Key.getEd25519()))));

        final var accessor = mock(TxnAccessor.class);
        given(accessor.getTxnBytes()).willReturn(mockTxnBytes);

        final var cryptoSigs =
                createCryptoSigsFrom(
                                explicitList, mockSigs, new ReusableBodySigningFactory(accessor))
                        .getPlatformSigs();
        new CryptoEngine(getStaticThreadManager()).verifySync(cryptoSigs);
        final var subject = pkToSigMapFrom(cryptoSigs);

        final var ed25519Sig = subject.apply(ed25519Key.getEd25519());
        assertEquals(SignatureType.ED25519, ed25519Sig.getSignatureType());
        assertEquals(VerificationStatus.VALID, ed25519Sig.getSignatureStatus());

        final var secp256k1Sig = subject.apply(secp256k1Key.getECDSASecp256k1Key());
        assertEquals(SignatureType.ECDSA_SECP256K1, secp256k1Sig.getSignatureType());
    }

    @Test
    void revocationServiceActivatesWithOneTopLevelSig() {
        final var characteristics =
                RevocationServiceCharacteristics.forTopLevelFile((JKeyList) complexKey);
        given(sigsFn.apply(any()))
                .willReturn(
                        VALID_SIG,
                        INVALID_SIG,
                        INVALID_SIG,
                        INVALID_SIG,
                        VALID_SIG,
                        INVALID_SIG,
                        INVALID_SIG,
                        INVALID_SIG,
                        VALID_SIG);

        assertTrue(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID, characteristics));
        verify(sigsFn, times(9)).apply(any());
    }

    @Test
    void revocationServiceRequiresOneTopLevelSig() {
        final var characteristics =
                RevocationServiceCharacteristics.forTopLevelFile((JKeyList) complexKey);
        given(sigsFn.apply(any())).willReturn(INVALID_SIG);

        assertFalse(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID, characteristics));
        verify(sigsFn, times(9)).apply(any());
    }

    @Test
    void mapSupplierReflectsInputList() {
        final var presentSigs = List.of(mockSigFn.apply(0), mockSigFn.apply(1));
        final var missingSig = mockSigFn.apply(2);
        final var sigsFn = pkToSigMapFrom(presentSigs);

        final var present0 = sigsFn.apply(presentSigs.get(0).getExpandedPublicKeyDirect());
        final var present1 = sigsFn.apply(presentSigs.get(1).getExpandedPublicKeyDirect());
        final var missing = sigsFn.apply(missingSig.getExpandedPublicKeyDirect());

        assertEquals(presentSigs.get(0), present0);
        assertEquals(presentSigs.get(1), present1);
        assertEquals(HederaKeyActivation.INVALID_MISSING_SIG, missing);
        assertEquals(
                VerificationStatus.INVALID,
                HederaKeyActivation.INVALID_MISSING_SIG.getSignatureStatus());
    }

    @Test
    void topLevelListActivatesOnlyIfAllChildrenAreActive() {
        given(sigsFn.apply(any())).willReturn(INVALID_SIG, VALID_SIG);

        assertFalse(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID));
        verify(sigsFn, times(9)).apply(any());
    }

    @Test
    void topLevelActivatesIfAllChildrenAreActive() {
        given(sigsFn.apply(any()))
                .willReturn(
                        VALID_SIG,
                        INVALID_SIG,
                        INVALID_SIG,
                        INVALID_SIG,
                        VALID_SIG,
                        VALID_SIG,
                        INVALID_SIG,
                        VALID_SIG,
                        VALID_SIG);

        assertTrue(isActive(complexKey, sigsFn, ONLY_IF_SIG_IS_VALID));
        verify(sigsFn, times(9)).apply(any());
    }

    @Test
    void throwsIfNoSigMetaHasBeenRationalized() {
        final var accessor = mock(PlatformTxnAccessor.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> HederaKeyActivation.payerSigIsActive(accessor, ONLY_IF_SIG_IS_VALID));
    }

    @Test
    void immediatelyReturnsFalseForNoRationalizedPayerData() {
        final var accessor = mock(PlatformTxnAccessor.class);

        given(accessor.getSigMeta()).willReturn(RationalizedSigMeta.noneAvailable());

        assertFalse(HederaKeyActivation.payerSigIsActive(accessor, ONLY_IF_SIG_IS_VALID));
    }

    @Test
    void cantMatchAnyBytesOtherThanExpectedLens() {
        final var miscBytes = "asdf".getBytes();

        assertFalse(HederaKeyActivation.keysMatch(miscBytes, miscBytes));
    }

    @Test
    void checksParityOfYCoordWhenMatchingSecp256k1Keys() {
        final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
        final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
        final var uncompressed = Arrays.copyOfRange(q.getEncoded(false), 1, 65);
        final var other =
                "0123456789012345678901234567890123456789012345678901234567890123".getBytes();

        final var sameParityCompressed = q.getEncoded(true);
        final var otherParityCompressed =
                Arrays.copyOfRange(sameParityCompressed, 0, sameParityCompressed.length);
        otherParityCompressed[0] =
                sameParityCompressed[0] == (byte) 0x02 ? (byte) 0x03 : (byte) 0x02;

        assertTrue(HederaKeyActivation.keysMatch(sameParityCompressed, uncompressed));
        assertFalse(HederaKeyActivation.keysMatch(otherParityCompressed, uncompressed));
        assertFalse(HederaKeyActivation.keysMatch(sameParityCompressed, other));
    }

    @Test
    void validSigIsValid() {
        assertEquals(
                VerificationStatus.VALID, HederaKeyActivationTest.VALID_SIG.getSignatureStatus());
    }
}
