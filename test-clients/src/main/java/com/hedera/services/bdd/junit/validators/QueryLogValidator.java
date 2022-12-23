/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.junit.validators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class QueryLogValidator {
    private final String logFileLocation;

    public QueryLogValidator(final String logFileLocation) {
        this.logFileLocation = logFileLocation;
    }

    public static void main(String... args) throws IOException {
        final var subject = new QueryLogValidator("build/network/itest/output/node_0/queries.log");

        subject.validate();
    }

    public void validate() throws IOException {
        final List<String> problemLines = Files.readAllLines(Paths.get(logFileLocation));
        if (!problemLines.isEmpty()) {
            Assertions.fail(
                    "Found problems in log file '"
                            + logFileLocation
                            + "':\n"
                            + String.join("\n", problemLines));
        }
    }
}
