/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.spi;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** An implementation of {@link ReadableStates} that is always empty. */
public final class EmptyReadableStates implements ReadableStates {
    public static final ReadableStates INSTANCE = new EmptyReadableStates();

    @NonNull
    @Override
    public <K, V extends Record> ReadableKVState<K, V> get(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);
        throw new IllegalArgumentException("There are no k/v states");
    }

    @NonNull
    @Override
    public <T> ReadableSingletonState<T> getSingleton(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);
        throw new IllegalArgumentException("There are no singleton states");
    }

    @NonNull
    @Override
    public <E> ReadableQueueState<E> getQueue(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);
        throw new IllegalArgumentException("There are no queue states");
    }

    @Override
    public boolean contains(@NonNull final String stateKey) {
        return false;
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return Collections.emptySet();
    }
}
