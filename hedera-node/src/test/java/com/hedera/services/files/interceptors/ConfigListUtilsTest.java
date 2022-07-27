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
package com.hedera.services.files.interceptors;

import static com.hedera.services.files.interceptors.ConfigListUtils.isConfigList;
import static com.hedera.services.files.interceptors.ConfigListUtils.uncheckedParse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigListUtilsTest {
    private ServicesConfigurationList example =
            ServicesConfigurationList.newBuilder()
                    .addNameValue(Setting.newBuilder().setName("key").setValue("value"))
                    .build();

    @Test
    void recognizesParseable() {
        // given:
        var nonsense = "NONSENSE".getBytes();
        var truth = example.toByteArray();

        // when:
        var nonsenseFlag = isConfigList(nonsense);
        var truthFlag = isConfigList(truth);

        // then:
        assertFalse(nonsenseFlag);
        assertTrue(truthFlag);
    }

    @Test
    void parsesToDefaultIfInvalid() {
        // expect:
        Assertions.assertEquals(
                ServicesConfigurationList.getDefaultInstance(),
                uncheckedParse("NONSENSE".getBytes()));
    }

    @Test
    void parses() {
        // expect:
        Assertions.assertEquals(example, uncheckedParse(example.toByteArray()));
    }

    @Test
    void cannotBeConstructed() {
        // expect:
        assertThrows(IllegalStateException.class, ConfigListUtils::new);
    }
}
