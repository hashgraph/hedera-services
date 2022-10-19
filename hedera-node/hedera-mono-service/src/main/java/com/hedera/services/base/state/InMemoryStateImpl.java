/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.base.state;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * An implementation of {@link com.hedera.services.base.state.State} backed by a {@link MerkleMap},
 * resulting in a state that is stored in memory.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public class InMemoryStateImpl<K, V extends MerkleNode & Keyed<K>> extends StateBase<K, V> {
    private final MerkleMap<K, V> merkle;
    private final Instant lastModifiedTime;

    InMemoryStateImpl(@Nonnull final String stateKey, @Nonnull final Instant lastModifiedTime) {
        super(stateKey);
        this.merkle = new MerkleMap<>();
        this.lastModifiedTime = lastModifiedTime;
    }

    public InMemoryStateImpl(
            @Nonnull String stateKey,
            @Nonnull MerkleMap<K, V> merkleMap,
            @Nonnull final Instant lastModifiedTime) {
        super(stateKey);
        this.merkle = Objects.requireNonNull(merkleMap);
        this.lastModifiedTime = lastModifiedTime;
    }

    @Override
    public Instant getLastModifiedTime() {
        return lastModifiedTime;
    }

    @Override
    protected V read(final K key) {
        return merkle.get(key);
    }
}
