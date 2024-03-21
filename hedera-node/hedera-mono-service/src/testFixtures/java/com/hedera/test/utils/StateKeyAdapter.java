/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.test.utils;

import com.swirlds.platform.state.spi.ReadableKVState;
import com.swirlds.platform.state.spi.ReadableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.function.Function;

public class StateKeyAdapter<K1, K2, V> extends ReadableKVStateBase<K2, V> {
    private final ReadableKVState<K1, V> delegate;
    private final Function<K2, K1> keyAdapter;

    public StateKeyAdapter(final ReadableKVState<K1, V> delegate, final Function<K2, K1> keyAdapter) {
        super("Unspecified");
        this.delegate = delegate;
        this.keyAdapter = keyAdapter;
    }

    @Override
    protected V readFromDataSource(@NonNull K2 key) {
        return delegate.get(keyAdapter.apply(key));
    }

    @NonNull
    @Override
    protected Iterator<K2> iterateFromDataSource() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** {@inheritDoc} */
    @Override
    public long size() {
        return delegate.size();
    }
}
