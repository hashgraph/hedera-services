/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.observers;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.EventImpl;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SimpleEventTracker {
    private final Map<ObservationType, Set<Hash>> simpleEventsObserved;

    public SimpleEventTracker() {
        this.simpleEventsObserved = new HashMap<>();
        for (ObservationType type : ObservationType.values()) {
            this.simpleEventsObserved.put(type, new HashSet<>());
        }
    }

    public void observe(ObservationType observation, EventImpl event) {
        if (isObserved(observation, event)) {
            throw new RuntimeException("Event should not be observed twice");
        }
        simpleEventsObserved.get(observation).add(event.getHash());
    }

    public boolean isObserved(ObservationType observation, EventImpl event) {
        return simpleEventsObserved.get(observation).contains(event.getHash());
    }

    public void clear() {
        for (Set<Hash> value : simpleEventsObserved.values()) {
            value.clear();
        }
    }
}
