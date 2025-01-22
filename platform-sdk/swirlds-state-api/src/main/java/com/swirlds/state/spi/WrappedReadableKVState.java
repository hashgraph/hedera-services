/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
import java.util.Iterator;

/**
 * Used to wrap a {@link ReadableKVState}, allowing to buffer reads into the {@link
 * WrappedReadableKVState}.
 *
 * @param <K> The key of the state
 * @param <V> The value of the state
 */
public class WrappedReadableKVState<K extends Comparable<K>, V> extends ReadableKVStateBase<K, V> {
    /** The {@link ReadableKVState} to delegate to for all read operations on cache miss */
    private final ReadableKVState<K, V> delegate;

    /**
     * Create a new instance that will treat the given {@code delegate} as the backend data source.
     * Note that the lifecycle of the delegate <b>MUST</b> be as long as, or longer than, the
     * lifecycle of this instance. If the delegate is reset or decommissioned while being used as a
     * delegate, bugs will occur.
     *
     * @param delegate The delegate. Must not be null.
     */
    public WrappedReadableKVState(@NonNull final ReadableKVState<K, V> delegate) {
        super(delegate.getStateKey());
        this.delegate = delegate;
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        return delegate.get(key);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return delegate.keys();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    @Deprecated
    public long size() {
        return delegate.size();
    }
}
