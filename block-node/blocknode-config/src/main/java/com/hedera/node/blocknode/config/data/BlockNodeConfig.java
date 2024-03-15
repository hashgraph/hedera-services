/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.blocknode.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

@ConfigData("BlockNodeConfig")
public record BlockNodeConfig(
        @ConfigProperty(defaultValue = "1") @Min(0) @Max(6) int fixedThreads,
        @ConfigProperty(defaultValue = "1") @Min(0) @Max(2) int scheduledThreads,
        @ConfigProperty(defaultValue = "2000") @Min(0) @Max(2000) int duration) {
    public BlockNodeConfig {
        if (fixedThreads == 0 || scheduledThreads == 0) {
            throw new IllegalArgumentException("Threads handled by block node cannot be lower than 0!");
        }
    }
}
