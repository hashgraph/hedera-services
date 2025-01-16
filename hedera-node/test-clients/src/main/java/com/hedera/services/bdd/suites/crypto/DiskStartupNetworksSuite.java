/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.junit.hedera.AdminKeySource;
import com.hedera.services.bdd.spec.utilops.EmbeddedVerbs;
import com.swirlds.platform.system.InitTrigger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(INTEGRATION)
public class DiskStartupNetworksSuite {
    private static final String GENESIS_JSON_NODE_0_PUBLIC_KEY =
            "0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final String PEM_PUBLIC_KEY = "b2ba8f7754d377fa2931f7450afb92ccefb98ba0284c459af29c1455cdb0ac62";

    @GenesisHapiTest(keySources = {AdminKeySource.PEM_FILE})
    @EmbeddedHapiTest(value = EmbeddedReason.NEEDS_STATE_ACCESS, initTrigger = InitTrigger.RESTART)
    @DisplayName("Loading a network with no network JSON resources falls back to config.txt and on-disk admin key")
    final Stream<DynamicTest> networkLoadsConfigTxtAndDiskAdminKey() {
        return hapiTest(EmbeddedVerbs.viewNode("0", node -> {
            // Verify the disk PEM key was loaded
            assertEquals(PEM_PUBLIC_KEY, node.adminKeyOrThrow().ed25519OrThrow().toHex());
        }));
    }

    @GenesisHapiTest(keySources = {AdminKeySource.NODE_ADMIN_KEYS_FILE, AdminKeySource.PEM_FILE})
    @EmbeddedHapiTest(value = EmbeddedReason.NEEDS_STATE_ACCESS, initTrigger = InitTrigger.RESTART)
    @DisplayName("Loading a network without genesis-network.json loads config.txt and the node-admin-keys.json key")
    final Stream<DynamicTest> networkLoadsConfigTxtAndNodeJsonKey() {
        return hapiTest(EmbeddedVerbs.viewNode(
                "0",
                node -> assertEquals(
                        // Even though the PEM file is present on disk, we want the NODE_ADMIN_KEYS_FILE to take
                        // precedence
                        GENESIS_JSON_NODE_0_PUBLIC_KEY,
                        node.adminKeyOrThrow().ed25519OrThrow().toHex())));
    }

    @GenesisHapiTest(keySources = {AdminKeySource.GENESIS_NETWORK_FILE, AdminKeySource.PEM_FILE})
    @EmbeddedHapiTest(value = EmbeddedReason.NEEDS_STATE_ACCESS, initTrigger = InitTrigger.RESTART)
    @DisplayName("Genesis network JSON admin key takes precedence over on-disk PEM key")
    final Stream<DynamicTest> networkPrefersGenesisJsonKeyOverDiskPemKey() {
        return hapiTest(EmbeddedVerbs.viewNode(
                "0",
                node -> assertEquals(
                        GENESIS_JSON_NODE_0_PUBLIC_KEY,
                        node.adminKeyOrThrow().ed25519OrThrow().toHex())));
    }

    @GenesisHapiTest
    @EmbeddedHapiTest(value = EmbeddedReason.NEEDS_STATE_ACCESS, initTrigger = InitTrigger.RESTART)
    @DisplayName("Loading a network defaults to loading genesis network JSON")
    final Stream<DynamicTest> networkLoadsPreferredNetworkResources() {
        return hapiTest(EmbeddedVerbs.viewNode(
                "0",
                node -> assertEquals(
                        GENESIS_JSON_NODE_0_PUBLIC_KEY,
                        node.adminKeyOrThrow().ed25519OrThrow().toHex())));
    }
}
