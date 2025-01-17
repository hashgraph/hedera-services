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

import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.junit.EmbeddedReason;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.spec.utilops.embedded.VerifyGossipCertOp;
import com.swirlds.platform.system.InitTrigger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(INTEGRATION)
public class DiskStartupNetworksSuite {
    @GenesisHapiTest(useDiskGossipFiles = true)
    @EmbeddedHapiTest(value = EmbeddedReason.NEEDS_STATE_ACCESS, initTrigger = InitTrigger.RESTART)
    @DisplayName("Loading a network with no network JSON resources falls back to config.txt and on-disk key/certs")
    final Stream<DynamicTest> networkLoadsConfigTxtAndDiskGossipKey() {
        return hapiTest(
                new VerifyGossipCertOp(0),
                new VerifyGossipCertOp(1),
                new VerifyGossipCertOp(2),
                new VerifyGossipCertOp(3));
    }
}
