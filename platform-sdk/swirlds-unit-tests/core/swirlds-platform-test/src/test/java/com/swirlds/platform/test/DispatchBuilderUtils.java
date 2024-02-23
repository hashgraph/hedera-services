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

package com.swirlds.platform.test;

import com.swirlds.platform.dispatch.DispatchConfiguration;

/**
 * Utilities for tests utilizing the {@link com.swirlds.platform.dispatch.DispatchBuilder}.
 */
public final class DispatchBuilderUtils {

    private DispatchBuilderUtils() {}

    private static DispatchConfiguration defaultConfiguration = new DispatchConfiguration(false, "", "", "", "");

    /**
     * Get a default configuration for the dispatch builder.
     */
    public static DispatchConfiguration getDefaultDispatchConfiguration() {
        return defaultConfiguration;
    }
}
