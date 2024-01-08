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

package com.swirlds.common.config;

import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TransactionConfigTest {
    @Test
    void testValidation() {
        final TestConfigBuilder builder = new TestConfigBuilder()
                .withValue(TransactionConfig_.MAX_TRANSACTION_BYTES_PER_EVENT, "1")
                .withValue(TransactionConfig_.TRANSACTION_MAX_BYTES, "12");

        Assertions.assertThrows(
                ConfigViolationException.class,
                builder::getOrCreateConfig,
                "Configuration failed based on 1 violations!");
    }
}
