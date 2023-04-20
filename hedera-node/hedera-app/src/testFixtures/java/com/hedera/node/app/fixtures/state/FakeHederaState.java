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

package com.hedera.node.app.fixtures.state;

import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.ReceiptCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

/** A useful test double for {@link HederaState}. Works together with {@link MapReadableStates} and other fixtures. */
public class FakeHederaState implements HederaState {
    private final ReceiptCache receiptCache = new FakeReceiptCache();
    // Key is Service, value is Map of state name to ReadableKVState
    private final Map<String, Map<String, ReadableKVState<?, ?>>> data = new HashMap<>();

    /** Adds to the service with the given name the {@link ReadableKVState} {@code states} */
    public void addService(@NonNull final String serviceName, @NonNull final ReadableKVState<?, ?>... states) {
        var serviceStates = data.computeIfAbsent(serviceName, k -> new HashMap<>());
        for (final var state : states) {
            serviceStates.put(state.getStateKey(), state);
        }
    }

    @NonNull
    @Override
    public ReadableStates createReadableStates(@NonNull String serviceName) {
        return new MapReadableStates(data.get(serviceName));
    }

    @NonNull
    @Override
    public WritableStates createWritableStates(@NonNull String serviceName) {
        return new MapWritableStates(data.get(serviceName));
    }

    @NonNull
    @Override
    public ReceiptCache getReceiptCache() {
        return receiptCache;
    }
}
