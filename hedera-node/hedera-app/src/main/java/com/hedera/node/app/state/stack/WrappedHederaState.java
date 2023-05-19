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

package com.hedera.node.app.state.stack;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.RecordCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

public class WrappedHederaState implements HederaState {

    private final HederaState delegate;
    private final Map<String, WrappedWritableStates> writableStatesMap = new HashMap<>();

    public WrappedHederaState(@NonNull final HederaState delegate) {
        this.delegate = requireNonNull(delegate, "delegate must not be null");
    }

    public boolean isModified() {
        for (final WrappedWritableStates writableStates : writableStatesMap.values()) {
            if (writableStates.isModified()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public ReadableStates createReadableStates(@NonNull String serviceName) {
        return delegate.createReadableStates(serviceName);
    }

    @NonNull
    @Override
    public WritableStates createWritableStates(@NonNull String serviceName) {
        return writableStatesMap.computeIfAbsent(
                serviceName, s -> new WrappedWritableStates(delegate.createWritableStates(s)));
    }

    @NonNull
    @Override
    public RecordCache getRecordCache() {
        throw new UnsupportedOperationException();
    }

    public void commit() {
        for (final WrappedWritableStates writableStates : writableStatesMap.values()) {
            writableStates.commit();
        }
    }
}
