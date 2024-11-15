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

package com.swirlds.platform.config;

import com.swirlds.config.api.ConfigurationBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AddressBookConfigTest {

    @Test
    public void testDefaultValuesValid() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataTypes(AddressBookConfig.class);
        // then
        Assertions.assertDoesNotThrow(() -> configurationBuilder.build(), "All default values should be valid");
    }

    @Test
    public void testUseRosterLifecycleDefaultValue() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataTypes(AddressBookConfig.class);
        // when
        AddressBookConfig config = configurationBuilder.build().getConfigData(AddressBookConfig.class);
        // then
        Assertions.assertFalse(config.useRosterLifecycle(), "The default value of useRosterLifecycle should be false");
    }
}
