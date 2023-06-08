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

package com.hedera.node.config.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigDataSupportTest {

    @Test
    void testConfig() {
        // given
        HederaTestConfigBuilder builder = new HederaTestConfigBuilder(Set.of("com.hedera.node.config.data"));

        // then
        assertThatNoException().isThrownBy(() -> builder.getOrCreateConfig());
    }

    @Test
    void testConfigRecordsAvailable() {
        // given
        HederaTestConfigBuilder builder = new HederaTestConfigBuilder(Set.of("com.hedera.node.config.data"));
        Configuration config = builder.getOrCreateConfig();

        // when
        ApiPermissionConfig configData = config.getConfigData(ApiPermissionConfig.class);

        // then
        assertThat(configData).isNotNull();
    }
}
