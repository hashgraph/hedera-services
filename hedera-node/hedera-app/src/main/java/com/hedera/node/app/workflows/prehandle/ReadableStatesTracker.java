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
package com.hedera.node.app.workflows.prehandle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

public class ReadableStatesTracker {

    private final HederaState hederaState;
    private final Map<String, ReadableStates> usedStates = new HashMap<>();

    public ReadableStatesTracker(@NonNull final HederaState hederaState) {
        this.hederaState = requireNonNull(hederaState);
    }

    @NonNull
    public Map<String, ReadableStates> getUsedStates() {
        return usedStates;
    }

    @NonNull
    public ReadableStates getReadableStates(@NonNull final String key) {
        requireNonNull(key);
        return usedStates.computeIfAbsent(key, hederaState::createReadableStates);
    }
}
