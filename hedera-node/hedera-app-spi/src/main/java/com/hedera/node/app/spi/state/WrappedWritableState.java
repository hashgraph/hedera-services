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
import java.util.Iterator;
import java.util.Objects;

/**
 * An implementation of {@link WritableState} that delegates to another {@link WritableState} as
 * though it were the backend data source. Modifications to this {@link WrappedWritableState} are
 * buffered, along with reads, allowing code to rollback by simply throwing away the wrapper.
 *
 * @param <K> The key
 * @param <V> The value
 */
public class WrappedWritableState<K, V> extends WritableStateBase<K, V> {
    /**
     * The {@link WritableState} to delegate to for all read operations on cache miss, and for
     * committing changes
     */
    private final WritableState<K, V> delegate;

    /**
     * Create a new instance that will treat the given {@code delegate} as the backend data source.
     * Note that the lifecycle of the delegate <b>MUST</b> be as long as, or longer than, the
     * lifecycle of this instance. If the delegate is reset or decommissioned while being used as a
     * delegate, bugs will occur.
     *
     * @param delegate The delegate. Must not be null.
     */
    public WrappedWritableState(@NonNull final WritableState<K, V> delegate) {
        super(delegate.getStateKey());
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        return delegate.getForModify(key).orElse(null);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        delegate.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        delegate.remove(key);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        return delegate.get(key).orElse(null);
    }

    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return delegate.keys();
    }
}
