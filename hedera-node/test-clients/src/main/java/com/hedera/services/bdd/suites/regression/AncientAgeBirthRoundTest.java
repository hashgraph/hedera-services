/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.junit.TestTags.ADHOC;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.burstOfTps;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.suites.regression.system.LifecycleTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(ADHOC)
public class AncientAgeBirthRoundTest implements LifecycleTest {

    @HapiTest
    @Disabled("Disabled due to expected failures while birth round ancient age is completed")
    final Stream<DynamicTest> upgradeToUseBirthRounds() {
        /*
        Note: This test should be run in subprocess mode. This is done by executing it with the 'testSubprocess' task:
        :test-clients:testSubprocess --tests "com.hedera.services.bdd.suites.regression.AncientAgeBirthRoundTest"
         */

        return hapiTest(
                burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION),
                upgradeToNextConfigVersion(Map.of("event.useBirthRoundAncientThreshold", "true")),
                burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION));
    }
}
