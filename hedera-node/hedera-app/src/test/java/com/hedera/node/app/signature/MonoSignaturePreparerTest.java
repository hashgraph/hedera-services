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

package com.hedera.node.app.signature;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.exception.InvalidAccountIDException;
import com.hedera.node.app.service.mono.legacy.exception.KeyPrefixMismatchException;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.sigs.Expansion;
import com.hedera.node.app.service.mono.sigs.PlatformSigsCreationResult;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.verification.PrecheckVerifier;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoSignaturePreparerTest {
    private static final Transaction MOCK_TXN = new Transaction.Builder().build();

    private static final JKey PAYER_KEY = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private static final List<HederaKey> OTHER_PARTY_KEYS = List.of(
            new JEd25519Key("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()),
            new JEd25519Key("cccccccccccccccccccccccccccccccc".getBytes()));
    private static final Transaction MOCK_TRANSACTION = new Transaction.Builder().build();

    @Mock
    private PrecheckVerifier precheckVerifier;

    @Mock
    private Expansion.CryptoSigsCreation cryptoSigsCreation;

    @Mock
    private PubKeyToSigBytes keyToSigBytes;

    @Mock
    private Function<SignatureMap, PubKeyToSigBytes> keyToSigFactory;

    @Mock
    private TxnScopedPlatformSigFactory scopedFactory;

    @Mock
    private Function<TxnAccessor, TxnScopedPlatformSigFactory> scopedFactoryProvider;

    @Mock
    private PlatformSigsCreationResult payerResult;

    @Mock
    private PlatformSigsCreationResult otherPartiesResult;

    @Mock
    private TransactionSignature mockSig;

    private MonoSignaturePreparer subject;

    @BeforeEach
    void setUp() {
        subject =
                new MonoSignaturePreparer(precheckVerifier, cryptoSigsCreation, keyToSigFactory, scopedFactoryProvider);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void happyPathContainsBothPayerAndOtherPartySigsWithOK() {
        given(keyToSigFactory.apply(any())).willReturn(keyToSigBytes);
        given(scopedFactoryProvider.apply(any())).willReturn(scopedFactory);
        givenHappy(payerResult);
        given(cryptoSigsCreation.createFrom(List.of(PAYER_KEY), keyToSigBytes, scopedFactory))
                .willReturn(payerResult);
        givenHappyTwo(otherPartiesResult);
        given(cryptoSigsCreation.createFrom((List) OTHER_PARTY_KEYS, keyToSigBytes, scopedFactory))
                .willReturn(otherPartiesResult);

        final var result = subject.expandedSigsFor(MOCK_TRANSACTION, PAYER_KEY, OTHER_PARTY_KEYS);

        assertEquals(OK, result.status());
        assertEquals(List.of(mockSig, mockSig, mockSig), result.cryptoSigs());
    }

    @Test
    void abortsAfterPayerFailureAndIncludesNoSigs() {
        given(keyToSigFactory.apply(any())).willReturn(keyToSigBytes);
        given(scopedFactoryProvider.apply(any())).willReturn(scopedFactory);
        givenUnhappy(payerResult);
        given(cryptoSigsCreation.createFrom(List.of(PAYER_KEY), keyToSigBytes, scopedFactory))
                .willReturn(payerResult);

        final var result = subject.expandedSigsFor(MOCK_TRANSACTION, PAYER_KEY, OTHER_PARTY_KEYS);

        assertEquals(KEY_PREFIX_MISMATCH, result.status());
        assertEquals(List.of(), result.cryptoSigs());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void abortsAfterOtherPartyFailureAndIncludesOnlyPayerSigs() {
        given(keyToSigFactory.apply(any())).willReturn(keyToSigBytes);
        given(scopedFactoryProvider.apply(any())).willReturn(scopedFactory);
        givenHappyTwo(payerResult);
        given(cryptoSigsCreation.createFrom(List.of(PAYER_KEY), keyToSigBytes, scopedFactory))
                .willReturn(payerResult);
        givenUnhappy(otherPartiesResult);
        given(cryptoSigsCreation.createFrom((List) OTHER_PARTY_KEYS, keyToSigBytes, scopedFactory))
                .willReturn(otherPartiesResult);

        final var result = subject.expandedSigsFor(MOCK_TRANSACTION, PAYER_KEY, OTHER_PARTY_KEYS);

        assertEquals(KEY_PREFIX_MISMATCH, result.status());
        assertEquals(List.of(mockSig, mockSig), result.cryptoSigs());
    }

    @Test
    void delegatesPayerSigCheck() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(OK, status);
    }

    @Test
    void translatesKeyPrefixMismatch() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willThrow(KeyPrefixMismatchException.class);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(KEY_PREFIX_MISMATCH, status);
    }

    @Test
    void translatesInvalidIdException() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willThrow(InvalidAccountIDException.class);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void translatesUnrecognizedFailure() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willThrow(IllegalArgumentException.class);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(INVALID_SIGNATURE, status);
    }

    private void givenHappy(final PlatformSigsCreationResult result) {
        given(result.getPlatformSigs()).willReturn(List.of(mockSig));
    }

    private void givenHappyTwo(final PlatformSigsCreationResult result) {
        given(result.getPlatformSigs()).willReturn(List.of(mockSig, mockSig));
    }

    private void givenUnhappy(final PlatformSigsCreationResult result) {
        given(result.asCode()).willReturn(PbjConverter.fromPbj(KEY_PREFIX_MISMATCH));
        given(result.hasFailed()).willReturn(true);
    }
}
