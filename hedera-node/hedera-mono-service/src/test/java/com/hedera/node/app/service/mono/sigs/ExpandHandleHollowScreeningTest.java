/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JWildcardECDSAKey;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandHandleHollowScreeningTest {
    @Mock
    private PubKeyToSigBytes pkToSigFn;

    @Mock
    private SigRequirements sigReqs;

    @Mock
    private PlatformTxnAccessor txnAccessor;

    @Mock
    private TxnScopedPlatformSigFactory sigFactory;

    @Mock
    private TransactionSignature secp256k1Sig;

    @Mock
    private AccountID payer;

    @Mock
    private Expansion.CryptoSigsCreation cryptoSigsCreation;

    @Mock
    private AliasManager aliasManager;

    @Mock
    private SyncVerifier syncVerifier;

    @Mock
    private SigImpactHistorian sigImpactHistorian;

    @Mock
    private ReusableBodySigningFactory handleSigFactory;

    private MockedStatic<MiscCryptoUtils> miscStatic;
    private ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor;

    @BeforeEach
    void setUp() {
        miscStatic = mockStatic(MiscCryptoUtils.class);
    }

    @AfterEach
    void tearDown() {
        miscStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandlePayerHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        setupHollowScreeningTest(false);
        givenEcdsaSigs();
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        setupDegenerateMocks(new SigningOrderResult<>(List.of(key)), mockOtherPartiesResponse);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        // verify payer key in meta has been replaced
        final var expectedFinalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        assertEquals(expectedFinalKey, txnAccessor.getSigMeta().payerKey());
        // verify pending completions in txn accessor
        final var expectedPendingCompletions = List.of(new PendingCompletion(num, expectedFinalKey));
        assertEquals(expectedPendingCompletions, pendingCompletionCaptor.getValue());

        final var linkedRefs = subject.getLinkedRefs();
        assertTrue(linkedRefs.linkedAliases().contains(ByteString.copyFrom(evmAddressForKey1)));
        assertTrue(Arrays.contains(linkedRefs.linkedNumbers(), num.longValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlePayerHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        setupHollowScreeningTest(true);
        givenEcdsaSigs();
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        final var mockOtherKey = mock(JKey.class);
        setupDegenerateMocks(new SigningOrderResult<>(List.of(key)), new SigningOrderResult<>(List.of(mockOtherKey)));
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();

        final var subject =
                new Rationalization(syncVerifier, sigImpactHistorian, sigReqs, handleSigFactory, aliasManager);
        subject.performFor(txnAccessor);

        final var finalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        final var allPretendCompletions = List.of(new PendingCompletion(num, finalKey));
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        assertEquals(allPretendCompletions, pendingCompletionCaptor.getValue());
        assertEquals(finalKey, txnAccessor.getSigMeta().payerKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleOtherReqHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        setupHollowScreeningTest(true);
        givenEcdsaSigs();
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(jHollowKey.isForHollowAccount()).willReturn(true);
        final var payerKey = mock(JKey.class);
        given(payerKey.hasWildcardECDSAKey()).willReturn(true);
        final var payerHollowKey = mock(JWildcardECDSAKey.class);
        given(payerKey.getWildcardECDSAKey()).willReturn(payerHollowKey);
        given(payerHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        setupDegenerateMocks(new SigningOrderResult<>(List.of(payerKey)), new SigningOrderResult<>(List.of(key)));
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();

        final var subject =
                new Rationalization(syncVerifier, sigImpactHistorian, sigReqs, handleSigFactory, aliasManager);
        subject.performFor(txnAccessor);

        final var finalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        final var allPretendCompletions = List.of(new PendingCompletion(num, finalKey));
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        assertEquals(allPretendCompletions, pendingCompletionCaptor.getValue());
        assertEquals(finalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDoesNotAddPendingHollowCompletionIfAliasManagerShowsMissingEntity() {
        setupHollowScreeningTest(true);
        givenEcdsaSigs();
        final var key = mock(JKey.class);
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(aliasManager.lookupIdBy(alias)).willReturn(EntityNum.MISSING_NUM);
        setupDegenerateMocks(
                new SigningOrderResult<>(List.of(key)), new SigningOrderResult<>(List.of(mock(JKey.class))));

        final var subject =
                new Rationalization(syncVerifier, sigImpactHistorian, sigReqs, handleSigFactory, aliasManager);
        subject.performFor(txnAccessor);

        verify(txnAccessor, never()).setPendingCompletions(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandleOtherReqKeyHollowKeyGetsReplacedAndPutInPendingFinalizationWhenECDSASigIsPresent() {
        // mock out a ECDSA key + sig
        setupHollowScreeningTest(false);
        givenEcdsaSigs();
        // mock account and returned key
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        setupDegenerateMocks(mockPayerResponse, new SigningOrderResult<>(List.of(key)));
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.isForHollowAccount()).willReturn(true);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        // verify payer key in meta has been replaced
        final var expectedFinalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        assertEquals(expectedFinalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
        // verify pending completions in txn accessor
        final var expectedPendingCompletions = List.of(new PendingCompletion(num, expectedFinalKey));
        assertEquals(expectedPendingCompletions, pendingCompletionCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandleOtherReqKeyHollowKeyGetsReplacedButNotPutInPendingFinalizationWhenNotNeeded() {
        // mock out a ECDSA key + sig
        setupHollowScreeningTest(false);
        givenEcdsaSigs();
        // mock account and returned key
        final var key = mock(JKey.class);
        setupDegenerateMocks(mockPayerResponse, new SigningOrderResult<>(List.of(key)));
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.isForHollowAccount()).willReturn(false);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        final ArgumentCaptor<List<PendingCompletion>> pendingCompletionCaptor = forClass(List.class);
        verify(txnAccessor, never()).setPendingCompletions(pendingCompletionCaptor.capture());
        // verify payer key in meta has been replaced
        final var expectedFinalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        assertEquals(expectedFinalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandlePayerHollowKeyDoesNotGetReplacedWhenNoMatchingECDSAKey() {
        final JKey payerKey = mock(JKey.class);
        given(payerKey.hasWildcardECDSAKey()).willReturn(true);
        final JWildcardECDSAKey payerHollowKey = mock(JWildcardECDSAKey.class);
        given(payerKey.getWildcardECDSAKey()).willReturn(payerHollowKey);
        given(payerHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        setupDegenerateMocks(new SigningOrderResult<>(List.of(payerKey)), mockOtherPartiesResponse);
        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();
        setupHollowScreeningTest(false);
        givenEcdsaSigs();

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        verify(txnAccessor, never()).setPendingCompletions(any());
        // verify payer key in meta has NOT been replaced
        assertEquals(payerKey, txnAccessor.getSigMeta().payerKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandleHollowKeyInTheOtherReqKeysWithoutCorrespondingECDSAKeyInSigsIsNotReplaced() {
        setupHollowScreeningTest(false);
        givenEcdsaSigs();
        final var num = EntityNum.fromLong(666L);
        given(aliasManager.lookupIdBy(alias)).willReturn(num);
        final var key = mock(JKey.class);
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(jHollowKey.isForHollowAccount()).willReturn(true);
        final var payerKey = mock(JKey.class);
        given(payerKey.hasWildcardECDSAKey()).willReturn(true);
        final var payerHollowKey = mock(JWildcardECDSAKey.class);
        given(payerKey.getWildcardECDSAKey()).willReturn(payerHollowKey);
        given(payerHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        final var lonelyKey = mock(JKey.class);
        given(lonelyKey.hasWildcardECDSAKey()).willReturn(true);
        final var lonelyHollowKey = mock(JWildcardECDSAKey.class);
        given(lonelyKey.getWildcardECDSAKey()).willReturn(lonelyHollowKey);
        given(lonelyHollowKey.getEvmAddress()).willReturn("onodsnaod".getBytes());
        setupDegenerateMocks(
                new SigningOrderResult<>(List.of(payerKey)), new SigningOrderResult<>(List.of(key, lonelyKey)));

        willCallRealMethod().given(txnAccessor).setSigMeta(any(RationalizedSigMeta.class));
        willCallRealMethod().given(txnAccessor).getSigMeta();

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        final var finalKey = new JECDSASecp256k1Key(decompressedKeyBytes);
        verify(txnAccessor).setPendingCompletions(pendingCompletionCaptor.capture());
        assertEquals(1, pendingCompletionCaptor.getValue().size());
        final PendingCompletion pendingCompletion =
                pendingCompletionCaptor.getValue().get(0);
        assertEquals(new PendingCompletion(num, finalKey), pendingCompletion);
        assertEquals(finalKey, txnAccessor.getSigMeta().othersReqSigs().get(0));
        assertEquals(lonelyKey, txnAccessor.getSigMeta().othersReqSigs().get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandleDoesNotAddPendingHollowCompletionIfAliasManagerShowsMissingEntity() {
        setupHollowScreeningTest(false);
        final var key = mock(JKey.class);
        given(key.hasWildcardECDSAKey()).willReturn(true);
        final var jHollowKey = mock(JWildcardECDSAKey.class);
        given(key.getWildcardECDSAKey()).willReturn(jHollowKey);
        given(jHollowKey.getEvmAddress()).willReturn(evmAddressForKey1);
        given(aliasManager.lookupIdBy(alias)).willReturn(EntityNum.MISSING_NUM);
        setupDegenerateMocks(new SigningOrderResult<>(List.of(key)), mockOtherPartiesResponse);
        givenEcdsaSigs();

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        verify(txnAccessor, never()).setPendingCompletions(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preHandleNoHollowScreeningIfNoEcdsaWildcardKeysArePresent() {
        setupHollowScreeningTest(false);
        setupDegenerateMocks(mockPayerResponse, mockOtherPartiesResponse);
        final var screeningMockedStatic = mockStatic(HollowScreening.class);
        screeningMockedStatic
                .when(() -> HollowScreening.atLeastOneWildcardECDSAKeyIn(
                        mockEd25519FullKey, List.of(mockEd25519FullKey, mockEd25519FullKey)))
                .thenReturn(false);

        final var subject =
                new Expansion(txnAccessor, sigReqs, pkToSigFn, cryptoSigsCreation, sigFactory, aliasManager);
        subject.execute();

        verify(txnAccessor, never()).setPendingCompletions(any());
        screeningMockedStatic.verify(() -> HollowScreening.performFor(any(), any(), any(), any(), notNull()), never());
        screeningMockedStatic.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleNoHollowScreeningIfNoEcdsaWildcardKeysArePresent() {
        setupHollowScreeningTest(true);
        final var result = new SigningOrderResult<ResponseCodeEnum>(List.of(mock(JKey.class)));
        setupDegenerateMocks(result, result);
        final var screeningMockedStatic = mockStatic(HollowScreening.class);
        screeningMockedStatic
                .when(() -> HollowScreening.atLeastOneWildcardECDSAKeyIn(any(), any()))
                .thenCallRealMethod();

        final var subject =
                new Rationalization(syncVerifier, sigImpactHistorian, sigReqs, handleSigFactory, aliasManager);
        subject.performFor(txnAccessor);

        verify(txnAccessor, never()).setPendingCompletions(any());
        screeningMockedStatic.verify(() -> HollowScreening.performFor(any(), any(), any(), any(), notNull()), never());
        screeningMockedStatic.close();
    }

    private void setupDegenerateMocks(
            SigningOrderResult<ResponseCodeEnum> payerResponse,
            SigningOrderResult<ResponseCodeEnum> otherPartiesResponse) {
        TransactionBody degenTxnBody = TransactionBody.getDefaultInstance();
        given(txnAccessor.getTxn()).willReturn(degenTxnBody);
        given(sigReqs.keysForPayer(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(payerResponse);
        given(sigReqs.keysForOtherParties(eq(degenTxnBody), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(payer)))
                .willReturn(otherPartiesResponse);
        given(txnAccessor.getPayer()).willReturn(payer);
    }

    private void givenEcdsaSigs() {
        given(secp256k1Sig.getSignatureType()).willReturn(SignatureType.ECDSA_SECP256K1);
        given(secp256k1Sig.getExpandedPublicKeyDirect()).willReturn(pretendSecp256k1FullKey);
        given(pkToSigFn.hasAtLeastOneEcdsaSig()).willReturn(true);
    }

    private void setupHollowScreeningTest(boolean isForHandle) {
        pendingCompletionCaptor = forClass(List.class);

        miscStatic
                .when(() -> MiscCryptoUtils.compressSecp256k1(pretendSecp256k1FullKey))
                .thenReturn(decompressedKeyBytes);
        miscStatic
                .when(() -> MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(pretendSecp256k1FullKey))
                .thenReturn(evmAddressForKey1);
        given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);

        final var pretendEd25519FullKey = "COMPLETE".getBytes(StandardCharsets.UTF_8);
        final var pretendEd25519FullSig = "NONSENSE".getBytes(StandardCharsets.UTF_8);
        final var ed25519Sig = mock(TransactionSignature.class);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig);
                    obs.accept(KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig);
                    return null;
                })
                .given(pkToSigFn)
                .forEachUnusedSigWithFullPrefix(any());

        if (isForHandle) {
            given(txnAccessor.getPkToSigsFn()).willReturn(pkToSigFn);
            given(handleSigFactory.signAppropriately(
                            KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                    .willReturn(secp256k1Sig);
            given(handleSigFactory.signAppropriately(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig))
                    .willReturn(ed25519Sig);
        } else {
            given(cryptoSigsCreation.createFrom(any(), any(), any())).willReturn(new PlatformSigsCreationResult());
            given(sigFactory.signAppropriately(
                            KeyType.ECDSA_SECP256K1, pretendSecp256k1FullKey, pretendSecp256k1FullSig))
                    .willReturn(secp256k1Sig);
            given(sigFactory.signAppropriately(KeyType.ED25519, pretendEd25519FullKey, pretendEd25519FullSig))
                    .willReturn(ed25519Sig);
        }
    }

    private static final byte[] pretendSecp256k1FullKey = "ALSO_COMPLETE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] pretendSecp256k1FullSig = "ALSO_NONSENSE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] decompressedKeyBytes = "decompressedKey".getBytes();
    private static final byte[] evmAddressForKey1 = "evmAddressForKey1".getBytes();
    private static final ByteString alias = ByteStringUtils.wrapUnsafely(evmAddressForKey1);
    private static final JKey mockEd25519FullKey = new JEd25519Key("01234567890123456789012345678901".getBytes());
    private static final SigningOrderResult<ResponseCodeEnum> mockPayerResponse =
            new SigningOrderResult<>(List.of(mockEd25519FullKey));
    private static final SigningOrderResult<ResponseCodeEnum> mockOtherPartiesResponse =
            new SigningOrderResult<>(List.of(mockEd25519FullKey, mockEd25519FullKey));
}
