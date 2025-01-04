/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.ProofKeysAccessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProofKeysAccessorImpl implements ProofKeysAccessor {
    private static final Bytes FAKE_SCHNORR_PRIVATE_KEY = Bytes.wrap("fake_schnorr_private_key".getBytes());
    private static final Bytes FAKE_SCHNORR_PUBLIC_KEY = Bytes.wrap("fake_schnorr_public_key".getBytes());
    private static final SchnorrKeyPair FAKE_SCHNORR_KEY_PAIR =
            new SchnorrKeyPair(FAKE_SCHNORR_PRIVATE_KEY, FAKE_SCHNORR_PUBLIC_KEY);

    private final HistoryLibrary operations;

    @Inject
    public ProofKeysAccessorImpl(@NonNull final HistoryLibrary operations) {
        this.operations = requireNonNull(operations);
    }

    @Override
    public Bytes signWithSchnorrPrivateKey(final long constructionId, @NonNull final Bytes message) {
        return operations.signSchnorr(message, FAKE_SCHNORR_PRIVATE_KEY);
    }

    @Override
    public SchnorrKeyPair getOrCreateSchnorrKeyPair(final long constructionId) {
        return FAKE_SCHNORR_KEY_PAIR;
    }
}
