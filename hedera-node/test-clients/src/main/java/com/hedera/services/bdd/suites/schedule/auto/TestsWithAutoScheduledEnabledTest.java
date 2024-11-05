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

package com.hedera.services.bdd.suites.schedule.auto;

import static com.hedera.services.bdd.suites.utils.DynamicTestUtils.extractAllTestAnnotatedMethods;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;

// Running all test methods that are specified in ALL_TESTS constant with auto scheduled enabled
@HapiTestLifecycle
public class TestsWithAutoScheduledEnabledTest {

    // All tests classes from which to get the test cases
    private static final Supplier<?>[] ALL_TEST_CLASSES = new Supplier<?>[] {};

    // All test cases we want to ignore when running the suite
    private static final List<String> IGNORED_TESTS = List.of();

    // All hapi operations we wish to transform into scheduled
    private static final Map<String, String> opsToConvert =
            Map.of("spec.autoScheduledTxns", "CryptoCreate,CryptoUpdate");

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of(
                "scheduling.whitelist",
                "ContractCall,CryptoCreate,CryptoTransfer,FileDelete,FileUpdate,"
                        + "SystemDelete,ConsensusSubmitMessage,TokenBurn,TokenMint,CryptoApproveAllowance"));
    }

    @HapiTest
    final Stream<DynamicTest> runAllTests() {
        var allDynamicTests =
                extractAllTestAnnotatedMethods(ALL_TEST_CLASSES, IGNORED_TESTS, HapiTest.class, opsToConvert);
        return allDynamicTests.stream().flatMap(s -> s);
    }
}
