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
package com.hedera.services.keys;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InHandleActivationHelperTest {
    private byte[] scopedTxnBytes = "ANYTHING".getBytes();
    private JKey other = new JEd25519Key("other".getBytes());
    private JKey scheduled = new JEd25519Key("scheduled".getBytes());
    private JKey secp256k1Scheduled = new JECDSASecp256k1Key("alsoScheduled".getBytes());
    private List<JKey> required = List.of(other, scheduled, secp256k1Scheduled);

    PlatformTxnAccessor accessor;

    RationalizedSigMeta sigMeta;
    TransactionSignature sig;
    CharacteristicsFactory characteristicsFactory;
    Function<byte[], TransactionSignature> sigsFn;
    List<TransactionSignature> sigs = new ArrayList<>();

    InHandleActivationHelper.Activation activation;

    InHandleActivationHelper subject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        scheduled.setForScheduledTxn(true);
        secp256k1Scheduled.setForScheduledTxn(true);

        characteristicsFactory = mock(CharacteristicsFactory.class);
        given(characteristicsFactory.inferredFor(any()))
                .willReturn(DEFAULT_ACTIVATION_CHARACTERISTICS);

        sigMeta = mock(RationalizedSigMeta.class);
        given(sigMeta.verifiedSigs()).willReturn(sigs);
        given(sigMeta.couldRationalizeOthers()).willReturn(true);
        given(sigMeta.othersReqSigs()).willReturn(required);

        accessor = mock(PlatformTxnAccessor.class);
        given(accessor.getTxnBytes()).willReturn(scopedTxnBytes);
        given(accessor.getSigMeta()).willReturn(sigMeta);

        sig = mock(TransactionSignature.class);

        sigsFn = mock(Function.class);
        given(sigMeta.pkToVerifiedSigFn()).willReturn(sigsFn);

        subject = new InHandleActivationHelper(characteristicsFactory, () -> accessor);

        activation = mock(InHandleActivationHelper.Activation.class);

        InHandleActivationHelper.activation = activation;
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesEmptyKeysOnErrorReport() {
        // setup:
        BiPredicate<JKey, TransactionSignature> tests =
                (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

        given(sigMeta.couldRationalizeOthers()).willReturn(false);

        // when:
        boolean ans = subject.areOtherPartiesActive(tests);

        // then:
        assertTrue(ans);

        // and:
        verify(activation, never()).test(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesExpectedSigsFnForOthers() {
        // setup:
        BiPredicate<JKey, TransactionSignature> tests =
                (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

        given(activation.test(other, sigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS))
                .willReturn(false);

        // when:
        boolean otherAns = subject.areOtherPartiesActive(tests);
        boolean scheduledAns =
                subject.areScheduledPartiesActive(TransactionBody.getDefaultInstance(), tests);

        // then:
        assertFalse(otherAns);
        assertFalse(scheduledAns);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesExpectedKeysForOtherPartiesActive() {
        // setup:
        BiPredicate<JKey, TransactionSignature> tests =
                (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

        given(activation.test(other, sigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS))
                .willReturn(true);

        // when:
        boolean ans = subject.areOtherPartiesActive(tests);
        boolean ansAgain = subject.areOtherPartiesActive(tests);

        // then:
        assertTrue(ans);
        assertTrue(ansAgain);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesExpectedKeysForScheduled() {
        // setup:
        BiPredicate<JKey, TransactionSignature> tests =
                (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

        given(activation.test(scheduled, sigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS))
                .willReturn(true);
        given(
                        activation.test(
                                secp256k1Scheduled,
                                sigsFn,
                                tests,
                                DEFAULT_ACTIVATION_CHARACTERISTICS))
                .willReturn(true);

        // when:
        boolean ans = subject.areScheduledPartiesActive(nonFileDelete(), tests);

        // then:
        assertTrue(ans);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsScheduledKeysAsExpected() {
        final var mockSecp2561kSig = mock(TransactionSignature.class);
        BiConsumer<JKey, TransactionSignature> visitor =
                (BiConsumer<JKey, TransactionSignature>) mock(BiConsumer.class);

        given(sigsFn.apply(scheduled.getEd25519())).willReturn(sig);
        given(sigsFn.apply(secp256k1Scheduled.getECDSASecp256k1Key())).willReturn(mockSecp2561kSig);

        // when:
        subject.visitScheduledCryptoSigs(visitor);

        // then:
        verify(visitor).accept(scheduled, sig);
        verify(visitor).accept(secp256k1Scheduled, mockSecp2561kSig);
    }

    @AfterEach
    void cleanup() {
        InHandleActivationHelper.activation = HederaKeyActivation::isActive;
    }

    private TransactionBody nonFileDelete() {
        return TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .build();
    }
}
