/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class UtilsTest {
    @Test
    @DisplayName("networkProperties() returns expected properties")
    void networkProperties() {
        final var config = new TestConfigBuilder(false)
                .withConfigDataType(NoNetworkAnnotatedConfig.class)
                .withConfigDataType(MixedAnnotatedConfig.class)
                .getOrCreateConfig();

        final var propertyNames = Utils.networkProperties(config);
        assertThat(propertyNames).containsOnlyKeys("networkProperty");
    }
}
