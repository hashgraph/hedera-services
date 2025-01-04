/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hints.impl;

import static com.hedera.node.app.hints.HintsModule.FAKE_BLS_PUBLIC_KEY;
import static com.hedera.node.app.hints.HintsService.SIGNATURE_SCHEMA;
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsKeyPair;
import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.hints.HintsKeyAccessor;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.tss.fakes.FakeFieldElement;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HintsKeyAccessorImpl implements HintsKeyAccessor {
    private static final BlsPrivateKey FAKE_BLS_PRIVATE_KEY =
            new BlsPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    private static final BlsKeyPair FAKE_BLS_KEY_PAIR = new BlsKeyPair(FAKE_BLS_PRIVATE_KEY, FAKE_BLS_PUBLIC_KEY);

    private final HintsLibrary operations;

    @Inject
    public HintsKeyAccessorImpl(@NonNull final HintsLibrary operations) {
        this.operations = requireNonNull(operations);
    }

    @Override
    public Bytes signWithBlsPrivateKey(final long constructionId, @NonNull final Bytes message) {
        final var signature = operations.signPartial(message, FAKE_BLS_PRIVATE_KEY);
        return Bytes.wrap(signature.toBytes());
    }

    @Override
    public BlsKeyPair getOrCreateBlsKeyPair(final long constructionId) {
        return FAKE_BLS_KEY_PAIR;
    }
}
