/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.SimpleKeyCount;
import com.hedera.node.app.spi.signatures.SimpleKeyVerification;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpiSignatureVerifierTest {
    private static final Key A_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    private static final Key B_KEY = Key.newBuilder()
            .ecdsaSecp256k1(Bytes.fromHex("020202020202020202020202020202020202020202020202020202020202020202"))
            .build();
    private static final Key C_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(666L).build())
            .build();
    private static final Key TEST_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder().threshold(1).keys(new KeyList(List.of(A_KEY, B_KEY, C_KEY))))
            .build();

    @Mock
    private SignatureExpander signatureExpander;

    @Mock
    private com.hedera.node.app.signature.SignatureVerifier signatureVerifier;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private SignatureVerification verification;

    @Mock
    private SignatureVerificationFuture future;

    private SpiSignatureVerifier subject;

    @BeforeEach
    void setUp() {
        subject = new SpiSignatureVerifier(configProvider, signatureExpander, signatureVerifier);
    }

    @Test
    void usesDefaultKeyVerifierWithNoOverride() throws ExecutionException, InterruptedException, TimeoutException {
        final Set<ExpandedSignaturePair> expandedPairs =
                Set.of(new ExpandedSignaturePair(A_KEY, A_KEY.ed25519OrThrow(), null, SignaturePair.DEFAULT));
        willAnswer(invocationOnMock -> {
                    final Set<ExpandedSignaturePair> pairs = invocationOnMock.getArgument(2);
                    pairs.addAll(expandedPairs);
                    return null;
                })
                .given(signatureExpander)
                .expand(eq(TEST_KEY), eq(SignatureMap.DEFAULT.sigPair()), any(Set.class));

        final var hederaConfig = DEFAULT_CONFIG.getConfigData(HederaConfig.class);
        given(future.get(hederaConfig.workflowVerificationTimeoutMS(), TimeUnit.MILLISECONDS))
                .willReturn(verification);
        given(signatureVerifier.verify(Bytes.EMPTY, expandedPairs)).willReturn(Map.of(A_KEY, future));
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 0L));

        assertThat(subject.verifySignature(TEST_KEY, Bytes.EMPTY, SignatureMap.DEFAULT, null))
                .isTrue();
        // The default verifier counts the number of failed verifications in a key list, not the number passed
        verify(verification).failed();
    }

    @Test
    void usesDefaultKeyVerifierWithValidOverride() throws ExecutionException, InterruptedException, TimeoutException {
        final Set<ExpandedSignaturePair> expandedPairs =
                Set.of(new ExpandedSignaturePair(A_KEY, A_KEY.ed25519OrThrow(), null, SignaturePair.DEFAULT));
        willAnswer(invocationOnMock -> {
                    final Set<ExpandedSignaturePair> pairs = invocationOnMock.getArgument(2);
                    pairs.addAll(expandedPairs);
                    return null;
                })
                .given(signatureExpander)
                .expand(eq(TEST_KEY), eq(SignatureMap.DEFAULT.sigPair()), any(Set.class));

        final var hederaConfig = DEFAULT_CONFIG.getConfigData(HederaConfig.class);
        given(future.get(hederaConfig.workflowVerificationTimeoutMS(), TimeUnit.MILLISECONDS))
                .willReturn(verification);
        given(signatureVerifier.verify(Bytes.EMPTY, expandedPairs)).willReturn(Map.of(A_KEY, future));
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 0L));

        assertThat(subject.verifySignature(
                        TEST_KEY,
                        Bytes.EMPTY,
                        SignatureMap.DEFAULT,
                        key -> key == C_KEY
                                ? SimpleKeyVerification.VALID
                                : SimpleKeyVerification.ONLY_IF_CRYPTO_SIG_VALID))
                .isTrue();
        // Here we are directly using the crypto verification passed() method ourselves in the callback
        verify(verification).passed();
    }

    @Test
    void usesDefaultKeyVerifierWithInvalidOverride() {
        final Set<ExpandedSignaturePair> expandedPairs =
                Set.of(new ExpandedSignaturePair(A_KEY, A_KEY.ed25519OrThrow(), null, SignaturePair.DEFAULT));
        willAnswer(invocationOnMock -> {
                    final Set<ExpandedSignaturePair> pairs = invocationOnMock.getArgument(2);
                    pairs.addAll(expandedPairs);
                    return null;
                })
                .given(signatureExpander)
                .expand(eq(TEST_KEY), eq(SignatureMap.DEFAULT.sigPair()), any(Set.class));

        given(signatureVerifier.verify(Bytes.EMPTY, expandedPairs)).willReturn(Map.of(A_KEY, future));
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 0L));

        assertThat(subject.verifySignature(
                        TEST_KEY, Bytes.EMPTY, SignatureMap.DEFAULT, key -> SimpleKeyVerification.INVALID))
                .isFalse();
    }

    @Test
    void countsKeysAsExpected() {
        final var structure = Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder()
                        .threshold(2)
                        .keys(KeyList.newBuilder()
                                .keys(
                                        TEST_KEY,
                                        TEST_KEY,
                                        Key.newBuilder()
                                                .keyList(KeyList.newBuilder()
                                                        .keys(A_KEY, C_KEY, TEST_KEY)
                                                        .build())
                                                .build())
                                .build())
                        .build())
                .build();
        final var expectedCount = new SimpleKeyCount(4, 3);
        final var actualCount = subject.countSimpleKeys(structure);
        assertThat(actualCount).isEqualTo(expectedCount);
    }
}
