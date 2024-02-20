/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;

/**
 * A simple, deterministic factory for Event instances
 */
public class EventFactory {
    public static ShadowEvent makeShadow() {
        return makeShadow(null);
    }

    public static ShadowEvent makeShadow(final ShadowEvent selfParent) {
        return makeShadow(selfParent, null);
    }

    public static ShadowEvent makeShadow(final ShadowEvent selfParent, final ShadowEvent otherParent) {
        final EventImpl e = TestingEventBuilder.builder()
                .setSelfParent(selfParent == null ? null : selfParent.getEvent())
                .setOtherParent(otherParent == null ? null : otherParent.getEvent())
                .buildEventImpl();
        return new ShadowEvent(e, selfParent, otherParent);
    }
}
