/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.config;

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.validation.ConfigViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VirtualMapConfigTest {

    @Test
    public void testDefaultValuesValid() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataType(VirtualMapConfig.class);
        // then
        Assertions.assertDoesNotThrow(() -> configurationBuilder.build(), "All default values should be valid");
    }

    @Test
    public void testPercentHashThreadsOutOfRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.percentHashThreads", -1))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testPercentHashThreadsOutOfRangeMax() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.percentHashThreads", 101))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testPercentCleanerThreadsOutOfRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.percentCleanerThreads", -1))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testPercentCleanerThreadsOutOfRangeMax() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.percentCleanerThreads", 101))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testMaximumVirtualMapSizeOutOfRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.maximumVirtualMapSize", 0L))
                .withSources(new SimpleConfigSource("virtualMap.virtualMapWarningThreshold", 0L))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        // the "virtualMapWarningThreshold" depends on the "maximumVirtualMapSize" and therefore we can not test it
        // alone
        Assertions.assertEquals(3, exception.getViolations().size(), "We must exactly have 3 violation");
    }

    @Test
    public void testMaximumVirtualMapSizeOutOfRangeMax() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.maximumVirtualMapSize", Integer.MAX_VALUE + 1L))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testVirtualMapWarningIntervalOutOfRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.virtualMapWarningInterval", 0L))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testVirtualMapWarningThresholdOutOfRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.virtualMapWarningThreshold", 0L))
                .withSources(new SimpleConfigSource("virtualMap.virtualMapWarningInterval", -1L))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        // the "virtualMapWarningInterval" depends on the "virtualMapWarningThreshold" and therefore we can not test it
        // alone
        Assertions.assertEquals(2, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testFlushIntervalOutOfRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.flushInterval", 0L))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }

    @Test
    public void testNumCleanerThreadsRangeMin() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("virtualMap.numCleanerThreads", -2))
                .withConfigDataType(VirtualMapConfig.class);

        // then
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "init must end in a violation");

        Assertions.assertEquals(1, exception.getViolations().size(), "We must exactly have 1 violation");
    }
}
