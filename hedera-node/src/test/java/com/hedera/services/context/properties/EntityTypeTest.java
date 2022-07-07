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
package com.hedera.services.context.properties;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class EntityTypeTest {
    @Test
    void worksWithSomeValidTypes() {
        assertEquals(
                EnumSet.allOf(EntityType.class),
                EntityType.csvTypeSet("ACCOUNT,CONTRACT, FILE,SCHEDULE,TOKEN, TOPIC"));
    }

    @Test
    void worksWithEmptyString() {
        assertTrue(EntityType.csvTypeSet("").isEmpty());
    }

    @Test
    void throwsOnInvalidSpec() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityType.csvTypeSet("ACCOUNT,CONTRACTUALLY_SPEAKING"));
    }
}
