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

package com.hedera.node.app.tss.cryptography.tss.groth21;

import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssPublicShare;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A helper class for {@link Groth21ShareExtractor} to extract keys and produce the aggregation.
 *
 * @param privateKeyFunction a function to create the aggregated entity from the message id and the privateKey
 * @param privateShareAggregator a function to create the {@link TssPrivateShare} from a list of the previous {@code P} entity
 * @param publicKeyFunction a function to create the aggregated entity from the message id and the publicKey
 * @param publicShareAggregator a function to create the {@link TssPublicShare} from a list of the previous {@code S} entity
 * @param <S> privateKey Type
 * @param <P> publicKey Type
 */
record KeyExtractionHelper<S, P>(
        @NonNull BiFunction<Integer, BlsPrivateKey, S> privateKeyFunction,
        @NonNull Function<List<S>, BlsPrivateKey> privateShareAggregator,
        @NonNull BiFunction<Integer, BlsPublicKey, P> publicKeyFunction,
        @NonNull Function<List<P>, BlsPublicKey> publicShareAggregator) {

    /**
     * Constructor
     * @param privateKeyFunction a function to create the aggregated entity from the message id and the privateKey
     * @param privateShareAggregator a function to create the {@link TssPrivateShare} from a list of the previous {@code P} entity
     * @param publicKeyFunction a function to create the aggregated entity from the message id and the publicKey
     * @param publicShareAggregator a function to create the {@link TssPublicShare} from a list of the previous {@code S} entity
     */
    public KeyExtractionHelper {
        Objects.requireNonNull(privateKeyFunction, "privateKeyFunction must not be null");
        Objects.requireNonNull(privateShareAggregator, "privateShareAggregator must not be null");
        Objects.requireNonNull(publicKeyFunction, "publicKeyFunction must not be null");
        Objects.requireNonNull(publicShareAggregator, "publicShareAggregator must not be null");
    }

    /**
     * Applies the function to obtain S
     * @param shareId the share id.
     * @param privateKey the produced privateKey from the message
     * @return an instance of S
     */
    S privateKey(final @NonNull Integer shareId, final @NonNull BlsPrivateKey privateKey) {
        return privateKeyFunction.apply(shareId, privateKey);
    }

    /**
     * Applies the function to obtain P
     * @param shareId the share id.
     * @param publicKey the produced privateKey from the message
     * @return an instance of P
     */
    P publicKey(final @NonNull Integer shareId, final @NonNull BlsPublicKey publicKey) {
        return publicKeyFunction.apply(shareId, publicKey);
    }

    /**
     * Applies the aggregation function to obtain a {@link BlsPrivateKey}
     * @param aggregation the list of all produced S
     * @return a {@link BlsPrivateKey} which is the aggregated form of all S
     */
    BlsPrivateKey aggregatePrivateKey(final @NonNull List<S> aggregation) {
        return privateShareAggregator.apply(aggregation);
    }

    /**
     * Applies the aggregation function to obtain a {@link BlsPublicKey}
     * @param aggregation the list of all produced P
     * @return a {@link BlsPublicKey} which is the aggregated form of all P
     */
    BlsPublicKey aggregatePublicKey(final @NonNull List<P> aggregation) {
        return publicShareAggregator.apply(aggregation);
    }
}
