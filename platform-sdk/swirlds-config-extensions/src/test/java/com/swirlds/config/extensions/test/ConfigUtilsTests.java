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

package com.swirlds.config.extensions.test;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.ConfigUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigUtilsTests {

    @Test
    void testNullParams() {
        // given
        final Configuration config = new TestConfigBuilder().getOrCreateConfig();

        // then
        Assertions.assertThatThrownBy(() -> ConfigUtils.haveEqualProperties(config, null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> ConfigUtils.haveEqualProperties(null, config))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> ConfigUtils.haveEqualProperties(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSimpleConfigs() {
        // given
        final Configuration config1 =
                new TestConfigBuilder().withValue("foo", "bar").getOrCreateConfig();
        final Configuration config2 =
                new TestConfigBuilder().withValue("foo", "bar").getOrCreateConfig();

        // then
        Assertions.assertThat(ConfigUtils.haveEqualProperties(config1, config2)).isTrue();
    }

    @Test
    void testSimpleDifferentConfigs1() {
        // given
        final Configuration config1 =
                new TestConfigBuilder().withValue("foo", "bar").getOrCreateConfig();
        final Configuration config2 =
                new TestConfigBuilder().withValue("foo", "barWrong").getOrCreateConfig();

        // then
        Assertions.assertThat(ConfigUtils.haveEqualProperties(config1, config2)).isFalse();
    }

    @Test
    void testSimpleDifferentConfigs2() {
        // given
        final Configuration config1 =
                new TestConfigBuilder().withValue("foo", "bar").getOrCreateConfig();
        final Configuration config2 = new TestConfigBuilder().getOrCreateConfig();

        // then
        Assertions.assertThat(ConfigUtils.haveEqualProperties(config1, config2)).isFalse();
    }

    @Test
    void testSimpleDifferentConfigs3() {
        // given
        final Configuration config1 =
                new TestConfigBuilder().withValue("foo", "bar").getOrCreateConfig();
        final Configuration config2 = new TestConfigBuilder()
                .withValue("foo", "bar")
                .withValue("foo2", "bar")
                .getOrCreateConfig();

        // then
        Assertions.assertThat(ConfigUtils.haveEqualProperties(config1, config2)).isFalse();
    }

    @Test
    void testSimpleDifferentConfigs4() {
        // given
        final Configuration config1 = new TestConfigBuilder()
                .withValue("foo", "bar")
                .withValue("foo2", "bar2")
                .withValue("foo3", "bar3")
                .getOrCreateConfig();
        final Configuration config2 = new TestConfigBuilder()
                .withValue("foo", "bar")
                .withValue("foo3", "bar3")
                .withValue("foo2", "bar2")
                .getOrCreateConfig();

        // then
        Assertions.assertThat(ConfigUtils.haveEqualProperties(config1, config2)).isTrue();
    }
}
