/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus.framework.validation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;

@SuppressWarnings("unused") // issue tracked #6998
public final class NoEventsLost {
    private static final ConsensusConfig CONFIG =
            new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);

    private NoEventsLost() {}

    /**
     * Validates that all ancient events are either stale or consensus, but not both. Non-ancient events could be
     * neither, so they are not checked.
     */
    public static void validateNoEventsAreLost(
            @NonNull final ConsensusOutput output, @NonNull final ConsensusOutput ignored) {
        final Map<Hash, PlatformEvent> stale =
                output.getStaleEvents().stream().collect(Collectors.toMap(Hashable::getHash, e -> e));
        final Map<Hash, PlatformEvent> cons = output.getConsensusRounds().stream()
                .flatMap(r -> r.getConsensusEvents().stream())
                .collect(Collectors.toMap(PlatformEvent::getHash, e -> e));
        if (output.getConsensusRounds().isEmpty()) {
            // no consensus reached, nothing to check
            return;
        }
        final long nonAncientGen = output.getConsensusRounds()
                .getLast()
                .getSnapshot()
                .getMinimumGenerationNonAncient(CONFIG.roundsNonAncient());

        for (final PlatformEvent event : output.getAddedEvents()) {
            if (event.getGeneration() >= nonAncientGen) {
                // non-ancient events are not checked
                continue;
            }
            if (stale.containsKey(event.getHash()) == cons.containsKey(event.getHash())) {
                Assertions.fail(String.format(
                        "An ancient event should be either stale or consensus, but not both!\n"
                                + "nonAncientGen=%d, Event %s, stale=%s, consensus=%s",
                        nonAncientGen,
                        event.getDescriptor(),
                        stale.containsKey(event.getHash()),
                        cons.containsKey(event.getHash())));
            }
        }
    }
}
