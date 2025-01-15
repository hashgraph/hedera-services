/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.EmbeddedReason;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.spec.utilops.EmbeddedVerbs;

import com.swirlds.platform.system.InitTrigger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

import java.util.stream.Stream;

@Tag(INTEGRATION)
public class StartupConfigTxtPemSuite {

    @GenesisHapiTest(generateNetworkJson = false, useDiskAdminKey = true)
    @EmbeddedHapiTest(value = EmbeddedReason.NEEDS_STATE_ACCESS, initTrigger = InitTrigger.RESTART)
    @DisplayName("Loading an override network without override-network.json uses config.txt and on-disk admin key")
    final Stream<DynamicTest> overrideNetworkLoadsConfigTxtAndDiskAdminKey2() {
        return hapiTest(EmbeddedVerbs.viewNode("0", node -> assertEquals("b2ba8f7754d377fa2931f7450afb92ccefb98ba0284c459af29c1455cdb0ac62", node.adminKeyOrThrow().ed25519OrThrow().toHex())));
    }

}