/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.node.app.service.mono.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.sigs.SyncVerifiers.ALWAYS_VALID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SystemDeleteFactory.newSignedSystemDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.exception.KeyPrefixMismatchException;
import com.hedera.node.app.service.mono.sigs.factories.PlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResult;
import com.hedera.node.app.service.mono.sigs.sourcing.KeyType;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.sourcing.SigObserver;
import com.hedera.node.app.service.mono.sigs.verification.SyncVerifier;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HederaToPlatformSigOpsTest {
    private static List<JKey> payerKey;
    private static List<JKey> otherKeys;
    private static List<JKey> fullPrefixKeys;
    private PubKeyToSigBytes allSigBytes;
    private PlatformTxnAccessor platformTxn;
    private SigRequirements keyOrdering;

    @BeforeAll
    static void setupAll() throws Throwable {
        payerKey = List.of(KeyTree.withRoot(ed25519()).asJKey());
        otherKeys = List.of(
                KeyTree.withRoot(ed25519()).asJKey(),
                KeyTree.withRoot(ed25519()).asJKey());
        fullPrefixKeys = List.of(KeyTree.withRoot(ed25519()).asJKey());
    }

    @BeforeEach
    void setup() throws Throwable {
        allSigBytes = mock(PubKeyToSigBytes.class);
        keyOrdering = mock(SigRequirements.class);
        platformTxn = PlatformTxnAccessor.from(newSignedSystemDelete().get());
    }

    @SuppressWarnings("unchecked")
    private void wellBehavedOrdersAndSigSources() throws Exception {
        given(keyOrdering.keysForPayer(
                        eq(platformTxn.getTxn()), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(DEFAULT_PAYER)))
                .willReturn(new SigningOrderResult<>(payerKey));
        given(keyOrdering.keysForOtherParties(
                        eq(platformTxn.getTxn()), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(DEFAULT_PAYER)))
                .willReturn(new SigningOrderResult<>(otherKeys));
        given(allSigBytes.sigBytesFor(any()))
                .willReturn("1".getBytes())
                .willReturn("2".getBytes())
                .willReturn("3".getBytes());
        given(allSigBytes.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
        willAnswer(inv -> {
                    final var obs = (SigObserver) inv.getArgument(0);
                    obs.accept(KeyType.ED25519, fullPrefixKeys.get(0).getEd25519(), "4".getBytes());
                    return null;
                })
                .given(allSigBytes)
                .forEachUnusedSigWithFullPrefix(any());
    }

    @Test
    void includesSuccessfulExpansions() throws Exception {
        wellBehavedOrdersAndSigSources();

        expandIn(platformTxn, keyOrdering, allSigBytes);

        assertEquals(expectedSigsWithNoErrors(), platformTxn.getCryptoSigs());
        assertEquals(OK, platformTxn.getExpandedSigStatus());
    }

    @Test
    void returnsImmediatelyOnPayerKeyOrderFailure() {
        given(keyOrdering.keysForPayer(
                        eq(platformTxn.getTxn()), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(DEFAULT_PAYER)))
                .willReturn(new SigningOrderResult<>(INVALID_ACCOUNT_ID));

        expandIn(platformTxn, keyOrdering, allSigBytes);

        assertEquals(INVALID_ACCOUNT_ID, platformTxn.getExpandedSigStatus());
    }

    @Test
    void doesntAddSigsIfCreationResultIsNotSuccess() throws Exception {
        given(keyOrdering.keysForPayer(
                        eq(platformTxn.getTxn()), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(DEFAULT_PAYER)))
                .willReturn(new SigningOrderResult<>(payerKey));
        given(keyOrdering.keysForOtherParties(
                        eq(platformTxn.getTxn()), eq(CODE_ORDER_RESULT_FACTORY), any(), eq(DEFAULT_PAYER)))
                .willReturn(new SigningOrderResult<>(otherKeys));
        given(allSigBytes.sigBytesFor(any()))
                .willReturn("1".getBytes())
                .willReturn("2".getBytes())
                .willThrow(KeyPrefixMismatchException.class);

        expandIn(platformTxn, keyOrdering, allSigBytes);

        assertEquals(KEY_PREFIX_MISMATCH, platformTxn.getExpandedSigStatus());
        assertEquals(expectedSigsWithOtherPartiesCreationError(), platformTxn.getCryptoSigs());
    }

    @Test
    void rationalizesMissingSigs() throws Exception {
        final var rationalization = new Rationalization(ALWAYS_VALID, keyOrdering, new ReusableBodySigningFactory());
        final var captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);
        final var mockAccessor = mock(PlatformTxnAccessor.class);

        wellBehavedOrdersAndSigSources();
        givenMirrorMock(mockAccessor, platformTxn);

        rationalization.performFor(mockAccessor);

        assertEquals(OK, rationalization.finalStatus());
        assertTrue(rationalization.usedSyncVerification());

        verify(mockAccessor).setSigMeta(captor.capture());
        final var sigMeta = captor.getValue();
        assertEquals(expectedSigsWithNoErrors(), sigMeta.verifiedSigs());

        platformTxn.setSigMeta(sigMeta);
        assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
    }

    @Test
    void stopImmediatelyOnPayerKeyOrderFailure() {
        given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY, null, DEFAULT_PAYER))
                .willReturn(new SigningOrderResult<>(INVALID_ACCOUNT_ID));
        final var rationalization = new Rationalization(ALWAYS_VALID, keyOrdering, new ReusableBodySigningFactory());

        rationalization.performFor(platformTxn);

        assertEquals(INVALID_ACCOUNT_ID, rationalization.finalStatus());
    }

    @Test
    void stopImmediatelyOnOtherPartiesKeyOrderFailure() throws Exception {
        wellBehavedOrdersAndSigSources();
        given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY, null, DEFAULT_PAYER))
                .willReturn(new SigningOrderResult<>(INVALID_ACCOUNT_ID));
        final var rationalization = new Rationalization(ALWAYS_VALID, keyOrdering, new ReusableBodySigningFactory());

        rationalization.performFor(platformTxn);

        assertEquals(INVALID_ACCOUNT_ID, rationalization.finalStatus());
    }

    @Test
    void stopImmediatelyOnOtherPartiesSigCreationFailure() throws Exception {
        final var mockAccessor = mock(PlatformTxnAccessor.class);
        given(keyOrdering.keysForPayer(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY, null, DEFAULT_PAYER))
                .willReturn(new SigningOrderResult<>(payerKey));
        given(keyOrdering.keysForOtherParties(platformTxn.getTxn(), CODE_ORDER_RESULT_FACTORY, null, DEFAULT_PAYER))
                .willReturn(new SigningOrderResult<>(otherKeys));
        given(allSigBytes.sigBytesFor(any()))
                .willReturn("1".getBytes())
                .willReturn("2".getBytes())
                .willThrow(KeyPrefixMismatchException.class);
        givenMirrorMock(mockAccessor, platformTxn);
        final var rationalization = new Rationalization(ALWAYS_VALID, keyOrdering, new ReusableBodySigningFactory());

        rationalization.performFor(mockAccessor);

        assertEquals(KEY_PREFIX_MISMATCH, rationalization.finalStatus());
    }

    @Test
    void rationalizesOnlyMissingSigs() throws Exception {
        wellBehavedOrdersAndSigSources();
        platformTxn.addAllCryptoSigs(asValid(expectedSigsWithOtherPartiesCreationError()));
        final SyncVerifier syncVerifier = l -> {
            if (l.equals(expectedSigsWithOtherPartiesCreationError())) {
                throw new AssertionError("Payer sigs were verified async!");
            } else {
                ALWAYS_VALID.verifySync(l);
            }
        };
        final var mockAccessor = mock(PlatformTxnAccessor.class);
        final var captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);
        givenMirrorMock(mockAccessor, platformTxn);
        final var rationalization = new Rationalization(syncVerifier, keyOrdering, new ReusableBodySigningFactory());

        rationalization.performFor(mockAccessor);

        assertTrue(rationalization.usedSyncVerification());
        assertEquals(OK, rationalization.finalStatus());

        verify(mockAccessor).setSigMeta(captor.capture());
        final var sigMeta = captor.getValue();
        platformTxn.setSigMeta(sigMeta);
        assertEquals(expectedSigsWithNoErrors(), platformTxn.getSigMeta().verifiedSigs());
        assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
    }

    @Test
    void doesNothingToTxnIfAllSigsAreRational() throws Exception {
        wellBehavedOrdersAndSigSources();
        platformTxn.addAllCryptoSigs(asValid(expectedSigsWithNoErrors()));
        final SyncVerifier syncVerifier = l -> {
            throw new AssertionError("All sigs were verified async!");
        };
        final var mockAccessor = mock(PlatformTxnAccessor.class);
        final var captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);
        givenMirrorMock(mockAccessor, platformTxn);

        final var rationalization = new Rationalization(syncVerifier, keyOrdering, new ReusableBodySigningFactory());

        rationalization.performFor(mockAccessor);

        assertFalse(rationalization.usedSyncVerification());
        assertEquals(OK, rationalization.finalStatus());
        verify(mockAccessor).setSigMeta(captor.capture());
        final var sigMeta = captor.getValue();
        platformTxn.setSigMeta(sigMeta);
        assertEquals(expectedSigsWithNoErrors(), platformTxn.getCryptoSigs());
        assertTrue(allVerificationStatusesAre(VerificationStatus.VALID::equals));
        final var resultingSigs = platformTxn.getCryptoSigs();
        assertFalse(resultingSigs.isEmpty());
    }

    private boolean allVerificationStatusesAre(final Predicate<VerificationStatus> statusPred) {
        return platformTxn.getSigMeta().verifiedSigs().stream()
                .map(TransactionSignature::getSignatureStatus)
                .allMatch(statusPred);
    }

    private List<TransactionSignature> expectedSigsWithNoErrors() {
        return new ArrayList<>(List.of(
                dummyFor(payerKey.get(0), "1"),
                dummyFor(otherKeys.get(0), "2"),
                dummyFor(otherKeys.get(1), "3"),
                dummyFor(fullPrefixKeys.get(0), "4")));
    }

    private List<TransactionSignature> expectedSigsWithOtherPartiesCreationError() {
        return expectedSigsWithNoErrors().subList(0, 1);
    }

    private TransactionSignature dummyFor(final JKey key, final String sig) {
        return PlatformSigFactory.ed25519Sig(key.getEd25519(), sig.getBytes(), platformTxn.getTxnBytes());
    }

    private void givenMirrorMock(PlatformTxnAccessor mock, PlatformTxnAccessor real) {
        given(mock.getPkToSigsFn()).willReturn(allSigBytes);
        given(mock.getTxn()).willReturn(real.getTxn());
        given(mock.getPayer()).willReturn(real.getPayer());
        given(mock.getTxnBytes()).willReturn(real.getTxnBytes());
        given(mock.getCryptoSigs()).willReturn(real.getCryptoSigs());
    }
}
