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

package com.hedera.services.bdd.suites.validation;

import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.OUTPUT_DIR;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateAllLogsAfter;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag("LOG_VALIDATION")
@Order(Integer.MAX_VALUE)
public class LogValidationTest {
    private static final Duration VALIDATION_DELAY = Duration.ofSeconds(1);
    private static final String SWIRLDS_LOG = "swirlds.log";
    private NodeMetadata metadata =
            new NodeMetadata(0, "node0", null, "", 0, 0, 0, 0, WorkingDirUtils.workingDirFor(0, "EMBEDDED"));

    private void validateSwirldsLog() throws IOException {
        final Path workingDir = requireNonNull(metadata.workingDir());
        final Path path = workingDir.resolve(OUTPUT_DIR).resolve(SWIRLDS_LOG);
        final String fileContent = Files.readString(path);
        try (var lines = Files.lines(path)) {
            if (lines.anyMatch(line -> line.contains("Exception"))) {
                throw new AssertionError("Unexpected problem found in logs: " + fileContent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @LeakyHapiTest
    final Stream<DynamicTest> logsContainNoUnexpectedProblems() {
        return Stream.concat(
                hapiTest(validateAllLogsAfter(VALIDATION_DELAY)),
                Stream.of(DynamicTest.dynamicTest("Validate swirlds.log", this::validateSwirldsLog)));
    }
}
