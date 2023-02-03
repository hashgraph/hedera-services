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
import com.hedera.services.bdd.junit.BalanceReconciliationValidator;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * We first run sequentially a minimal set of {@link com.hedera.services.bdd.spec.HapiSpec}'s that
 * have "leaky" side effects like disabling a feature flag or setting restrictive throttles.
 *
 * <p>These specs end by:
 *
 * <ol>
 *   <li>Enabling all feature flags; and,
 *   <li>Disabling contract throttles.
 * </ol>
 *
 * <p>Afterwards we run concurrently a much larger set of non-interfering specs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("java:S2699")
class AllIntegrationTests extends IntegrationTestBase {
    @Tag("integration")
    @Order(1)
    @TestFactory
    Collection<DynamicContainer> sequentialSpecsBySuite() {
        return Arrays.stream(SequentialSuites.all()).map(this::extractSpecsFromSuite).toList();
    }

    @Tag("integration")
    @Order(2)
    @TestFactory
    List<DynamicTest> concurrentSpecs() {
        return List.of(
                concurrentSpecsFrom(ConcurrentSuites.all()),
                concurrentEthSpecsFrom(ConcurrentSuites.ethereumSuites()));
    }

    @Tag("integration")
    @Order(3)
    @TestFactory
    List<DynamicTest> logValidation() {
        return List.of(
                hgcaaLogValidation("build/network/itest/output/node_0/hgcaa.log"),
                queriesLogValidation("build/network/itest/output/node_0/queries.log"));
    }

    @Tag("integration")
    @Order(4)
    @TestFactory
    List<DynamicTest> recordStreamValidation() {
        return List.of(
                recordStreamValidation(
                        "build/network/itest/records/node_0",
                        new BalanceReconciliationValidator()));
    }
}
