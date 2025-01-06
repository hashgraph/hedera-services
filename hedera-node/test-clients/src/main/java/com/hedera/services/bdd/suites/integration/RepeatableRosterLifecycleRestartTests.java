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

package com.hedera.services.bdd.suites.integration;

import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.junit.restart.RestartType.GENESIS;
import static com.hedera.services.bdd.junit.restart.StartupAssets.ROSTER_ONLY;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;

import com.hedera.services.bdd.junit.ConfigOverride;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.restart.RestartHapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Order(3)
@Tag(INTEGRATION)
@TargetEmbeddedMode(REPEATABLE)
public class RepeatableRosterLifecycleRestartTests {
    @RestartHapiTest(
            restartType = GENESIS,
            restartOverrides = {@ConfigOverride(key = "addressBook.useRosterLifecycle", value = "true")},
            restartAssets = ROSTER_ONLY)
    Stream<DynamicTest> genesisMigrationReflectsInitialRoster() {
        return hapiTest();
    }
}
