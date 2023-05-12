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

package com.swirlds.platform.event;

import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.internal.EventImpl;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link SelfEventStorage} that can be accessed and modified by separate threads safely
 */
public class ThreadSafeSelfEventStorage implements SelfEventStorage, Clearable {
    private final AtomicReference<EventImpl> atomic = new AtomicReference<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public EventImpl getMostRecentSelfEvent() {
        return atomic.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMostRecentSelfEvent(final EventImpl selfEvent) {
        atomic.set(selfEvent);
    }

    @Override
    public void clear() {
        atomic.set(null);
    }
}
