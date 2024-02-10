/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.extensions.test.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.ext.swirlds.config.extensions.test.ConfigExportTestConstants.ConfigExportTestRecord;
import com.ext.swirlds.config.extensions.test.ConfigExportTestConstants.PrefixedConfigExportTestRecord;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.export.ConfigExport;
import com.swirlds.config.extensions.sources.SimpleConfigSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigExportTest {

    @Test
    void testPrint() throws IOException {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(ConfigExportTestRecord.class)
                .withConfigDataType(PrefixedConfigExportTestRecord.class)
                .withSource(new SimpleConfigSource("property", "value"))
                .withSource(new SimpleConfigSource("prefix.property", "anotherValue"))
                .withSource(new SimpleConfigSource("prefix.unmappedProperty", "notPresentValue"))
                .withSource(new SimpleConfigSource("unmappedProperty", "anotherNotPresentValue"))
                .build();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // when
        ConfigExport.printConfig(configuration, outputStream);
        final List<String> lines =
                outputStream.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList());

        // then
        Assertions.assertNotNull(lines);
        Assertions.assertFalse(lines.isEmpty());

        assertThat(lines)
                .as("All values of the exported configuration")
                .isNotNull()
                .isNotEmpty()
                // Verify properties in file are listed
                .anySatisfy(value -> assertThat(value).matches("^property, value\\s*$"))
                .anySatisfy(value -> assertThat(value).matches("^prefix.property, anotherValue\\s*$"))
                // Verify properties not in file are listed (spot check only)
                .anySatisfy(value -> assertThat(value)
                        .matches("^prefix.unmappedProperty, notPresentValue\\s*\\[NOT USED IN RECORD]$"))
                .anySatisfy(value -> assertThat(value)
                        .matches("^unmappedProperty, anotherNotPresentValue\\s*\\[NOT USED IN RECORD]$"));
    }
}
