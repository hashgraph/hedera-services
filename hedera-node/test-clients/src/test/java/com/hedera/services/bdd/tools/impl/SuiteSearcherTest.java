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

package com.hedera.services.bdd.tools.impl;

import com.hedera.services.bdd.suites.tools.annotation.BddMethodIsNotATest;
import com.hedera.services.bdd.suites.tools.annotation.BddPrerequisiteSpec;
import com.hedera.services.bdd.suites.tools.annotation.BddSpecTransformer;
import com.hedera.services.bdd.suites.tools.annotation.BddTestNameDoesNotMatchMethodName;
import com.hedera.services.bdd.tools.impl.SuiteSearcher.AnnotatedMethod;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class SuiteSearcherTest {

    @InjectSoftAssertions
    private SoftAssertions softly;

    static SuiteSearcher searcher = null;

    @BeforeAll
    static void doClassgraphScan() {
        searcher = new SuiteSearcher();
        searcher.doScan();
    }

    @AfterAll
    static void closeClassgraphScan() {
        searcher.close();
        searcher = null;
    }

    @Test
    void getAllHapiSuiteConcreteSubclassesTest() {
        final var actual = searcher.getAllHapiSuiteConcreteSubclasses();
        softly.assertThat(actual).hasSizeGreaterThanOrEqualTo(250);
        softly.assertThat(toClassSimpleNames(actual))
                .contains(
                        "TopicUpdateSuite",
                        "TokenUpdateSpecs",
                        "ContractUpdateSuite",
                        "TokenUpdatePrecompileSuite",
                        "CryptoUpdateSuite",
                        "FileUpdateSuite");
    }

    @Test
    void getAllHapiSuiteAbstractSubclassesTest() {
        final var actual = searcher.getAllHapiSuiteAbstractSubclasses();
        softly.assertThat(actual).hasSize(3);
        softly.assertThat(toClassSimpleNames(actual))
                .contains("HapiSuite", "LoadTest", "AbstractCryptoTransferLoadTest");
    }

    @Test
    void getAllBddAnnotatedMethodsTest() {
        final var actual1 = searcher.getAllBddAnnotatedMethods(BddPrerequisiteSpec.class);
        softly.assertThat(actual1).hasSizeGreaterThanOrEqualTo(5);
        softly.assertThat(toMethodSimpleNames(actual1))
                .contains(
                        "disableAllFeatureFlagsAndConfirmNotSupported",
                        "enableAllFeatureFlagsAndDisableThrottlesForFurtherCiTesting",
                        "ensureSystemStateAsExpectedWithSystemDefaultFiles",
                        "runCryptoTransfers",
                        "runTransfersBeforeReconnect");

        final var actual2 = searcher.getAllBddAnnotatedMethods(BddMethodIsNotATest.class);
        softly.assertThat(actual2).hasSizeGreaterThanOrEqualTo(1);
        softly.assertThat(toMethodSimpleNames(actual2)).contains("testIndirectApprovalWith");

        final var actual3 = searcher.getAllBddAnnotatedMethods(BddTestNameDoesNotMatchMethodName.class);
        softly.assertThat(actual3).hasSizeGreaterThanOrEqualTo(2);
        softly.assertThat(toMethodSimpleNames(actual3))
                .contains("matrixedPayerRelayerTest", "discoversExpectedVersions");

        final var actual4 = searcher.getAllBddAnnotatedMethods(BddSpecTransformer.class);
        softly.assertThat(actual4).hasSizeGreaterThanOrEqualTo(1);
        softly.assertThat(toMethodSimpleNames(actual4)).contains("withAndWithoutLongTermEnabled");
    }

    @NonNull
    Collection<String> toClassSimpleNames(@NonNull final Collection<Class<?>> klasses) {
        return klasses.stream().map(Class::getSimpleName).toList();
    }

    @NonNull
    Collection<String> toMethodSimpleNames(@NonNull final List<AnnotatedMethod> methods) {
        return methods.stream().map(AnnotatedMethod::methodSimpleName).toList();
    }
}
