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

package com.swirlds.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class PathsConfigTest {
    @Test
    @DisplayName("absolutePath() From Start Test")
    void absolutePathFromStartTest() throws IOException {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
        final File start = new File("start");

        assertEquals(start.getCanonicalFile().toPath(), pathsConfig.getAbsolutePath("start"), "invalid path");

        final File expected = new File(start.getCanonicalFile() + "/foo/bar/baz.txt");
        assertEquals(
                expected.toPath(),
                pathsConfig
                        .getAbsolutePath("start")
                        .resolve("foo")
                        .resolve("bar")
                        .resolve("baz.txt"),
                "file does not match expected");
    }

    @Test
    @DisplayName("absolutePath() From Current Working Directory Test")
    void absolutePathFromCurrentWorkingDirectoryTest() throws IOException {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
        assertEquals(new File(".").getCanonicalFile().toPath(), pathsConfig.getAbsolutePath(), "invalid path");

        final File expected = new File(new File(".").getCanonicalFile() + "/foo/bar/baz.txt");
        assertEquals(
                expected.toPath(),
                pathsConfig.getAbsolutePath().resolve("foo").resolve("bar").resolve("baz.txt"),
                "invalid path");
    }
}
