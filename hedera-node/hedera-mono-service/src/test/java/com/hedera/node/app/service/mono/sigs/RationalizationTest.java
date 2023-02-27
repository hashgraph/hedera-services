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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JHollowKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
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
import com.hedera.node.app.service.mono.sigs.verification.SyncVerifier;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RationalizationTest {
    private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
    private final TransactionBody txn = TransactionBody.getDefaultInstance();
    private final SigningOrderResult<ResponseCodeEnum> generalError = CODE_ORDER_RESULT_FACTORY.forGeneralError();
    private final SigningOrderResult<ResponseCodeEnum> othersError = CODE_ORDER_RESULT_FACTORY.forImmutableContract();

    @Mock
    private PlatformTxnAccessor txnAccessor;

    @Mock
    private SyncVerifier syncVerifier;

    @Mock
    private SigRequirements keyOrderer;

    @Mock
    private ReusableBodySigningFactory sigFactory;

    @Mock
    private PubKeyToSigBytes pkToSigFn;

    @Mock
    private SigningOrderResult<ResponseCodeEnum> mockOrderResult;

    @Mock
    private SigImpactHistorian sigImpactHistorian;

    @Mock
    private AccountID payer;

    @Mock
    private LinkedRefs linkedRefs;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private SigMetadataLookup sigMetadataLookup;

    @Mock
    private TransactionSignature secp256k1Sig;

    private Rationalization subject;

    @BeforeEach
    void setUp() {
        subject = new Rationalization(syncVerifier, sigImpactHistorian, keyOrderer, sigFactory, aliasManager);
    }

    @Test
    void resetWorks() {
        final List<TransactionSignature> mockSigs = new ArrayList<>();
        final JKey fake = new JEd25519Key("FAKE".getBytes(StandardCharsets.UTF_8));

        subject = new Rationalization(syncVerifier, keyOrderer, sigFactory);

        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        // and:
        subject.getRealPayerSigs().add(null);
        subject.getRealOtherPartySigs().add(null);
        subject.setReqPayerSig(fake);
        subject.setReqOthersSigs(List.of(fake));
        subject.setLastOrderResult(CODE_ORDER_RESULT_FACTORY.forGeneralError());
        subject.setFinalStatus(INVALID_ACCOUNT_ID);
        subject.setVerifiedSync(true);

        // when:
        subject.resetFor(txnAccessor);

        // then:
        assertSame(txnAccessor, subject.getTxnAccessor());
        assertSame(syncVerifier, subject.getSyncVerifier());
        assertSame(keyOrderer, subject.getSigReqs());
        assertSame(pkToSigFn, subject.getPkToSigFn());
        assertEquals(mockSigs, subject.getTxnSigs());
        // and:
        assertTrue(subject.getRealPayerSigs().isEmpty());
        assertTrue(subject.getRealOtherPartySigs().isEmpty());
        // and:
        assertFalse(subject.usedSyncVerification());
        assertNull(subject.finalStatus());
        assertNull(subject.getReqPayerSig());
        assertNull(subject.getReqOthersSigs());
        assertNull(subject.getLastOrderResult());
        // and:
        verify(sigFactory).resetFor(txnAccessor);
        verify(pkToSigFn).resetAllSigsToUnused();
    }

    @Test
    void doesNothingIfLinkedRefsAvailableAndUnchanged() {
        given(txnAccessor.getLinkedRefs()).willReturn(linkedRefs);
        given(txnAccessor.getExpandedSigStatus()).willReturn(KEY_PREFIX_MISMATCH);
        given(linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian)).willReturn(true);
        subject.setVerifiedSync(true);

        subject.performFor(txnAccessor);

        verifyNoMoreInteractions(txnAccessor);
        assertEquals(KEY_PREFIX_MISMATCH, subject.finalStatus());
        assertFalse(subject.usedSyncVerification());
    }

    @Test
    void setsUnavailableMetaIfCannotListPayerKey() {
        given(txnAccessor.getLinkedRefs()).willReturn(linkedRefs);
        ArgumentCaptor<RationalizedSigMeta> captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);

        given(txnAccessor.getTxn()).willReturn(txn);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(keyOrderer.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY, null, payer))
                .willReturn(generalError);

        // when:
        subject.performFor(txnAccessor);

        // then:
        assertEquals(generalError.getErrorReport(), subject.finalStatus());
        // and:
        verify(txnAccessor).setSigMeta(captor.capture());
        assertSame(RationalizedSigMeta.noneAvailable(), captor.getValue());
    }

    @Test
    void propagatesFailureIfCouldNotExpandOthersKeys() {
        given(txnAccessor.getLinkedRefs()).willReturn(linkedRefs);
        given(linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian)).willReturn(true);
        ArgumentCaptor<RationalizedSigMeta> captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);

        given(txnAccessor.getTxn()).willReturn(txn);
        given(txnAccessor.getPayer()).willReturn(payer);
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(mockOrderResult.getPayerKey()).willReturn(payerKey);
        given(keyOrderer.keysForPayer(txn, CODE_ORDER_RESULT_FACTORY, null, payer))
                .willReturn(mockOrderResult);
        given(keyOrderer.keysForOtherParties(txn, CODE_ORDER_RESULT_FACTORY, null, payer))
                .willReturn(othersError);

        // when:
        subject.performFor(txnAccessor);

        // then:
        assertEquals(othersError.getErrorReport(), subject.finalStatus());
        // and:
        verify(txnAccessor).setSigMeta(captor.capture());
        final var sigMeta = captor.getValue();
        assertTrue(sigMeta.couldRationalizePayer());
        assertFalse(sigMeta.couldRationalizeOthers());
        assertSame(payerKey, sigMeta.payerKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    void payerHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
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
        given(keyOrderer.getSigMetaLookup()).willReturn(sigMetadataLookup);
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
        given(keyOrderer.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(key)));
        JKey mockOtherKey = mock(JKey.class);
        given(keyOrderer.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
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
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.performFor(txnAccessor);

        final var finalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        final var allPretendCompletions = List.of(new PendingCompletion(finalKey, num));
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        assertEquals(allPretendCompletions, pendingCompletionCaptor.getValue());
        assertEquals(finalKey, txnAccessor.getSigMeta().payerKey());
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void otherReqHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);

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
        given(keyOrderer.getSigMetaLookup()).willReturn(sigMetadataLookup);
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
        given(keyOrderer.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(payerKey)));
        given(keyOrderer.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(key)));
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
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.performFor(txnAccessor);

        final var finalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        final var allPretendCompletions = List.of(new PendingCompletion(finalKey, num));
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        assertEquals(allPretendCompletions, pendingCompletionCaptor.getValue());
        assertEquals(finalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotAddPendingHollowCompletionFromUnusedFullPrefixECDSASigIfAliasManagerShowsMissingEntity() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
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
        given(keyOrderer.getSigMetaLookup()).willReturn(sigMetadataLookup);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        given(aliasManager.lookupIdBy(alias)).willReturn(EntityNum.MISSING_NUM);

        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        JKey mockOtherKey = mock(JKey.class);
        given(keyOrderer.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
        given(keyOrderer.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.performFor(txnAccessor);

        verify(txnAccessor, never()).setPendingCompletions(any());
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hollowScreenOnUnsuccessfulMetadataLookup() {
        final var pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);

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
        given(keyOrderer.getSigMetaLookup()).willReturn(sigMetadataLookup);
        final var alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT));
        given(sigFactory.signAppropriately(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                .willReturn(secp256k1Sig);
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        JKey mockOtherKey = mock(JKey.class);
        given(keyOrderer.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
        given(keyOrderer.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.performFor(txnAccessor);

        verify(txnAccessor, never()).setPendingCompletions(any());
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonHollowAccountsAreNotAddedToPendingCompletions() {
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
        given(keyOrderer.getSigMetaLookup()).willReturn(sigMetadataLookup);
        given(sigMetadataLookup.accountSigningMetaFor(alias, null))
                .willReturn(new SafeLookupResult<>(
                        new AccountSigningMetadata(new JECDSASecp256k1Key("someBytesForKey".getBytes()), false)));
        // mock txn and sig reqs
        final var degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        JKey mockOtherKey = mock(JKey.class);
        given(keyOrderer.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
        given(keyOrderer.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(new SigningOrderResult<>(List.of(mockOtherKey)));
        given(txnAccessor.getPayer()).willReturn(payer);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());
        given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);

        subject.performFor(txnAccessor);

        verify(txnAccessor, never()).setPendingCompletions(any());
        miscCryptoUtilsStaticMock.close();
    }
}
