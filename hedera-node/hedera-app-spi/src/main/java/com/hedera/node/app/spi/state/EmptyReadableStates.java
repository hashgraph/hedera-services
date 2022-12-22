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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** An implementation of {@link ReadableStates} that is always empty. */
public class EmptyReadableStates implements ReadableStates {
    private static final Set<String> EMPTY_SET = Collections.emptySet();

    @NonNull
    @Override
    public <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
        Objects.requireNonNull(stateKey);
        throw new IllegalArgumentException("There are no states");
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
        return false;
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return EMPTY_SET;
    }
}
