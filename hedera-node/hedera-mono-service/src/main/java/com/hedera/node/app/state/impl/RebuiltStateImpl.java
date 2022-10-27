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
package com.hedera.node.app.state.impl;

import com.hedera.node.app.spi.state.State;
import com.swirlds.fchashmap.FCHashMap;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * An implementation of {@link State} backed by a {@link FCHashMap} for aliases, that needs to be
 * rebuilt from the {@link com.hedera.services.ServicesState}.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class RebuiltStateImpl<K, V> extends StateBase<K, V> {
    private Map<K, V> aliases;
    private final Instant lastModifiedTime;

    public RebuiltStateImpl(
            @Nonnull final String stateKey,
            @Nonnull Map<K, V> aliases,
            @Nonnull final Instant lastModifiedTime) {
        super(stateKey);
        this.aliases = Objects.requireNonNull(aliases);
        this.lastModifiedTime = lastModifiedTime;
    }

    RebuiltStateImpl(@Nonnull final String stateKey, @Nonnull final Instant lastModifiedTime) {
        this(stateKey, new FCHashMap<>(), lastModifiedTime);
    }

    @Override
    public Instant getLastModifiedTime() {
        return lastModifiedTime;
    }

    @Override
    protected V read(final K key) {
        return aliases.get(key);
    }
}
