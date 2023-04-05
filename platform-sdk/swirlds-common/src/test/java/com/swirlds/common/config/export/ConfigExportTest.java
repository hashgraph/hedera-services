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

package com.swirlds.common.config.export;

import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.sources.PropertyFileConfigSource;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigExportTest {

    @Test
    void testPrint() throws IOException {
        // given
        final Path configFile =
                Paths.get(ConfigExportTest.class.getResource("test.properties").getPath());
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(BasicConfig.class)
                .withConfigDataTypes(StateConfig.class)
                .withConfigDataTypes(MetricsConfig.class)
                .withSource(new PropertyFileConfigSource(configFile))
                .build();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // when
        ConfigExport.printConfig(configuration, outputStream);
        final List<String> lines =
                outputStream.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList());

        // then
        Assertions.assertNotNull(lines);

        Assertions.assertTrue(lines.size() > 10);

        // Verify properties in file are listed
        assertContains(regexForLine("verifyEventSigs", "false", true), lines);
        assertContains(regexForLine("doUpnp", "false", true), lines);
        assertContains(regexForLine("checkSignedStateFromDisk", "true", true), lines);
        assertContains(regexForLine("showInternalStats", "true", true), lines);
        assertContains(regexForLine("csvFileName", "PlatformTesting", true), lines);
        assertContains(regexForLine("useLoopbackIp", "false", true), lines);
        assertContains(regexForLine("maxOutgoingSyncs", "1", true), lines);
        assertContains(regexForLine("state.saveStatePeriod", "0", true), lines);
        assertContains(regexForLine("loadKeysFromPfxFiles", "false", true), lines);
        assertContains(regexForLine("madeUpSetting", "0", false), lines);
        assertContains(regexForLine("state.madeUpSetting", "1", false), lines);

        // Verify properties not in file are listed (spot check only)
        assertContains(regexForLine("state.signedStateDisk", "3", true), lines);
        assertContains(regexForLine("numConnections", "40", true), lines);
        assertContains(regexForLine("eventIntakeQueueSize", "10000", true), lines);
        assertContains(regexForLine("verboseStatistics", "false", true), lines);
    }

    private String regexForLine(final String paramName, final String value, final boolean inRecord) {
        return "^" + paramName + "\\s*-> " + value + "\\s*\\[" + (inRecord ? "" : "NOT ") + "USED IN RECORD\\]$";
    }

    private static void assertContains(final String regex, final List<String> list) {
        if (list.stream().noneMatch(value -> value.matches(regex))) {
            fail("List does not contain value " + regex);
        }
    }
}
