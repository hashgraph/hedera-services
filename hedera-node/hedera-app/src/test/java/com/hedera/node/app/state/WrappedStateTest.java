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
package com.hedera.node.app.state;

import java.util.HashMap;
import java.util.Map;

/**
 * This test extends the {@link StateBaseTest}, getting all the test methods used there, but this
 * time executed on a {@link WrappedState} rather than on the {@link
 * com.hedera.node.app.state.StateBaseTest.DummyState} directly.
 */
class WrappedStateTest extends StateBaseTest {
    protected StateBase<String, String> createState() {
        final Map<String, String> backingStore = new HashMap<>();
        backingStore.put(KNOWN_KEY_1, KNOWN_VALUE_1);
        backingStore.put(KNOWN_KEY_2, KNOWN_VALUE_2);
        final var dummy = new DummyState(STATE_KEY, backingStore);
        return new WrappedState<>(dummy);
    }
}
