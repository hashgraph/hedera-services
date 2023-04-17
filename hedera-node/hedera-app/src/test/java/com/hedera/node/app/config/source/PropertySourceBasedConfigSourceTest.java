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

package com.hedera.node.app.config.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertySourceBasedConfigSourceTest {

    @Mock(strictness = Strictness.LENIENT)
    private PropertySource propertySource;

    @BeforeEach
    void configureMockForConfigData() {
        BDDMockito.given(propertySource.allPropertyNames()).willReturn(Set.of("a", "b", "c"));
        BDDMockito.given(propertySource.getRawValue("a")).willReturn("result-a");
        BDDMockito.given(propertySource.getRawValue("b")).willReturn("result-b");
        BDDMockito.given(propertySource.getRawValue("c")).willReturn("result-c");
    }

    @Test
    void testNullParam() {
        assertThatThrownBy(() -> new PropertySourceBasedConfigSource(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testUsageParam() {
        final PropertySourceBasedConfigSource configSource = new PropertySourceBasedConfigSource(propertySource);

        assertThat(configSource.getPropertyNames()).hasSize(3).contains("a", "b", "c");
        assertThat(configSource.getValue("a")).isEqualTo("result-a");
        assertThat(configSource.getValue("b")).isEqualTo("result-b");
        assertThat(configSource.getValue("c")).isEqualTo("result-c");
    }
}
