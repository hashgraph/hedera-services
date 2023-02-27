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

package com.hedera.node.app.service.mono.sigs;

import static com.hedera.node.app.service.mono.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JHollowKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.metadata.AccountSigningMetadata;
import com.hedera.node.app.service.mono.sigs.metadata.SafeLookupResult;
import com.hedera.node.app.service.mono.sigs.metadata.SigMetadataLookup;
import com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResult;
import com.hedera.node.app.service.mono.sigs.sourcing.KeyType;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.sourcing.SigObserver;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpansionTest {
    @Mock
    private PubKeyToSigBytes pkToSigFn;

    @Mock
    private SigRequirements sigReqs;

    @Mock
    private PlatformTxnAccessor txnAccessor;

    @Mock
    private TxnScopedPlatformSigFactory sigFactory;

    @Mock
    private TransactionSignature ed25519Sig;

    @Mock
    private TransactionSignature secp256k1Sig;

    @Mock
    private AccountID payer;

    @Mock
    private Expansion.CryptoSigsCreation cryptoSigsCreation;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private SigMetadataLookup sigMetadataLookup;

    private Expansion subject;

    @BeforeEach
    void setUp() {
        subject = new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        given(cryptoSigsCreation.createFrom(any(), any(), any())).willReturn(new PlatformSigsCreationResult());
    }

    @Test
    void tracksLinkedRefs() {
        final var mockTxn = TransactionBody.getDefaultInstance();
        given(sigReqs.keysForPayer(eq(mockTxn), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willAnswer(invocationOnMock -> {
                    final var linkedRefs = (LinkedRefs) invocationOnMock.getArgument(2);
                    linkedRefs.link(1L);
                    return mockPayerResponse;
                });
        given(sigReqs.keysForOtherParties(eq(mockTxn), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willAnswer(invocationOnMock -> {
                    final var linkedRefs = (LinkedRefs) invocationOnMock.getArgument(2);
                    linkedRefs.link(2L);
                    return mockOtherPartiesResponse;
                });
        given(txnAccessor.getPayer()).willReturn(payer);
        given(txnAccessor.getTxn()).willReturn(mockTxn);

        subject.execute();

        final ArgumentCaptor<LinkedRefs> captor = forClass(LinkedRefs.class);
        verify(txnAccessor).setLinkedRefs(captor.capture());
        final var linkedRefs = captor.getValue();
        assertArrayEquals(new long[] {1L, 2L}, linkedRefs.linkedNumbers());
    }

    @Test
    void skipsUnusedFullKeySigsIfNotPresent() {
        setupDegenerateMocks();

        subject.execute();

        verify(pkToSigFn, never()).forEachUnusedSigWithFullPrefix(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendsUnusedFullKeySignaturesToList() {
        final var pretendEd25519FullKey = "COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendEd25519FullSig = "NONSENSE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        final ArgumentCaptor<List<TransactionSignature>> captor = forClass(List.class);

        given(sigFactory.signAppropriately(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig))
                .willReturn(ed25519Sig);
        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        setupDegenerateMocks();
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());

        subject.execute();

        final var allSigs = List.of(ed25519Sig, secp256k1Sig);
        verify(txnAccessor).addAllCryptoSigs(captor.capture());
        assertEquals(allSigs, captor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void payerHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        // mock out a ECDSA key + sig
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscCryptoUtilsStaticMock = mockStatic(MiscCryptoUtils.class);
        miscCryptoUtilsStaticMock
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscCryptoUtilsStaticMock
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        // mock account and returned key
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasHollowKey()).willReturn(true);
        final var jHollowKey = mock(JHollowKey.class);
        given(key.getHollowKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(new SafeLookupResult<>(new AccountSigningMetadata(key, false)));
        // mock txn and sig reqs
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();
        // payer has the JHollowKey we've mocked
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(key)));
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(mockOtherPartiesResponse);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());

        subject.execute();

        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        // verify payer key in meta has been replaced
        final var expectedFinalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        assertEquals(expectedFinalKey, txnAccessor.getSigMeta().payerKey());
        // verify pending completions in txn accessor
        final var expectedPendingCompletions = List.of(new PendingCompletion(expectedFinalKey, num));
        assertEquals(expectedPendingCompletions, pendingCompletionCaptor.getValue());
        // verify linked refs
        final var linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(num.longValue(), linkedRefsCaptorValue.linkedNumbers()[0]);
        assertEquals(1, linkedRefsCaptorValue.linkedNumbers().length);
        final var linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(2, linkedAliases.size());
        assertEquals(alias, linkedAliases.get(0));
        assertEquals(protobufKeyFrom(decompressedKeyBytes), linkedAliases.get(1));
        miscCryptoUtilsStaticMock.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void otherReqKeyHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        // mock out a ECDSA key + sig
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscCryptoUtilsStaticMock = mockStatic(MiscCryptoUtils.class);
        miscCryptoUtilsStaticMock
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscCryptoUtilsStaticMock
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        // mock account and returned key
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasHollowKey()).willReturn(true);
        final var jHollowKey = mock(JHollowKey.class);
        given(key.getHollowKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(new SafeLookupResult<>(new AccountSigningMetadata(key, false)));
        // mock txn and sig reqs
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();
        // payer has the JHollowKey we've mocked
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(mockPayerResponse);
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(key)));
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());

        subject.execute();

        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        // verify payer key in meta has been replaced
        final var expectedFinalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        assertEquals(expectedFinalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
        // verify pending completions in txn accessor
        final var expectedPendingCompletions = List.of(new PendingCompletion(expectedFinalKey, num));
        assertEquals(expectedPendingCompletions, pendingCompletionCaptor.getValue());
        // verify linked refs
        final var linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(num.longValue(), linkedRefsCaptorValue.linkedNumbers()[0]);
        assertEquals(1, linkedRefsCaptorValue.linkedNumbers().length);
        final var linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(2, linkedAliases.size());
        assertEquals(alias, linkedAliases.get(0));
        assertEquals(protobufKeyFrom(decompressedKeyBytes), linkedAliases.get(1));
        miscCryptoUtilsStaticMock.close();
    }

    private static ByteString protobufKeyFrom(byte[] decompressedKeyBytes) {
        return Key.newBuilder()
                .setECDSASecp256K1(ByteStringUtils.wrapUnsafely(decompressedKeyBytes))
                .build()
                .toByteString();
    }

    @Test
    @SuppressWarnings("unchecked")
    void payerHollowKeyDoesNotGetReplacedWhenNoMatchingECDSAKey() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);

        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscStatic = mockStatic(MiscCryptoUtils.class);
        miscStatic
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscStatic
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasHollowKey()).willReturn(true);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(new SafeLookupResult<>(new AccountSigningMetadata(key, false)));

        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);

        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        final JKey payerKey = mock(JKey.class);
        given(payerKey.hasHollowKey()).willReturn(true);
        final JHollowKey payerHollowKey = mock(JHollowKey.class);
        given(payerKey.getHollowKey()).willReturn(payerHollowKey);
        given(payerHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(payerKey)));
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(mockOtherPartiesResponse);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.execute();

        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        // verify payer key in meta has NOT been replaced
        assertEquals(payerKey, txnAccessor.getSigMeta().payerKey());
        // verify pending completions in txn accessor
        final var expectedFinalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        final var expectedPendingCompletions = List.of(new PendingCompletion(expectedFinalKey, num));
        assertEquals(expectedPendingCompletions, pendingCompletionCaptor.getValue());
        // verify linked refs
        final var linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(num.longValue(), linkedRefsCaptorValue.linkedNumbers()[0]);
        assertEquals(1, linkedRefsCaptorValue.linkedNumbers().length);
        final var linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(2, linkedAliases.size());
        assertEquals(alias, linkedAliases.get(0));
        assertEquals(protobufKeyFrom(decompressedKeyBytes), linkedAliases.get(1));
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hollowKeyInTheOtherReqKeysWithoutCorrespondingECDSAKeyInSigsIsNotReplaced() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);

        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscStatic = mockStatic(MiscCryptoUtils.class);
        miscStatic
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscStatic
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final JKey key = mock(JKey.class);
        given(key.hasHollowKey()).willReturn(true);
        final JHollowKey jHollowKey = mock(JHollowKey.class);
        given(key.getHollowKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(new SafeLookupResult<>(new AccountSigningMetadata(key, false)));

        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);

        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        final JKey payerKey = mock(JKey.class);
        given(payerKey.hasHollowKey()).willReturn(true);
        final JHollowKey payerHollowKey = mock(JHollowKey.class);
        given(payerKey.getHollowKey()).willReturn(payerHollowKey);
        given(payerHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(payerKey)));

        final JKey lonelyKey = mock(JKey.class);
        given(lonelyKey.hasHollowKey()).willReturn(true);
        final JHollowKey lonelyHollowKey = mock(JHollowKey.class);
        given(lonelyKey.getHollowKey()).willReturn(lonelyHollowKey);
        given(lonelyHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(key, lonelyKey)));
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.execute();

        final var finalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        final var allPretendCompletions = List.of(new PendingCompletion(finalKey, num));
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        assertEquals(allPretendCompletions, pendingCompletionCaptor.getValue());
        final LinkedRefs linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(num.longValue(), linkedRefsCaptorValue.linkedNumbers()[0]);
        final List<ByteString> linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(2, linkedAliases.size());
        assertEquals(alias, linkedAliases.get(0));
        assertEquals(protobufKeyFrom(decompressedKeyBytes), linkedAliases.get(1));
        assertEquals(finalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotAddPendingHollowCompletionFromUnusedFullPrefixECDSASigIfAliasManagerShowsMissingEntity() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);

        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscStatic = mockStatic(MiscCryptoUtils.class);
        miscStatic
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscStatic
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        given(aliasManager.lookupIdBy(alias)).willReturn(EntityNum.MISSING_NUM);

        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        setupDegenerateMocks();
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.execute();

        verify(txnAccessor, never()).setPendingCompletions(any());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        final LinkedRefs linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(0, linkedRefsCaptorValue.linkedNumbers()[0]);
        final List<ByteString> linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(2, linkedAliases.size());
        assertEquals(alias, linkedAliases.get(0));
        assertEquals(protobufKeyFrom(decompressedKeyBytes), linkedAliases.get(1));
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hollowScreenOnUnsuccessfulMetadataLookup() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);

        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscStatic = mockStatic(MiscCryptoUtils.class);
        miscStatic
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscStatic
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT));

        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        setupDegenerateMocks();
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.execute();

        verify(txnAccessor, never()).setPendingCompletions(any());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        final LinkedRefs linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(num.longValue(), linkedRefsCaptorValue.linkedNumbers()[0]);
        final List<ByteString> linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(2, linkedAliases.size());
        assertEquals(alias, linkedAliases.get(0));
        assertEquals(protobufKeyFrom(decompressedKeyBytes), linkedAliases.get(1));
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonHollowAccountsAreNotAddedToPendingCompletionsNorLinkedRefs() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        final var decompressedKeyBytes = "decompressedKey".getBytes();
        final var evmAddressForKey1 = "evmAddressForKey1".getBytes();
        final var miscCryptoUtilsStaticMock = mockStatic(MiscCryptoUtils.class);
        miscCryptoUtilsStaticMock
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscCryptoUtilsStaticMock
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        given(sigReqs.getSigMetaLookup()).willReturn(sigMetadataLookup);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(new SafeLookupResult<>(
                        new AccountSigningMetadata(new JECDSASecp256k1Key("someBytesForKey".getBytes()), false)));
        // mock txn and sig reqs
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        final var payerKey = new JECDSASecp256k1Key("anotherKey".getBytes());
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(payerKey)));
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(mockOtherPartiesResponse);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());

        subject.execute();

        final ArgumentCaptor<LinkedRefs> linkedRefsCaptor = forClass(LinkedRefs.class);
        verify(txnAccessor, never()).setPendingCompletions(any());
        verify(txnAccessor).setLinkedRefs(linkedRefsCaptor.capture());
        // verify linked refs
        final var linkedRefsCaptorValue = linkedRefsCaptor.getValue();
        assertEquals(0, linkedRefsCaptorValue.linkedNumbers()[0]);
        assertEquals(1, linkedRefsCaptorValue.linkedNumbers().length);
        final var linkedAliases = linkedRefsCaptorValue.linkedAliases();
        assertEquals(0, linkedAliases.size());
        miscCryptoUtilsStaticMock.close();
    }

    private void setupDegenerateMocks() {
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(mockPayerResponse);
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(mockOtherPartiesResponse);
        given(txnAccessor.getPayer()).willReturn(payer);
    }

    private static final JKey mockEd25519FullKey = new JEd25519Key("01234567890123456789012345678901".getBytes());
    private static final SigningOrderResult<ResponseCodeEnum> mockPayerResponse =
            new SigningOrderResult<>(List.of(mockEd25519FullKey));
    private static final SigningOrderResult<ResponseCodeEnum> mockOtherPartiesResponse =
            new SigningOrderResult<>(List.of(mockEd25519FullKey, mockEd25519FullKey));
}
