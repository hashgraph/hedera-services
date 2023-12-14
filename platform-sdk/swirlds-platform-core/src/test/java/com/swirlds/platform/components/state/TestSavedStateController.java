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

package com.swirlds.platform.components.state;

import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Deque;
import java.util.LinkedList;

public class TestSavedStateController extends SavedStateController {
    private final Deque<SignedState> queue = new LinkedList<>();

    public TestSavedStateController() {
        super(new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class));
    }

    @Override
    public synchronized void reconnectStateReceived(@NonNull final ReservedSignedState reservedSignedState) {
        queue.add(reservedSignedState.get());
    }

    @Override
    public synchronized void registerSignedStateFromDisk(@NonNull final SignedState signedState) {
        queue.add(signedState);
    }

    public @NonNull Deque<SignedState> getStatesQueue() {
        return queue;
    }
}
