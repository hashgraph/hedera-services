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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.node.app.spi.signatures.SimpleKeyCount;
import com.hedera.node.app.spi.signatures.SimpleKeyVerification;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implements the {@link SignatureVerifier} interface for verifying signatures using the same components
 * as used by the app workflows.
 */
@Singleton
public class SpiSignatureVerifier implements SignatureVerifier {
    private static final int EDDSA_COUNT_INDEX = 0;
    private static final int ECDSA_COUNT_INDEX = 1;

    private final ConfigProvider configProvider;
    private final SignatureExpander signatureExpander;
    private final com.hedera.node.app.signature.SignatureVerifier signatureVerifier;

    @Inject
    public SpiSignatureVerifier(
            @NonNull final ConfigProvider configProvider,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final com.hedera.node.app.signature.SignatureVerifier signatureVerifier) {
        this.configProvider = configProvider;
        this.signatureExpander = signatureExpander;
        this.signatureVerifier = signatureVerifier;
    }

    @Override
    public boolean verifySignature(
            @NonNull final Key key,
            @NonNull final Bytes bytes,
            @NonNull final SignatureMap signatureMap,
            @Nullable final Function<Key, SimpleKeyVerification> simpleKeyVerifier) {
        final Set<ExpandedSignaturePair> sigPairs = new HashSet<>();
        signatureExpander.expand(key, signatureMap.sigPair(), sigPairs);
        final var results = signatureVerifier.verify(bytes, sigPairs);
        final var verifier =
                new DefaultKeyVerifier(0, configProvider.getConfiguration().getConfigData(HederaConfig.class), results);
        return simpleKeyVerifier == null
                ? verifier.verificationFor(key).passed()
                : verifier.verificationFor(key, (k, v) -> switch (simpleKeyVerifier.apply(k)) {
                            case VALID -> true;
                            case INVALID -> false;
                            case ONLY_IF_CRYPTO_SIG_VALID -> v.passed();
                        })
                        .passed();
    }

    @Override
    public SimpleKeyCount countSimpleKeys(@NonNull final Key key) {
        final int[] counts = new int[2];
        countSimpleKeys(key, counts);
        return new SimpleKeyCount(counts[EDDSA_COUNT_INDEX], counts[ECDSA_COUNT_INDEX]);
    }

    private void countSimpleKeys(@NonNull final Key key, @NonNull final int[] counts) {
        switch (key.key().kind()) {
            case ED25519 -> counts[EDDSA_COUNT_INDEX]++;
            case ECDSA_SECP256K1 -> counts[ECDSA_COUNT_INDEX]++;
            case KEY_LIST -> key.keyListOrThrow().keys().forEach(k -> countSimpleKeys(k, counts));
            case THRESHOLD_KEY -> key.thresholdKeyOrThrow().keys().keys().forEach(k -> countSimpleKeys(k, counts));
            default -> {
                // No-op, we only count these two key types
            }
        }
    }
}
