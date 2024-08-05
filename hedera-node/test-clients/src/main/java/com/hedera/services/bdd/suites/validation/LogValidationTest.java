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

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateAllLogsAfter;

import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag("LOG_VALIDATION")
@Order(Integer.MAX_VALUE - 1)
public class LogValidationTest {
    private static final Duration VALIDATION_DELAY = Duration.ofSeconds(1);

    @LeakyHapiTest
    final Stream<DynamicTest> logsContainNoUnexpectedProblems() {
        return hapiTest(validateAllLogsAfter(VALIDATION_DELAY));
    }
}
