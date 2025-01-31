/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.eventhandling.EventConfig_;

public class Constants {
    /** The platform context to use for tests that use the birth round as ancient threshold. */
    public static final PlatformContext BIRTH_ROUND_PLATFORM_CONTEXT = TestPlatformContextBuilder.create()
            .withConfiguration(new TestConfigBuilder()
                    .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                    .getOrCreateConfig())
            .build();

    public static final PlatformContext DEFAULT_PLATFORM_CONTEXT =
            TestPlatformContextBuilder.create().build();
}
