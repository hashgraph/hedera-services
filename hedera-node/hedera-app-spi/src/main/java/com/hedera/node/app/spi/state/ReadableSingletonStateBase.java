/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import java.util.Objects;
import java.util.function.Supplier;

public class ReadableSingletonStateBase<T> implements ReadableSingletonState<T> {
    private final String stateKey;
    private final Supplier<T> backingStoreAccessor;
    private boolean read = false;

    public ReadableSingletonStateBase(
            @NonNull final String stateKey, @NonNull final Supplier<T> backingStoreAccessor) {
        this.stateKey = Objects.requireNonNull(stateKey);
        this.backingStoreAccessor = Objects.requireNonNull(backingStoreAccessor);
    }

    @Override
    public final String getStateKey() {
        return stateKey;
    }

    @Override
    public T get() {
        // TODO Should this be cached after first read? Repeatable read vs. dirty read
        this.read = true;
        return backingStoreAccessor.get();
    }

    @Override
    public boolean read() {
        return read;
    }

    public void reset() {
        this.read = false;
    }
}
