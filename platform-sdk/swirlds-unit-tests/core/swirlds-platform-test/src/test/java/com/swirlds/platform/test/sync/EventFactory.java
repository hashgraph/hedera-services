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
import com.swirlds.platform.test.fixtures.event.EventImplTestUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * A simple, deterministic factory for Event instances
 */
public class EventFactory {
    public static ShadowEvent makeShadow(@NonNull final Random random) {
        return makeShadow(random, null);
    }

    public static ShadowEvent makeShadow(@NonNull final Random random, final ShadowEvent selfParent) {
        return makeShadow(random, selfParent, null);
    }

    public static ShadowEvent makeShadow(
            @NonNull final Random random, final ShadowEvent selfParent, final ShadowEvent otherParent) {

        final EventImpl eventImpl = EventImplTestUtils.createEventImpl(
                TestingEventBuilder.builder(random),
                selfParent == null ? null : selfParent.getEvent(),
                otherParent == null ? null : otherParent.getEvent());

        return new ShadowEvent(eventImpl, selfParent, otherParent);
    }
}
