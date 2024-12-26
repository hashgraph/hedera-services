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

package com.hedera.node.app.hints.impl;

import static com.hedera.node.app.hints.HintsService.SIGNATURE_SCHEMA;
import static com.hedera.node.app.hints.impl.HintsModule.FAKE_BLS_PUBLIC_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.hints.HintsOperations;
import com.hedera.node.app.tss.api.FakeFieldElement;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HintsKeyAccessorImpl implements HintsKeyAccessor {
    BlsPrivateKey FAKE_BLS_PRIVATE_KEY =
            new BlsPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);

    private final HintsOperations operations;

    @Inject
    public HintsKeyAccessorImpl(@NonNull final HintsOperations operations) {
        this.operations = requireNonNull(operations);
    }

    @Override
    public Optional<Bytes> signWithBlsPrivateKey(final long constructionId, @NonNull final Bytes message) {
        final var signature = operations.signPartial(message, FAKE_BLS_PRIVATE_KEY);
        return Optional.of(Bytes.wrap(signature.toBytes()));
    }

    @Override
    public BlsPublicKey getOrCreateBlsPublicKey() {
        return FAKE_BLS_PUBLIC_KEY;
    }
}
