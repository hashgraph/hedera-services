/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.internal;

import com.swirlds.common.config.BasicCommonConfig;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigurationBuilderFactoryImplTest {

    @Test
    public void testNotSameResults() {
        // given
        final ConfigurationBuilderFactory factory = new ConfigurationBuilderFactoryImpl();

        // when
        final ConfigurationBuilder configurationBuilder1 = factory.create();
        final ConfigurationBuilder configurationBuilder2 = factory.create();

        // then
        Assertions.assertNotNull(configurationBuilder1);
        Assertions.assertNotNull(configurationBuilder2);
        Assertions.assertNotSame(configurationBuilder1, configurationBuilder2);
    }

    @Test
    void testDefaultBehaviorExtensionScanning() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().autoDiscoverExtensions();
        final Configuration configuration = configurationBuilder.build();

        // then
        Assertions.assertFalse(configuration.getConfigDataTypes().isEmpty());
        Assertions.assertTrue(configuration.getConfigDataTypes().contains(BasicCommonConfig.class));
        Assertions.assertTrue(configuration.getConfigDataTypes().contains(StateCommonConfig.class));
    }

    @Test
    void testEmptyBuilderWithoutDiscovery() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();

        // when
        final Configuration configuration = configurationBuilder.build();

        // then
        Assertions.assertTrue(configuration.getConfigDataTypes().isEmpty());
    }
}
