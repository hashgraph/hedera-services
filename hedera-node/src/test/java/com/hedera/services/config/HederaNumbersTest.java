/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.config;

import static com.hedera.services.context.properties.PropertyNames.HEDERA_REALM;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_SHARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.properties.PropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaNumbersTest {
    PropertySource properties;
    HederaNumbers subject;

    @BeforeEach
    void setup() {
        properties = mock(PropertySource.class);
        given(properties.getLongProperty(HEDERA_SHARD)).willReturn(1L);
        given(properties.getLongProperty(HEDERA_REALM)).willReturn(2L);

        subject = new HederaNumbers(properties);
    }

    @Test
    void hasExpectedNumbers() {
        // expect:
        assertEquals(1L, subject.shard());
        assertEquals(2L, subject.realm());
        assertEquals(750L, subject.numReservedSystemEntities());
    }
}
