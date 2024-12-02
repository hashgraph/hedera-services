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

import static com.hedera.hapi.node.state.tss.RosterToKey.NONE;
import static com.hedera.hapi.node.state.tss.TssKeyingStatus.KEYING_COMPLETE;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_STATUS_KEY;
import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary.FAKE_LEDGER_ID;
import static com.hedera.services.bdd.junit.restart.RestartType.GENESIS;
import static com.hedera.services.bdd.junit.restart.StartupAssets.ROSTER_AND_FULL_TSS_KEY_MATERIAL;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.viewSingleton;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
public class RepeatableRestartTests {
    @RestartHapiTest(
            restartType = GENESIS,
            bootstrapOverrides = {
                @ConfigOverride(key = "tss.keyCandidateRoster", value = "true"),
                @ConfigOverride(key = "addressBook.useRosterLifecycle", value = "true")
            },
            startupAssets = ROSTER_AND_FULL_TSS_KEY_MATERIAL)
    Stream<DynamicTest> genesisTransactionDetectsAvailableLedgerIdAndUpdatesStatus() {
        // If all TSS key material is available at genesis, this is the expected status
        final var expectedStatus = new TssStatus(KEYING_COMPLETE, NONE, Bytes.wrap(FAKE_LEDGER_ID.toBytes()));
        return hapiTest(viewSingleton(
                TssBaseService.NAME,
                TSS_STATUS_KEY,
                (TssStatus actualStatus) -> assertEquals(expectedStatus, actualStatus)));
    }
}
