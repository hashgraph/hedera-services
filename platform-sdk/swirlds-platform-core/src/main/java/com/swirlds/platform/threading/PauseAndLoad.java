/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.threading;

import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;

/**
 * Pauses a thread while {@link LoadableFromSignedState} loads a state. This is useful for externally loading instances
 * that are not thread-safe
 */
public class PauseAndLoad implements LoadableFromSignedState {
    private final StoppableThread thread;
    private final LoadableFromSignedState loadable;

    /**
     * @param thread
     * 		the thread to pause
     * @param loadable
     * 		the instance to load the state into
     */
    public PauseAndLoad(final StoppableThread thread, final LoadableFromSignedState loadable) {
        this.thread = thread;
        this.loadable = loadable;
    }

    @Override
    public void loadFromSignedState(final SignedState signedState) {
        thread.pause();
        try {
            loadable.loadFromSignedState(signedState);
        } finally {
            thread.resume();
        }
    }
}
